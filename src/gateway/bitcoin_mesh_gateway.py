#!/usr/bin/env python3
"""
Bitcoin Mesh Gateway - Reçoit des transactions via LoRa et les broadcast sur Bitcoin (Web/Tor)

Architecture:
    [Zone sans Internet]          [Gateway avec Internet]           [Bitcoin Network]
    
    📱 Client ──LoRa──▶ 📡 T-Beam ──LoRa──▶ 📡 Gateway T-Beam ──USB──▶ 💻 Ce script ──Tor/Web──▶ ₿ Bitcoin Node
"""

import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import threading
import struct
import time
import json
import hashlib

try:
    import meshtastic
    import meshtastic.serial_interface
    from pubsub import pub
    import requests
    import serial.tools.list_ports
except ImportError:
    import subprocess
    subprocess.check_call(["pip", "install", "meshtastic", "pypubsub", "requests", "pyserial", "pysocks"])
    import meshtastic
    import meshtastic.serial_interface
    from pubsub import pub
    import requests
    import serial.tools.list_ports

# Essayer d'importer le support SOCKS pour Tor
try:
    import socks
    import socket
    TOR_AVAILABLE = True
except ImportError:
    TOR_AVAILABLE = False

# Constantes protocole BitcoinTxModule
BTX_MSG_TX_START = 0x01
BTX_MSG_TX_CHUNK = 0x02
BTX_MSG_TX_END   = 0x03
BTX_MSG_TX_ACK   = 0x04
BTX_MSG_TX_ERROR = 0x05
BTX_CHUNK_SIZE   = 180
BTX_MAX_TX_SIZE  = 2048
PRIVATE_APP_PORT = 256

# Erreurs
BTX_ERR_TOO_LARGE = 1
BTX_ERR_TIMEOUT   = 2
BTX_ERR_INVALID   = 3
BTX_ERR_BROADCAST_FAIL = 4

# APIs Bitcoin (clearnet et onion)
BITCOIN_APIS = {
    "Mempool.space": {
        "clearnet": "https://mempool.space/api/tx",
        "onion": "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/api/tx",
        "testnet": "https://mempool.space/testnet/api/tx"
    },
    "Blockstream": {
        "clearnet": "https://blockstream.info/api/tx",
        "onion": "http://explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion/api/tx",
        "testnet": "https://blockstream.info/testnet/api/tx"
    },
    "Bitcoin Core (local)": {
        "clearnet": "http://127.0.0.1:8332",
        "rpc": True
    }
}


class PendingTransaction:
    """Transaction en cours de réception"""
    def __init__(self, tx_id, total_size, sender):
        self.tx_id = tx_id
        self.total_size = total_size
        self.sender = sender
        self.chunks = {}
        self.start_time = time.time()
        self.expected_chunks = (total_size + BTX_CHUNK_SIZE - 1) // BTX_CHUNK_SIZE
        
    def add_chunk(self, index, data):
        self.chunks[index] = data
        
    def is_complete(self):
        return len(self.chunks) == self.expected_chunks
        
    def get_data(self):
        result = b""
        for i in range(self.expected_chunks):
            if i in self.chunks:
                result += self.chunks[i]
        return result[:self.total_size]
        
    def is_expired(self, timeout=30):
        return time.time() - self.start_time > timeout


