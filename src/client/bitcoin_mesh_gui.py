#!/usr/bin/env python3
"""
Bitcoin Mesh GUI - Interface pour envoyer des transactions Bitcoin via Meshtastic LoRa
"""

import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox, filedialog
import threading
import struct
import time
import serial.tools.list_ports

try:
    import meshtastic
    import meshtastic.serial_interface
    from pubsub import pub
except ImportError:
    print("Installation des dépendances...")
    import subprocess
    subprocess.check_call(["pip", "install", "meshtastic", "pypubsub"])
    import meshtastic
    import meshtastic.serial_interface
    from pubsub import pub

# Constantes du protocole BitcoinTxModule
BTX_MSG_TX_START = 0x01
BTX_MSG_TX_CHUNK = 0x02
BTX_MSG_TX_END   = 0x03
BTX_MSG_TX_ACK   = 0x04
BTX_MSG_TX_ERROR = 0x05
BTX_CHUNK_SIZE   = 180
BTX_MAX_TX_SIZE  = 2048
PRIVATE_APP_PORT = 256


class BitcoinMeshGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("₿ Bitcoin Mesh Relay")
        self.root.geometry("700x600")
        self.root.configure(bg="#1a1a2e")
        
        self.interface = None
        self.tx_id = 0
        self.connected = False
        
        self.setup_styles()
        self.create_widgets()
        self.refresh_ports()
        
    def setup_styles(self):
        style = ttk.Style()
        style.theme_use('clam')
        
        # Couleurs Bitcoin
        style.configure("Title.TLabel", 
                       font=("Segoe UI", 18, "bold"),
                       foreground="#f7931a",
                       background="#1a1a2e")
        style.configure("TLabel", 
                       font=("Segoe UI", 10),
                       foreground="#ffffff",
                       background="#1a1a2e")
        style.configure("TFrame", background="#1a1a2e")
        style.configure("TButton", 
                       font=("Segoe UI", 10, "bold"),
                       padding=10)
        style.configure("Connect.TButton",
                       font=("Segoe UI", 10, "bold"))
        style.configure("Send.TButton",
                       font=("Segoe UI", 12, "bold"))
        style.configure("TCombobox", padding=5)
        
    def create_widgets(self):
        # Frame principal
        main_frame = ttk.Frame(self.root, padding=20)
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Titre
        title_frame = ttk.Frame(main_frame)
        title_frame.pack(fill=tk.X, pady=(0, 20))
        
        ttk.Label(title_frame, 
                 text="₿ Bitcoin Mesh Relay",
                 style="Title.TLabel").pack(side=tk.LEFT)
        
        self.status_label = ttk.Label(title_frame, 
                                      text="● Déconnecté",
                                      foreground="#ff6b6b")
        self.status_label.pack(side=tk.RIGHT)
        
        # Section connexion
        conn_frame = ttk.LabelFrame(main_frame, text="Connexion", padding=10)
        conn_frame.pack(fill=tk.X, pady=(0, 15))
        
        port_frame = ttk.Frame(conn_frame)
        port_frame.pack(fill=tk.X)
        
        ttk.Label(port_frame, text="Port:").pack(side=tk.LEFT, padx=(0, 10))
        
        self.port_var = tk.StringVar()
        self.port_combo = ttk.Combobox(port_frame, 
                                        textvariable=self.port_var,
                                        width=15,
                                        state="readonly")
        self.port_combo.pack(side=tk.LEFT, padx=(0, 10))
        
        ttk.Button(port_frame, 
                  text="🔄",
                  width=3,
                  command=self.refresh_ports).pack(side=tk.LEFT, padx=(0, 10))
        
        self.connect_btn = ttk.Button(port_frame,
                                      text="Connecter",
                                      style="Connect.TButton",
                                      command=self.toggle_connection)
        self.connect_btn.pack(side=tk.LEFT)
        
        # Section transaction
        tx_frame = ttk.LabelFrame(main_frame, text="Transaction Bitcoin", padding=10)
        tx_frame.pack(fill=tk.BOTH, expand=True, pady=(0, 15))
        
        # Zone de texte pour la transaction
        ttk.Label(tx_frame, text="Transaction hex brute:").pack(anchor=tk.W)
        
        self.tx_text = scrolledtext.ScrolledText(tx_frame,
                                                  height=8,
                                                  font=("Consolas", 9),
                                                  bg="#0f0f23",
                                                  fg="#00ff88",
                                                  insertbackground="#f7931a")
        self.tx_text.pack(fill=tk.BOTH, expand=True, pady=(5, 10))
        
        # Boutons transaction
        tx_btn_frame = ttk.Frame(tx_frame)
        tx_btn_frame.pack(fill=tk.X)
        
        ttk.Button(tx_btn_frame,
                  text="📁 Charger fichier",
                  command=self.load_tx_file).pack(side=tk.LEFT, padx=(0, 10))
        
        ttk.Button(tx_btn_frame,
                  text="📋 Coller",
                  command=self.paste_tx).pack(side=tk.LEFT, padx=(0, 10))
        
        ttk.Button(tx_btn_frame,
                  text="🗑️ Effacer",
                  command=self.clear_tx).pack(side=tk.LEFT)
        
        # Info taille
        self.size_label = ttk.Label(tx_btn_frame, text="Taille: 0 octets")
        self.size_label.pack(side=tk.RIGHT)
        
        # Bind pour mettre à jour la taille
        self.tx_text.bind("<KeyRelease>", self.update_tx_size)
        
        # Section destination
        dest_frame = ttk.Frame(main_frame)
        dest_frame.pack(fill=tk.X, pady=(0, 15))
        
        ttk.Label(dest_frame, text="Destination:").pack(side=tk.LEFT, padx=(0, 10))
        
        self.dest_var = tk.StringVar(value="broadcast")
        ttk.Radiobutton(dest_frame, 
                       text="Broadcast (tous)",
                       variable=self.dest_var,
                       value="broadcast").pack(side=tk.LEFT, padx=(0, 20))
        ttk.Radiobutton(dest_frame,
                       text="Node ID:",
                       variable=self.dest_var,
                       value="specific").pack(side=tk.LEFT)
        
        self.dest_id_entry = ttk.Entry(dest_frame, width=12)
        self.dest_id_entry.pack(side=tk.LEFT, padx=(5, 0))
        self.dest_id_entry.insert(0, "!abcd1234")
        
        # Bouton envoyer
        self.send_btn = ttk.Button(main_frame,
                                   text="📡 ENVOYER SUR LE MESH",
                                   style="Send.TButton",
                                   command=self.send_transaction,
                                   state=tk.DISABLED)
        self.send_btn.pack(fill=tk.X, pady=(0, 15))
        
        # Log
        log_frame = ttk.LabelFrame(main_frame, text="Journal", padding=10)
        log_frame.pack(fill=tk.BOTH, expand=True)
        
        self.log_text = scrolledtext.ScrolledText(log_frame,
                                                   height=8,
                                                   font=("Consolas", 9),
                                                   bg="#0f0f23",
                                                   fg="#aaaaaa",
                                                   state=tk.DISABLED)
        self.log_text.pack(fill=tk.BOTH, expand=True)
        
        # Tags pour les couleurs du log
        self.log_text.tag_configure("info", foreground="#00aaff")
        self.log_text.tag_configure("success", foreground="#00ff88")
        self.log_text.tag_configure("error", foreground="#ff6b6b")
        self.log_text.tag_configure("warning", foreground="#f7931a")
        
    def refresh_ports(self):
        """Actualise la liste des ports série"""
        ports = [p.device for p in serial.tools.list_ports.comports()]
        self.port_combo['values'] = ports
        if ports:
            self.port_combo.current(0)
        self.log("Ports détectés: " + ", ".join(ports) if ports else "Aucun port trouvé", "info")
        
    def toggle_connection(self):
        """Connecte ou déconnecte du périphérique"""
        if self.connected:
            self.disconnect()
        else:
            self.connect()
            
    def connect(self):
        """Connecte au périphérique Meshtastic"""
        port = self.port_var.get()
        if not port:
            messagebox.showerror("Erreur", "Sélectionnez un port")
            return
            
        try:
            self.log(f"Connexion à {port}...", "info")
            self.interface = meshtastic.serial_interface.SerialInterface(port)
            pub.subscribe(self.on_receive, "meshtastic.receive")
            
            self.connected = True
            self.status_label.configure(text="● Connecté", foreground="#00ff88")
            self.connect_btn.configure(text="Déconnecter")
            self.send_btn.configure(state=tk.NORMAL)
            
            # Récupérer info du nœud
            node_info = self.interface.getMyNodeInfo()
            self.log(f"✅ Connecté au nœud: {node_info.get('user', {}).get('shortName', 'N/A')}", "success")
            
        except Exception as e:
            self.log(f"❌ Erreur connexion: {e}", "error")
            messagebox.showerror("Erreur de connexion", str(e))
            
    def disconnect(self):
        """Déconnecte du périphérique"""
        try:
            if self.interface:
                pub.unsubscribe(self.on_receive, "meshtastic.receive")
                self.interface.close()
                self.interface = None
        except:
            pass
            
        self.connected = False
        self.status_label.configure(text="● Déconnecté", foreground="#ff6b6b")
        self.connect_btn.configure(text="Connecter")
        self.send_btn.configure(state=tk.DISABLED)
        self.log("Déconnecté", "info")
        
    def on_receive(self, packet, interface):
        """Callback pour les messages reçus"""
        try:
            decoded = packet.get("decoded", {})
            portnum = decoded.get("portnum")
            
            if portnum == "PRIVATE_APP":
                payload = decoded.get("payload", b"")
                if len(payload) > 0:
                    msg_type = payload[0]
                    
                    if msg_type == BTX_MSG_TX_ACK:
                        tx_id = payload[1] if len(payload) > 1 else 0
                        self.log(f"✅ ACK reçu pour TX #{tx_id}", "success")
                        
                    elif msg_type == BTX_MSG_TX_ERROR:
                        tx_id = payload[1] if len(payload) > 1 else 0
                        err_code = payload[2] if len(payload) > 2 else 0
                        errors = {1: "TX trop grande", 2: "Timeout", 3: "Chunks manquants"}
                        err_msg = errors.get(err_code, f"Code {err_code}")
                        self.log(f"❌ Erreur TX #{tx_id}: {err_msg}", "error")
                        
                    elif msg_type == BTX_MSG_TX_START:
                        tx_id = payload[1] if len(payload) > 1 else 0
                        tx_size = struct.unpack("<H", payload[2:4])[0] if len(payload) >= 4 else 0
                        self.log(f"📥 TX #{tx_id} reçue: {tx_size} octets", "warning")
                        
        except Exception as e:
            self.log(f"Erreur parsing: {e}", "error")
            
    def send_transaction(self):
        """Envoie la transaction sur le mesh"""
        if not self.connected:
            messagebox.showerror("Erreur", "Non connecté")
            return
            
        tx_hex = self.tx_text.get("1.0", tk.END).strip()
        tx_hex = tx_hex.replace(" ", "").replace("\n", "")
        
        if not tx_hex:
            messagebox.showerror("Erreur", "Entrez une transaction")
            return
            
        # Valider hex
        try:
            tx_bytes = bytes.fromhex(tx_hex)
        except ValueError:
            messagebox.showerror("Erreur", "Format hexadécimal invalide")
            return
            
        if len(tx_bytes) > BTX_MAX_TX_SIZE:
            messagebox.showerror("Erreur", f"Transaction trop grande ({len(tx_bytes)} > {BTX_MAX_TX_SIZE} octets)")
            return
            
        # Destination
        if self.dest_var.get() == "broadcast":
            dest_id = 0xFFFFFFFF
        else:
            dest_str = self.dest_id_entry.get().strip()
            try:
                if dest_str.startswith("!"):
                    dest_id = int(dest_str[1:], 16)
                else:
                    dest_id = int(dest_str, 16)
            except:
                messagebox.showerror("Erreur", "ID destination invalide")
                return
        
        # Envoyer dans un thread
        threading.Thread(target=self._send_tx_thread, args=(tx_bytes, dest_id), daemon=True).start()
        
    def _send_tx_thread(self, tx_bytes, dest_id):
        """Thread d'envoi de la transaction"""
        try:
            tx_size = len(tx_bytes)
            self.tx_id = (self.tx_id + 1) % 256
            num_chunks = (tx_size + BTX_CHUNK_SIZE - 1) // BTX_CHUNK_SIZE
            
            self.log(f"📡 Envoi TX #{self.tx_id}: {tx_size} octets en {num_chunks} chunks", "info")
            
            # 1. TX_START
            start_msg = struct.pack("<BBH", BTX_MSG_TX_START, self.tx_id, tx_size)
            self.interface.sendData(start_msg, portNum=PRIVATE_APP_PORT, destId=dest_id)
            self.log("  → TX_START envoyé", "info")
            time.sleep(0.5)
            
            # 2. Chunks
            chunk_index = 0
            for i in range(0, tx_size, BTX_CHUNK_SIZE):
                chunk = tx_bytes[i:i + BTX_CHUNK_SIZE]
                chunk_msg = struct.pack("<BBB", BTX_MSG_TX_CHUNK, self.tx_id, chunk_index) + chunk
                self.interface.sendData(chunk_msg, portNum=PRIVATE_APP_PORT, destId=dest_id)
                self.log(f"  → Chunk {chunk_index + 1}/{num_chunks}: {len(chunk)} octets", "info")
                chunk_index += 1
                time.sleep(0.3)
                
            # 3. TX_END
            end_msg = struct.pack("<BB", BTX_MSG_TX_END, self.tx_id)
            self.interface.sendData(end_msg, portNum=PRIVATE_APP_PORT, destId=dest_id)
            self.log("  → TX_END envoyé, en attente d'ACK...", "warning")
            
        except Exception as e:
            self.log(f"❌ Erreur envoi: {e}", "error")
            
    def load_tx_file(self):
        """Charge une transaction depuis un fichier"""
        file_path = filedialog.askopenfilename(
            title="Charger transaction",
            filetypes=[("Fichiers texte", "*.txt"), ("Tous fichiers", "*.*")]
        )
        if file_path:
            try:
                with open(file_path, 'r') as f:
                    self.tx_text.delete("1.0", tk.END)
                    self.tx_text.insert("1.0", f.read().strip())
                self.update_tx_size()
            except Exception as e:
                messagebox.showerror("Erreur", f"Impossible de lire le fichier: {e}")
                
    def paste_tx(self):
        """Colle depuis le presse-papiers"""
        try:
            clipboard = self.root.clipboard_get()
            self.tx_text.delete("1.0", tk.END)
            self.tx_text.insert("1.0", clipboard.strip())
            self.update_tx_size()
        except:
            pass
            
    def clear_tx(self):
        """Efface la transaction"""
        self.tx_text.delete("1.0", tk.END)
        self.update_tx_size()
        
    def update_tx_size(self, event=None):
        """Met à jour l'affichage de la taille"""
        tx_hex = self.tx_text.get("1.0", tk.END).strip()
        tx_hex = tx_hex.replace(" ", "").replace("\n", "")
        try:
            size = len(bytes.fromhex(tx_hex)) if tx_hex else 0
            color = "#00ff88" if size <= BTX_MAX_TX_SIZE else "#ff6b6b"
            self.size_label.configure(text=f"Taille: {size} octets", foreground=color)
        except:
            self.size_label.configure(text="Taille: (hex invalide)", foreground="#ff6b6b")
            
    def log(self, message, tag="info"):
        """Ajoute un message au journal"""
        def _log():
            self.log_text.configure(state=tk.NORMAL)
            timestamp = time.strftime("%H:%M:%S")
            self.log_text.insert(tk.END, f"[{timestamp}] {message}\n", tag)
            self.log_text.see(tk.END)
            self.log_text.configure(state=tk.DISABLED)
        self.root.after(0, _log)
        
    def on_closing(self):
        """Fermeture propre"""
        self.disconnect()
        self.root.destroy()


if __name__ == "__main__":
    root = tk.Tk()
    app = BitcoinMeshGUI(root)
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    root.mainloop()
