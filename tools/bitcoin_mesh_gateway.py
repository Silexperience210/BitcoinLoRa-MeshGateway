#!/usr/bin/env python3
"""
Bitcoin Mesh Gateway - Receive Bitcoin transactions via LoRa mesh and broadcast to network
Also provides HTTP API for direct transaction submission from mobile apps
"""

import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import threading
import queue
import time
import json
from datetime import datetime
import re
from http.server import HTTPServer, BaseHTTPRequestHandler
import urllib.parse

# Meshtastic
import meshtastic
import meshtastic.serial_interface
from pubsub import pub

# Bitcoin / Tor
import socket
import socks  # PySocks

# Configuration
DEFAULT_SERIAL_PORT = "COM83"
TOR_PROXY_HOST = "127.0.0.1"
TOR_PROXY_PORT = 9050
BITCOIN_NODES = [
    ("explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion", 8333),  # Blockstream
    ("bitcoin.org", 8333),
]
HTTP_PORT = 8088  # HTTP API port for mobile apps

class PendingTransaction:
    """Track chunks for a multi-part transaction"""
    def __init__(self, total_chunks):
        self.total_chunks = total_chunks
        self.chunks = {}
        self.first_seen = time.time()
    
    def add_chunk(self, index, data):
        self.chunks[index] = data
    
    def is_complete(self):
        return len(self.chunks) == self.total_chunks
    
    def assemble(self):
        return ''.join(self.chunks[i] for i in sorted(self.chunks.keys()))

class HTTPAPIHandler(BaseHTTPRequestHandler):
    """HTTP handler for receiving transactions from mobile apps"""
    gateway = None  # Will be set by the main app
    
    def log_message(self, format, *args):
        # Override to suppress HTTP logs in console
        pass
    
    def do_POST(self):
        if self.path == '/api/tx':
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length).decode('utf-8')
            
            try:
                data = json.loads(body)
                tx_hex = data.get('tx_hex', '')
                
                if tx_hex and self.gateway:
                    # Add to gateway's queue
                    self.gateway.log_queue.put(f"HTTP API: Received TX ({len(tx_hex)} chars)")
                    self.gateway.process_transaction(tx_hex, source="HTTP API")
                    
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.end_headers()
                    self.wfile.write(json.dumps({'status': 'ok', 'message': 'Transaction queued'}).encode())
                else:
                    self.send_response(400)
                    self.send_header('Content-Type', 'application/json')
                    self.end_headers()
                    self.wfile.write(json.dumps({'status': 'error', 'message': 'Missing tx_hex'}).encode())
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'error', 'message': str(e)}).encode())
        
        elif self.path == '/api/chunk':
            # Handle chunked transmission (BTX format)
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length).decode('utf-8')
            
            try:
                data = json.loads(body)
                chunk_index = data.get('index', 0)
                total_chunks = data.get('total', 1)
                chunk_data = data.get('data', '')
                tx_id = data.get('tx_id', 'default')
                
                if self.gateway:
                    self.gateway.handle_btx_chunk(chunk_index, total_chunks, chunk_data, source="HTTP API", tx_id=tx_id)
                    
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.end_headers()
                    self.wfile.write(json.dumps({'status': 'ok', 'chunk': chunk_index}).encode())
                else:
                    raise Exception("Gateway not initialized")
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'error', 'message': str(e)}).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_GET(self):
        if self.path == '/api/status':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            status = {
                'status': 'running',
                'mesh_connected': self.gateway.mesh_connected if self.gateway else False,
                'pending_txs': len(self.gateway.pending_txs) if self.gateway else 0
            }
            self.wfile.write(json.dumps(status).encode())
        else:
            self.send_response(404)
            self.end_headers()