class BitcoinMeshGateway:
    def __init__(self, root):
        self.root = root
        self.root.title("₿ Bitcoin Mesh Gateway")
        self.root.geometry("800x700")
        self.root.configure(bg="#1a1a2e")
        
        self.interface = None
        self.connected = False
        self.pending_txs = {}  # tx_id -> PendingTransaction
        self.text_buffers = {}  # sender -> {"parts": [], "last_time": timestamp}
        self.tx_history = []
        self.tx_count = 0
        self.tor_enabled = False
        self.session = requests.Session()
        
        self.setup_styles()
        self.create_widgets()
        self.refresh_ports()
        
        # Timer pour nettoyer les TX expirées
        self.cleanup_timer()
        
    def setup_styles(self):
        style = ttk.Style()
        style.theme_use('clam')
        
        style.configure("Title.TLabel", 
                       font=("Segoe UI", 18, "bold"),
                       foreground="#f7931a",
                       background="#1a1a2e")
        style.configure("TLabel", 
                       font=("Segoe UI", 10),
                       foreground="#ffffff",
                       background="#1a1a2e")
        style.configure("Status.TLabel",
                       font=("Segoe UI", 9),
                       foreground="#888888",
                       background="#1a1a2e")
        style.configure("TFrame", background="#1a1a2e")
        style.configure("TLabelframe", background="#1a1a2e")
        style.configure("TLabelframe.Label", 
                       font=("Segoe UI", 10, "bold"),
                       foreground="#f7931a",
                       background="#1a1a2e")
        style.configure("TCheckbutton",
                       font=("Segoe UI", 10),
                       foreground="#ffffff",
                       background="#1a1a2e")
        
    def create_widgets(self):
        main_frame = ttk.Frame(self.root, padding=15)
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Header
        header = ttk.Frame(main_frame)
        header.pack(fill=tk.X, pady=(0, 15))
        
        ttk.Label(header, text="₿ Bitcoin Mesh Gateway", style="Title.TLabel").pack(side=tk.LEFT)
        
        status_frame = ttk.Frame(header)
        status_frame.pack(side=tk.RIGHT)
        
        self.mesh_status = ttk.Label(status_frame, text="📡 Mesh: Déconnecté", foreground="#ff6b6b")
        self.mesh_status.pack(anchor=tk.E)
        self.btc_status = ttk.Label(status_frame, text="₿ Bitcoin: Non configuré", foreground="#888888")
        self.btc_status.pack(anchor=tk.E)
        
        # Section Connexion Mesh
        mesh_frame = ttk.LabelFrame(main_frame, text="🔗 Connexion Mesh LoRa", padding=10)
        mesh_frame.pack(fill=tk.X, pady=(0, 10))
        
        conn_row = ttk.Frame(mesh_frame)
        conn_row.pack(fill=tk.X)
        
        ttk.Label(conn_row, text="Port:").pack(side=tk.LEFT, padx=(0, 5))
        
        self.port_var = tk.StringVar()
        self.port_combo = ttk.Combobox(conn_row, textvariable=self.port_var, width=12, state="readonly")
        self.port_combo.pack(side=tk.LEFT, padx=(0, 5))
        
        ttk.Button(conn_row, text="🔄", width=3, command=self.refresh_ports).pack(side=tk.LEFT, padx=(0, 10))
        
        self.connect_btn = ttk.Button(conn_row, text="Connecter", command=self.toggle_mesh_connection)
        self.connect_btn.pack(side=tk.LEFT)
        
        # Section Bitcoin Network
        btc_frame = ttk.LabelFrame(main_frame, text="₿ Connexion Bitcoin Network", padding=10)
        btc_frame.pack(fill=tk.X, pady=(0, 10))
        
        # API Selection
        api_row = ttk.Frame(btc_frame)
        api_row.pack(fill=tk.X, pady=(0, 5))
        
        ttk.Label(api_row, text="API:").pack(side=tk.LEFT, padx=(0, 5))
        
        self.api_var = tk.StringVar(value="Mempool.space")
        self.api_combo = ttk.Combobox(api_row, textvariable=self.api_var, 
                                       values=list(BITCOIN_APIS.keys()), width=20, state="readonly")
        self.api_combo.pack(side=tk.LEFT, padx=(0, 15))
        
        ttk.Label(api_row, text="Réseau:").pack(side=tk.LEFT, padx=(0, 5))
        
        self.network_var = tk.StringVar(value="mainnet")
        ttk.Radiobutton(api_row, text="Mainnet", variable=self.network_var, value="mainnet").pack(side=tk.LEFT, padx=(0, 10))
        ttk.Radiobutton(api_row, text="Testnet", variable=self.network_var, value="testnet").pack(side=tk.LEFT)
        
        # Tor
        tor_row = ttk.Frame(btc_frame)
        tor_row.pack(fill=tk.X, pady=(5, 0))
        
        self.tor_var = tk.BooleanVar(value=False)
        self.tor_check = ttk.Checkbutton(tor_row, text="🧅 Utiliser Tor (anonymat)", 
                                          variable=self.tor_var, command=self.toggle_tor)
        self.tor_check.pack(side=tk.LEFT)
        
        if not TOR_AVAILABLE:
            self.tor_check.configure(state=tk.DISABLED)
            ttk.Label(tor_row, text="(pip install pysocks)", foreground="#666666").pack(side=tk.LEFT, padx=10)
        
        ttk.Label(tor_row, text="Tor SOCKS:").pack(side=tk.LEFT, padx=(20, 5))
        self.tor_host = ttk.Entry(tor_row, width=12)
        self.tor_host.insert(0, "127.0.0.1")
        self.tor_host.pack(side=tk.LEFT, padx=(0, 2))
        ttk.Label(tor_row, text=":").pack(side=tk.LEFT)
        self.tor_port = ttk.Entry(tor_row, width=6)
        self.tor_port.insert(0, "9050")
        self.tor_port.pack(side=tk.LEFT)
        
        # Bitcoin Core RPC (optionnel)
        rpc_row = ttk.Frame(btc_frame)
        rpc_row.pack(fill=tk.X, pady=(5, 0))
        
        ttk.Label(rpc_row, text="RPC (Bitcoin Core):").pack(side=tk.LEFT, padx=(0, 5))
        ttk.Label(rpc_row, text="user:", foreground="#888888").pack(side=tk.LEFT)
        self.rpc_user = ttk.Entry(rpc_row, width=10)
        self.rpc_user.pack(side=tk.LEFT, padx=(2, 5))
        ttk.Label(rpc_row, text="pass:", foreground="#888888").pack(side=tk.LEFT)
        self.rpc_pass = ttk.Entry(rpc_row, width=10, show="*")
        self.rpc_pass.pack(side=tk.LEFT, padx=(2, 10))
        
        self.test_btn = ttk.Button(rpc_row, text="Tester connexion", command=self.test_bitcoin_connection)
        self.test_btn.pack(side=tk.LEFT)
        
        # Stats
        stats_frame = ttk.LabelFrame(main_frame, text="📊 Statistiques", padding=10)
        stats_frame.pack(fill=tk.X, pady=(0, 10))
        
        stats_row = ttk.Frame(stats_frame)
        stats_row.pack(fill=tk.X)
        
        self.stat_received = ttk.Label(stats_row, text="Reçues: 0", foreground="#00aaff")
        self.stat_received.pack(side=tk.LEFT, padx=(0, 30))
        
        self.stat_broadcast = ttk.Label(stats_row, text="Broadcastées: 0", foreground="#00ff88")
        self.stat_broadcast.pack(side=tk.LEFT, padx=(0, 30))
        
        self.stat_failed = ttk.Label(stats_row, text="Échouées: 0", foreground="#ff6b6b")
        self.stat_failed.pack(side=tk.LEFT, padx=(0, 30))
        
        self.stat_pending = ttk.Label(stats_row, text="En attente: 0", foreground="#f7931a")
        self.stat_pending.pack(side=tk.LEFT)
        
        # Transactions reçues
        tx_frame = ttk.LabelFrame(main_frame, text="📜 Transactions reçues du Mesh", padding=10)
        tx_frame.pack(fill=tk.BOTH, expand=True, pady=(0, 10))
        
        # Treeview pour les transactions
        columns = ("time", "txid", "size", "status", "btc_txid")
        self.tx_tree = ttk.Treeview(tx_frame, columns=columns, show="headings", height=6)
        
        self.tx_tree.heading("time", text="Heure")
        self.tx_tree.heading("txid", text="Mesh TX ID")
        self.tx_tree.heading("size", text="Taille")
        self.tx_tree.heading("status", text="Statut")
        self.tx_tree.heading("btc_txid", text="Bitcoin TXID")
        
        self.tx_tree.column("time", width=80)
        self.tx_tree.column("txid", width=80)
        self.tx_tree.column("size", width=70)
        self.tx_tree.column("status", width=100)
        self.tx_tree.column("btc_txid", width=350)
        
        scrollbar = ttk.Scrollbar(tx_frame, orient=tk.VERTICAL, command=self.tx_tree.yview)
        self.tx_tree.configure(yscrollcommand=scrollbar.set)
        
        self.tx_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        
        # Log
        log_frame = ttk.LabelFrame(main_frame, text="📋 Journal", padding=10)
        log_frame.pack(fill=tk.BOTH, expand=True)
        
        self.log_text = scrolledtext.ScrolledText(log_frame, height=10, font=("Consolas", 9),
                                                   bg="#0f0f23", fg="#aaaaaa", state=tk.DISABLED)
        self.log_text.pack(fill=tk.BOTH, expand=True)
        
        self.log_text.tag_configure("info", foreground="#00aaff")
        self.log_text.tag_configure("success", foreground="#00ff88")
        self.log_text.tag_configure("error", foreground="#ff6b6b")
        self.log_text.tag_configure("warning", foreground="#f7931a")
        self.log_text.tag_configure("btc", foreground="#f7931a")
        
    def refresh_ports(self):
        ports = [p.device for p in serial.tools.list_ports.comports()]
        self.port_combo['values'] = ports
        if ports:
            self.port_combo.current(0)
            
    def toggle_mesh_connection(self):
        if self.connected:
            self.disconnect_mesh()
        else:
            self.connect_mesh()
            
    def connect_mesh(self):
        port = self.port_var.get()
        if not port:
            messagebox.showerror("Erreur", "Sélectionnez un port")
            return
            
        try:
            self.log(f"Connexion au mesh via {port}...", "info")
            self.interface = meshtastic.serial_interface.SerialInterface(port)
            pub.subscribe(self.on_mesh_receive, "meshtastic.receive")
            
            self.connected = True
            self.mesh_status.configure(text="📡 Mesh: Connecté ✓", foreground="#00ff88")
            self.connect_btn.configure(text="Déconnecter")
            
            node_info = self.interface.getMyNodeInfo()
            name = node_info.get('user', {}).get('shortName', 'N/A')
            self.log(f"✅ Connecté au nœud gateway: {name}", "success")
            self.log("🎧 En écoute des transactions Bitcoin sur le mesh...", "info")
            
        except Exception as e:
            self.log(f"❌ Erreur connexion mesh: {e}", "error")
            messagebox.showerror("Erreur", str(e))
            
    def disconnect_mesh(self):
        try:
            if self.interface:
                pub.unsubscribe(self.on_mesh_receive, "meshtastic.receive")
                self.interface.close()
                self.interface = None
        except:
            pass
            
        self.connected = False
        self.mesh_status.configure(text="📡 Mesh: Déconnecté", foreground="#ff6b6b")
        self.connect_btn.configure(text="Connecter")
        self.log("Déconnecté du mesh", "info")
        
    def toggle_tor(self):
        if self.tor_var.get():
            self.setup_tor()
        else:
            self.session = requests.Session()
            self.btc_status.configure(text="₿ Bitcoin: Clearnet", foreground="#00aaff")
            self.log("🌐 Mode clearnet activé", "info")
            
    def setup_tor(self):
        if not TOR_AVAILABLE:
            messagebox.showerror("Erreur", "PySocks non installé: pip install pysocks")
            self.tor_var.set(False)
            return
            
        try:
            tor_host = self.tor_host.get()
            tor_port = int(self.tor_port.get())
            
            self.session = requests.Session()
            self.session.proxies = {
                'http': f'socks5h://{tor_host}:{tor_port}',
                'https': f'socks5h://{tor_host}:{tor_port}'
            }
            
            # Test connexion Tor
            self.log("🧅 Test connexion Tor...", "info")
            r = self.session.get("https://check.torproject.org/api/ip", timeout=30)
            data = r.json()
            if data.get("IsTor"):
                self.btc_status.configure(text="₿ Bitcoin: via Tor 🧅", foreground="#00ff88")
                self.log(f"✅ Connecté via Tor (IP: {data.get('IP', 'N/A')})", "success")
            else:
                raise Exception("Pas connecté via Tor")
                
        except Exception as e:
            self.log(f"❌ Erreur Tor: {e}", "error")
            self.tor_var.set(False)
            self.session = requests.Session()
            messagebox.showerror("Erreur Tor", f"Impossible de se connecter via Tor:\n{e}\n\nVérifiez que Tor est lancé.")
            
    def test_bitcoin_connection(self):
        """Teste la connexion à l'API Bitcoin"""
        threading.Thread(target=self._test_bitcoin_thread, daemon=True).start()
        
    def _test_bitcoin_thread(self):
        try:
            api_name = self.api_var.get()
            api_config = BITCOIN_APIS.get(api_name, {})
            
            if api_config.get("rpc"):
                # Test Bitcoin Core RPC
                url = api_config["clearnet"]
                auth = (self.rpc_user.get(), self.rpc_pass.get())
                data = {"jsonrpc": "1.0", "method": "getblockchaininfo", "params": []}
                r = self.session.post(url, json=data, auth=auth, timeout=10)
                result = r.json()
                if "result" in result:
                    chain = result["result"]["chain"]
                    blocks = result["result"]["blocks"]
                    self.log(f"✅ Bitcoin Core connecté: {chain}, bloc {blocks}", "success")
                    self.btc_status.configure(text=f"₿ Bitcoin Core: {chain}", foreground="#00ff88")
                else:
                    raise Exception(result.get("error", "Erreur inconnue"))
            else:
                # Test API publique
                if self.tor_var.get() and "onion" in api_config:
                    test_url = api_config["onion"].replace("/tx", "")
                else:
                    network = self.network_var.get()
                    if network == "testnet" and "testnet" in api_config:
                        test_url = api_config["testnet"].replace("/tx", "")
                    else:
                        test_url = api_config["clearnet"].replace("/tx", "")
                
                # Test avec le dernier bloc
                r = self.session.get(f"{test_url}/blocks/tip/height", timeout=15)
                if r.status_code == 200:
                    height = r.text
                    self.log(f"✅ {api_name} connecté, bloc actuel: {height}", "success")
                    mode = "🧅 Tor" if self.tor_var.get() else "🌐 Clearnet"
                    self.btc_status.configure(text=f"₿ {api_name}: {mode}", foreground="#00ff88")
                else:
                    raise Exception(f"HTTP {r.status_code}")
                    
        except Exception as e:
            self.log(f"❌ Test échoué: {e}", "error")
            self.btc_status.configure(text="₿ Bitcoin: Erreur", foreground="#ff6b6b")
            
    def on_mesh_receive(self, packet, interface):
        """Callback pour les messages reçus du mesh"""
        try:
            decoded = packet.get("decoded", {})
            portnum = decoded.get("portnum")
            sender = packet.get("fromId", "unknown")
            
            # Mode PRIVATE_APP - protocole chunké BitcoinTx
            if portnum == "PRIVATE_APP":
                payload = decoded.get("payload", b"")
                
                if len(payload) > 0:
                    msg_type = payload[0]
                    
                    if msg_type == BTX_MSG_TX_START:
                        self.handle_tx_start(payload, sender)
                    elif msg_type == BTX_MSG_TX_CHUNK:
                        self.handle_tx_chunk(payload, sender)
                    elif msg_type == BTX_MSG_TX_END:
                        self.handle_tx_end(payload, sender)
            
            # Mode TEXT_MESSAGE - accepter aussi les messages texte (pour app smartphone)
            elif portnum == "TEXT_MESSAGE_APP":
                text = decoded.get("text", "")
                if text:
                    self.handle_text_message(text, sender)
                        
        except Exception as e:
            self.log(f"Erreur parsing mesh: {e}", "error")
    
    def handle_text_message(self, text, sender):
        """Traite un message texte - vérifie si c'est une transaction Bitcoin (supporte multi-messages)"""
        text = text.strip()
        
        # Ignorer les messages vides
        if len(text) < 5:
            return
            
        # Nettoyer le texte (enlever espaces, 0x, etc.)
        clean_hex = text.replace(" ", "").replace("0x", "").replace("\n", "").replace("\r", "")
        
        # Vérifier que c'est bien du hex
        if not all(c in '0123456789abcdefABCDEF' for c in clean_hex):
            self.log(f"📨 Message texte de {sender}: {text[:50]}...", "info")
            return
        
        # C'est du hex! Ajouter au buffer de ce sender
        if sender not in self.text_buffers:
            self.text_buffers[sender] = {"parts": [], "last_time": time.time()}
        
        buffer = self.text_buffers[sender]
        buffer["parts"].append(clean_hex)
        buffer["last_time"] = time.time()
        
        # Assembler toutes les parties
        full_hex = "".join(buffer["parts"])
        
        self.log(f"📦 Partie {len(buffer['parts'])} reçue de {sender} ({len(clean_hex)} chars)", "info")
        self.log(f"   Total accumulé: {len(full_hex)} chars ({len(full_hex)//2} octets)", "info")
        
        # Vérifier si c'est une transaction Bitcoin complète
        # Une TX commence par version (01 ou 02) et doit avoir une longueur paire
        if len(full_hex) >= 120 and len(full_hex) % 2 == 0:
            if full_hex.startswith('01') or full_hex.startswith('02'):
                # Essayer de valider la structure
                if self.looks_like_complete_tx(full_hex):
                    self.log(f"✅ TX Bitcoin complète détectée! ({len(buffer['parts'])} parties)", "success")
                    
                    # Vider le buffer
                    del self.text_buffers[sender]
                    
                    # Broadcaster
                    self.broadcast_text_transaction(full_hex, sender)
                else:
                    self.log(f"   ⏳ En attente de plus de données...", "warning")
    
    def looks_like_complete_tx(self, tx_hex):
        """Vérifie basiquement si la TX semble complète"""
        try:
            tx_bytes = bytes.fromhex(tx_hex)
            
            # Une TX doit faire au moins 60 bytes
            if len(tx_bytes) < 60:
                return False
            
            # Version (4 bytes) - doit être 1 ou 2
            version = int.from_bytes(tx_bytes[0:4], 'little')
            if version not in [1, 2]:
                return False
            
            # Vérifier le locktime à la fin (4 derniers bytes)
            # Le locktime est généralement 0 ou une valeur de bloc/timestamp
            locktime = int.from_bytes(tx_bytes[-4:], 'little')
            
            # Locktime 0 ou valeur raisonnable (< 2^31)
            if locktime > 2147483647:
                return False
            
            # Semble valide!
            return True
            
        except Exception:
            return False
    
    def broadcast_text_transaction(self, tx_hex, sender):
        """Broadcast une transaction reçue par message texte"""
        self.tx_count += 1
        
        # Ajouter à l'historique
        tree_id = self.tx_tree.insert("", 0, values=(
            time.strftime("%H:%M:%S"),
            f"TXT ({sender[:6]})",
            f"{len(tx_hex)//2}B",
            "⏳ Broadcast...",
            ""
        ))
        
        # Broadcast en arrière-plan
        def do_broadcast():
            try:
                api_name = self.api_var.get()
                api_config = BITCOIN_APIS.get(api_name, {})
                
                if api_config.get("rpc"):
                    btc_txid = self._broadcast_rpc(tx_hex, api_config)
                else:
                    btc_txid = self._broadcast_api(tx_hex, api_config)
                
                self.log(f"🚀 TX broadcastée! TXID: {btc_txid}", "success")
                self.root.after(0, lambda tid=tree_id, txid=btc_txid: (
                    self.tx_tree.set(tid, "status", "✅ Broadcastée"),
                    self.tx_tree.set(tid, "btc_txid", txid)
                ))
                
            except Exception as e:
                self.log(f"❌ Échec broadcast: {e}", "error")
                self.root.after(0, lambda tid=tree_id, err=str(e): (
                    self.tx_tree.set(tid, "status", "❌ Échec"),
                    self.tx_tree.set(tid, "btc_txid", err[:40])
                ))
            
            self.update_stats()
        
        threading.Thread(target=do_broadcast, daemon=True).start()
            
    def handle_tx_start(self, payload, sender):
        """Reçoit TX_START"""
        if len(payload) >= 4:
            tx_id = payload[1]
            tx_size = struct.unpack("<H", payload[2:4])[0]
            
            self.log(f"📥 TX_START reçu: ID={tx_id}, taille={tx_size} octets, de {sender}", "warning")
            
            if tx_size > BTX_MAX_TX_SIZE:
                self.send_error(tx_id, BTX_ERR_TOO_LARGE, sender)
                return
                
            self.pending_txs[tx_id] = PendingTransaction(tx_id, tx_size, sender)
            self.update_stats()
            
    def handle_tx_chunk(self, payload, sender):
        """Reçoit TX_CHUNK"""
        if len(payload) >= 3:
            tx_id = payload[1]
            chunk_idx = payload[2]
            chunk_data = payload[3:]
            
            if tx_id in self.pending_txs:
                self.pending_txs[tx_id].add_chunk(chunk_idx, chunk_data)
                self.log(f"  📦 Chunk {chunk_idx + 1}: {len(chunk_data)} octets", "info")
                
    def handle_tx_end(self, payload, sender):
        """Reçoit TX_END - transaction complète, la broadcaster"""
        if len(payload) >= 2:
            tx_id = payload[1]
            
            if tx_id not in self.pending_txs:
                self.log(f"❌ TX_END pour TX inconnue #{tx_id}", "error")
                return
                
            pending = self.pending_txs[tx_id]
            
            if not pending.is_complete():
                self.log(f"❌ TX #{tx_id} incomplète ({len(pending.chunks)}/{pending.expected_chunks} chunks)", "error")
                self.send_error(tx_id, BTX_ERR_INVALID, sender)
                del self.pending_txs[tx_id]
                return
                
            # Récupérer la transaction
            tx_bytes = pending.get_data()
            tx_hex = tx_bytes.hex()
            
            self.log(f"✅ TX #{tx_id} complète: {len(tx_bytes)} octets", "success")
            
            # Ajouter à l'historique
            self.tx_count += 1
            tree_id = self.tx_tree.insert("", 0, values=(
                time.strftime("%H:%M:%S"),
                f"#{tx_id}",
                f"{len(tx_bytes)} B",
                "⏳ Broadcast...",
                ""
            ))
            
            # Broadcaster sur Bitcoin
            threading.Thread(target=self._broadcast_tx, args=(tx_id, tx_hex, sender, tree_id), daemon=True).start()
            
            del self.pending_txs[tx_id]
            self.update_stats()
            
    def _broadcast_tx(self, tx_id, tx_hex, sender, tree_id):
        """Broadcast la transaction sur le réseau Bitcoin"""
        try:
            api_name = self.api_var.get()
            api_config = BITCOIN_APIS.get(api_name, {})
            
            if api_config.get("rpc"):
                # Bitcoin Core RPC
                btc_txid = self._broadcast_rpc(tx_hex, api_config)
            else:
                # API publique
                btc_txid = self._broadcast_api(tx_hex, api_config)
                
            # Succès !
            self.log(f"🎉 TX #{tx_id} broadcastée! TXID: {btc_txid}", "btc")
            self.send_ack(tx_id, sender)
            
            # Mettre à jour l'affichage
            self.root.after(0, lambda: self.tx_tree.item(tree_id, values=(
                time.strftime("%H:%M:%S"),
                f"#{tx_id}",
                f"",
                "✅ Broadcastée",
                btc_txid
            )))
            
            self.update_stats()
            
        except Exception as e:
            self.log(f"❌ Échec broadcast TX #{tx_id}: {e}", "error")
            self.send_error(tx_id, BTX_ERR_BROADCAST_FAIL, sender)
            
            self.root.after(0, lambda: self.tx_tree.item(tree_id, values=(
                time.strftime("%H:%M:%S"),
                f"#{tx_id}",
                f"",
                f"❌ Erreur",
                str(e)[:50]
            )))
            
    def _broadcast_api(self, tx_hex, api_config):
        """Broadcast via API publique (Mempool, Blockstream)"""
        if self.tor_var.get() and "onion" in api_config:
            url = api_config["onion"]
        else:
            network = self.network_var.get()
            if network == "testnet" and "testnet" in api_config:
                url = api_config["testnet"]
            else:
                url = api_config["clearnet"]
                
        self.log(f"📡 Broadcast vers {url}...", "info")
        
        r = self.session.post(url, data=tx_hex, timeout=30,
                              headers={"Content-Type": "text/plain"})
        
        if r.status_code == 200:
            return r.text.strip()  # Le TXID
        else:
            error_text = r.text.strip()
            
            # Si la TX est déjà dans le mempool/blockchain, calculer le TXID
            if "already" in error_text.lower() or "exist" in error_text.lower() or "duplicate" in error_text.lower():
                # Calculer le TXID à partir du hex
                txid = self._calculate_txid(tx_hex)
                self.log(f"ℹ️ TX déjà dans le mempool/blockchain", "warning")
                return txid  # Retourner quand même le TXID calculé
            
            raise Exception(f"HTTP {r.status_code}: {error_text[:100]}")
    
    def _calculate_txid(self, tx_hex):
        """Calcule le TXID à partir du hex de la transaction (supporte SegWit)"""
        import hashlib
        tx_bytes = bytes.fromhex(tx_hex)
        
        # Vérifier si c'est une transaction SegWit (marker 0x00, flag 0x01 après version)
        if len(tx_bytes) > 6 and tx_bytes[4] == 0x00 and tx_bytes[5] == 0x01:
            # C'est une SegWit transaction - il faut sérialiser sans witness pour le TXID
            try:
                tx_for_txid = self._strip_witness(tx_bytes)
            except:
                tx_for_txid = tx_bytes  # Fallback
        else:
            # Transaction legacy
            tx_for_txid = tx_bytes
        
        # Double SHA256
        hash1 = hashlib.sha256(tx_for_txid).digest()
        hash2 = hashlib.sha256(hash1).digest()
        # Inverser (little-endian -> big-endian pour affichage)
        txid = hash2[::-1].hex()
        return txid
    
    def _strip_witness(self, tx_bytes):
        """Enlève les données witness d'une transaction SegWit pour calculer le TXID"""
        import struct
        
        # Version (4 bytes)
        version = tx_bytes[0:4]
        
        # Skip marker (0x00) et flag (0x01)
        pos = 6
        
        # Lire le nombre d'inputs (varint)
        input_count, varint_size = self._read_varint(tx_bytes, pos)
        pos += varint_size
        
        inputs = []
        for _ in range(input_count):
            # txid (32) + vout (4) + script_len (varint) + script + sequence (4)
            input_start = pos
            pos += 36  # txid + vout
            script_len, vs = self._read_varint(tx_bytes, pos)
            pos += vs + script_len + 4  # script + sequence
            inputs.append(tx_bytes[input_start:pos])
        
        # Lire le nombre d'outputs (varint)
        output_count, varint_size = self._read_varint(tx_bytes, pos)
        pos += varint_size
        
        outputs = []
        for _ in range(output_count):
            output_start = pos
            pos += 8  # amount
            script_len, vs = self._read_varint(tx_bytes, pos)
            pos += vs + script_len
            outputs.append(tx_bytes[output_start:pos])
        
        # Skip witness data - on va directement au locktime (4 derniers bytes)
        locktime = tx_bytes[-4:]
        
        # Reconstruire la TX sans witness
        result = version
        result += self._encode_varint(input_count)
        for inp in inputs:
            result += inp
        result += self._encode_varint(output_count)
        for out in outputs:
            result += out
        result += locktime
        
        return result
    
    def _read_varint(self, data, pos):
        """Lit un varint Bitcoin et retourne (valeur, taille)"""
        first = data[pos]
        if first < 0xfd:
            return first, 1
        elif first == 0xfd:
            return int.from_bytes(data[pos+1:pos+3], 'little'), 3
        elif first == 0xfe:
            return int.from_bytes(data[pos+1:pos+5], 'little'), 5
        else:
            return int.from_bytes(data[pos+1:pos+9], 'little'), 9
    
    def _encode_varint(self, n):
        """Encode un entier en varint Bitcoin"""
        if n < 0xfd:
            return bytes([n])
        elif n <= 0xffff:
            return bytes([0xfd]) + n.to_bytes(2, 'little')
        elif n <= 0xffffffff:
            return bytes([0xfe]) + n.to_bytes(4, 'little')
        else:
            return bytes([0xff]) + n.to_bytes(8, 'little')
            
    def _broadcast_rpc(self, tx_hex, api_config):
        """Broadcast via Bitcoin Core RPC"""
        url = api_config["clearnet"]
        auth = (self.rpc_user.get(), self.rpc_pass.get())
        
        data = {
            "jsonrpc": "1.0",
            "method": "sendrawtransaction",
            "params": [tx_hex]
        }
        
        r = self.session.post(url, json=data, auth=auth, timeout=30)
        result = r.json()
        
        if "result" in result and result["result"]:
            return result["result"]  # Le TXID
        elif "error" in result:
            raise Exception(result["error"].get("message", "RPC Error"))
        else:
            raise Exception("Réponse RPC invalide")
            
    def send_ack(self, tx_id, dest):
        """Envoie ACK au sender"""
        if self.interface:
            msg = struct.pack("<BB", BTX_MSG_TX_ACK, tx_id)
            try:
                self.interface.sendData(msg, portNum=PRIVATE_APP_PORT, destId=dest)
                self.log(f"  → ACK envoyé pour TX #{tx_id}", "info")
            except:
                pass
                
    def send_error(self, tx_id, error_code, dest):
        """Envoie ERROR au sender"""
        if self.interface:
            msg = struct.pack("<BBB", BTX_MSG_TX_ERROR, tx_id, error_code)
            try:
                self.interface.sendData(msg, portNum=PRIVATE_APP_PORT, destId=dest)
                self.log(f"  → ERROR {error_code} envoyé pour TX #{tx_id}", "error")
            except:
                pass
                
    def update_stats(self):
        """Met à jour les statistiques"""
        received = sum(1 for item in self.tx_tree.get_children())
        broadcast = sum(1 for item in self.tx_tree.get_children() 
                       if "✅" in self.tx_tree.item(item)["values"][3])
        failed = sum(1 for item in self.tx_tree.get_children() 
                    if "❌" in self.tx_tree.item(item)["values"][3])
        pending = len(self.pending_txs)
        
        self.root.after(0, lambda: [
            self.stat_received.configure(text=f"Reçues: {received}"),
            self.stat_broadcast.configure(text=f"Broadcastées: {broadcast}"),
            self.stat_failed.configure(text=f"Échouées: {failed}"),
            self.stat_pending.configure(text=f"En attente: {pending}")
        ])
        
    def cleanup_timer(self):
        """Nettoie les transactions expirées"""
        expired = [tx_id for tx_id, tx in self.pending_txs.items() if tx.is_expired()]
        for tx_id in expired:
            self.log(f"⏰ TX #{tx_id} expirée (timeout)", "warning")
            self.send_error(tx_id, BTX_ERR_TIMEOUT, self.pending_txs[tx_id].sender)
            del self.pending_txs[tx_id]
            
        if expired:
            self.update_stats()
            
        self.root.after(5000, self.cleanup_timer)
        
    def log(self, message, tag="info"):
        def _log():
            self.log_text.configure(state=tk.NORMAL)
            timestamp = time.strftime("%H:%M:%S")
            self.log_text.insert(tk.END, f"[{timestamp}] {message}\n", tag)
            self.log_text.see(tk.END)
            self.log_text.configure(state=tk.DISABLED)
        self.root.after(0, _log)
        
    def on_closing(self):
        self.disconnect_mesh()
        self.root.destroy()


if __name__ == "__main__":
    root = tk.Tk()
    app = BitcoinMeshGateway(root)
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    root.mainloop()