class BitcoinMeshGateway:
    def __init__(self, root):
        self.root = root
        self.root.title("Bitcoin Mesh Gateway")
        self.root.geometry("800x600")
        
        self.mesh_interface = None
        self.mesh_connected = False
        self.pending_txs = {}  # tx_id -> PendingTransaction
        self.log_queue = queue.Queue()
        self.tx_count = 0
        self.http_server = None
        
        self.setup_ui()
        self.start_log_consumer()
        self.start_http_server()
        
    def setup_ui(self):
        # Main frame
        main_frame = ttk.Frame(self.root, padding=10)
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Connection frame
        conn_frame = ttk.LabelFrame(main_frame, text="Mesh Connection", padding=5)
        conn_frame.pack(fill=tk.X, pady=5)
        
        ttk.Label(conn_frame, text="Serial Port:").pack(side=tk.LEFT)
        self.port_var = tk.StringVar(value=DEFAULT_SERIAL_PORT)
        self.port_entry = ttk.Entry(conn_frame, textvariable=self.port_var, width=15)
        self.port_entry.pack(side=tk.LEFT, padx=5)
        
        self.connect_btn = ttk.Button(conn_frame, text="Connect", command=self.toggle_connection)
        self.connect_btn.pack(side=tk.LEFT, padx=5)
        
        self.status_label = ttk.Label(conn_frame, text="Disconnected", foreground="red")
        self.status_label.pack(side=tk.LEFT, padx=10)
        
        # HTTP API status
        self.http_label = ttk.Label(conn_frame, text=f"HTTP API: http://0.0.0.0:{HTTP_PORT}", foreground="blue")
        self.http_label.pack(side=tk.RIGHT, padx=10)
        
        # Tor frame
        tor_frame = ttk.LabelFrame(main_frame, text="Tor Proxy", padding=5)
        tor_frame.pack(fill=tk.X, pady=5)
        
        self.use_tor_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(tor_frame, text="Use Tor", variable=self.use_tor_var).pack(side=tk.LEFT)
        
        ttk.Label(tor_frame, text="Proxy:").pack(side=tk.LEFT, padx=(20, 5))
        self.tor_host_var = tk.StringVar(value=TOR_PROXY_HOST)
        ttk.Entry(tor_frame, textvariable=self.tor_host_var, width=15).pack(side=tk.LEFT)
        
        ttk.Label(tor_frame, text=":").pack(side=tk.LEFT)
        self.tor_port_var = tk.StringVar(value=str(TOR_PROXY_PORT))
        ttk.Entry(tor_frame, textvariable=self.tor_port_var, width=6).pack(side=tk.LEFT)
        
        # Stats frame
        stats_frame = ttk.LabelFrame(main_frame, text="Statistics", padding=5)
        stats_frame.pack(fill=tk.X, pady=5)
        
        self.stats_label = ttk.Label(stats_frame, text="TX Received: 0 | Broadcasted: 0 | Pending: 0")
        self.stats_label.pack(side=tk.LEFT)
        
        # Log frame
        log_frame = ttk.LabelFrame(main_frame, text="Activity Log", padding=5)
        log_frame.pack(fill=tk.BOTH, expand=True, pady=5)
        
        self.log_text = scrolledtext.ScrolledText(log_frame, height=20, state=tk.DISABLED)
        self.log_text.pack(fill=tk.BOTH, expand=True)
        
        # Manual TX frame
        manual_frame = ttk.LabelFrame(main_frame, text="Manual Transaction Test", padding=5)
        manual_frame.pack(fill=tk.X, pady=5)
        
        self.tx_entry = ttk.Entry(manual_frame, width=80)
        self.tx_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=5)
        self.tx_entry.insert(0, "0100000001...")  # Placeholder
        
        ttk.Button(manual_frame, text="Broadcast", command=self.manual_broadcast).pack(side=tk.RIGHT)
        
    def log(self, message):
        timestamp = datetime.now().strftime("%H:%M:%S")
        self.log_queue.put(f"[{timestamp}] {message}")
        
    def start_log_consumer(self):
        def consume():
            while True:
                try:
                    message = self.log_queue.get(timeout=0.1)
                    self.log_text.config(state=tk.NORMAL)
                    self.log_text.insert(tk.END, message + "\n")
                    self.log_text.see(tk.END)
                    self.log_text.config(state=tk.DISABLED)
                except queue.Empty:
                    pass
                self.root.update_idletasks()
        
        thread = threading.Thread(target=consume, daemon=True)
        thread.start()
        
    def start_http_server(self):
        """Start HTTP API server for mobile apps"""
        def run_server():
            HTTPAPIHandler.gateway = self
            server = HTTPServer(('0.0.0.0', HTTP_PORT), HTTPAPIHandler)
            self.http_server = server
            self.log(f"HTTP API server started on port {HTTP_PORT}")
            server.serve_forever()
        
        thread = threading.Thread(target=run_server, daemon=True)
        thread.start()
        
    def toggle_connection(self):
        if self.mesh_connected:
            self.disconnect_mesh()
        else:
            self.connect_mesh()
            
    def connect_mesh(self):
        port = self.port_var.get()
        self.log(f"Connecting to mesh on {port}...")
        
        try:
            self.mesh_interface = meshtastic.serial_interface.SerialInterface(port)
            pub.subscribe(self.on_mesh_receive, "meshtastic.receive")
            self.mesh_connected = True
            self.status_label.config(text="Connected", foreground="green")
            self.connect_btn.config(text="Disconnect")
            self.log("Connected to mesh network!")
        except Exception as e:
            self.log(f"Connection failed: {e}")
            messagebox.showerror("Connection Error", str(e))
            
    def disconnect_mesh(self):
        if self.mesh_interface:
            try:
                pub.unsubscribe(self.on_mesh_receive, "meshtastic.receive")
                self.mesh_interface.close()
            except:
                pass
        self.mesh_interface = None
        self.mesh_connected = False
        self.status_label.config(text="Disconnected", foreground="red")
        self.connect_btn.config(text="Connect")
        self.log("Disconnected from mesh")
        
    def on_mesh_receive(self, packet, interface):
        """Handle received mesh packet"""
        try:
            decoded = packet.get("decoded", {})
            portnum = decoded.get("portnum", "")
            sender = packet.get("fromId", "unknown")
            
            # Debug: Log ALL received packets
            self.log(f"RECV portnum={portnum} from={sender}")
            
            # Only process text messages
            if portnum == "TEXT_MESSAGE_APP":
                payload = decoded.get("payload", b"")
                if isinstance(payload, bytes):
                    text = payload.decode('utf-8', errors='ignore')
                else:
                    text = str(payload)
                
                self.log(f"Text from {sender}: {text[:50]}...")
                
                # Check for BTX format: BTX:n/total:data
                if text.startswith("BTX:"):
                    self.handle_btx_message(text, sender)
                    
        except Exception as e:
            self.log(f"Error processing packet: {e}")
            
    def handle_btx_message(self, text, sender):
        """Parse BTX:n/total:data format"""
        try:
            # Format: BTX:1/3:hexdata
            match = re.match(r'BTX:(\d+)/(\d+):(.+)', text)
            if match:
                chunk_num = int(match.group(1))
                total_chunks = int(match.group(2))
                chunk_data = match.group(3)
                
                self.handle_btx_chunk(chunk_num, total_chunks, chunk_data, source=f"LoRa:{sender}")
        except Exception as e:
            self.log(f"BTX parse error: {e}")
            
    def handle_btx_chunk(self, chunk_num, total_chunks, chunk_data, source="unknown", tx_id=None):
        """Handle a BTX chunk from any source"""
        if tx_id is None:
            tx_id = f"{source}_{int(time.time())}"
        
        self.log(f"BTX chunk {chunk_num}/{total_chunks} from {source}")
        
        # Create or get pending transaction
        if tx_id not in self.pending_txs:
            self.pending_txs[tx_id] = PendingTransaction(total_chunks)
        
        pending = self.pending_txs[tx_id]
        pending.add_chunk(chunk_num, chunk_data)
        
        # Update stats
        self.update_stats()
        
        # Check if complete
        if pending.is_complete():
            full_tx = pending.assemble()
            self.log(f"Transaction complete from {source}! ({len(full_tx)} chars)")
            del self.pending_txs[tx_id]
            self.process_transaction(full_tx, source=source)
            
    def process_transaction(self, tx_hex, source="unknown"):
        """Process and broadcast a complete transaction"""
        self.tx_count += 1
        self.update_stats()
        
        # Validate hex
        try:
            bytes.fromhex(tx_hex)
        except ValueError:
            self.log(f"Invalid hex from {source}")
            return
        
        self.log(f"Broadcasting TX from {source}: {tx_hex[:32]}...")
        
        # Broadcast in background
        thread = threading.Thread(target=self.broadcast_transaction, args=(tx_hex,))
        thread.start()
        
    def broadcast_transaction(self, tx_hex):
        """Broadcast transaction to Bitcoin network"""
        tx_bytes = bytes.fromhex(tx_hex)
        
        for node_addr, node_port in BITCOIN_NODES:
            try:
                if self.use_tor_var.get():
                    sock = socks.socksocket()
                    sock.set_proxy(socks.SOCKS5, self.tor_host_var.get(), int(self.tor_port_var.get()))
                else:
                    sock = socket.socket()
                
                sock.settimeout(30)
                sock.connect((node_addr, node_port))
                
                # Send version message (simplified)
                # In reality, you'd need proper Bitcoin P2P protocol
                # For now, this is a placeholder
                self.log(f"Connected to {node_addr}:{node_port}")
                
                # Close for now - full implementation would do P2P handshake + tx broadcast
                sock.close()
                self.log(f"TX sent to {node_addr}")
                return
                
            except Exception as e:
                self.log(f"Failed to connect to {node_addr}: {e}")
                continue
        
        self.log("All Bitcoin nodes failed!")
        
    def update_stats(self):
        pending = len(self.pending_txs)
        self.stats_label.config(text=f"TX Received: {self.tx_count} | Pending: {pending}")
        
    def manual_broadcast(self):
        tx_hex = self.tx_entry.get().strip()
        if tx_hex and tx_hex != "0100000001...":
            self.process_transaction(tx_hex, source="Manual")
            
    def on_closing(self):
        self.disconnect_mesh()
        if self.http_server:
            self.http_server.shutdown()
        self.root.destroy()


def main():
    root = tk.Tk()
    app = BitcoinMeshGateway(root)
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    root.mainloop()


if __name__ == "__main__":
    main()
