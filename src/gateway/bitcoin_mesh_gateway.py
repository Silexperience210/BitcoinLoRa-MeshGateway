
import re
import binascii
import logging
from collections import defaultdict

logging.basicConfig(level=logging.INFO, format='[%(levelname)s] %(message)s')

# Dictionnaire pour stocker les chunks reçus par expéditeur
chunks_buffer = defaultdict(dict)  # {sender: {chunk_num: hexdata}}
chunks_total = defaultdict(int)    # {sender: total_chunks}

def handle_btx_chunk(text, sender="default"):
    """
    Détecte et assemble les messages BTX: BTX:n/total:hexdata
    """
    logging.info(f"[BTX] Reçu de {sender}: {text}")
    if not text.startswith("BTX:"):
        return False
    try:
        parts = text.split(":", 2)
        if len(parts) != 3:
            logging.error(f"❌ Format BTX invalide: {text}")
            return False
        _, chunk_info, hexdata = parts
        if not re.match(r"^\d+/\d+$", chunk_info):
            logging.error(f"❌ Chunk info invalide: {chunk_info}")
            return False
        num, total = map(int, chunk_info.split("/"))
        try:
            binascii.unhexlify(hexdata)
        except Exception as e:
            logging.error(f"❌ Hex invalide: {hexdata} ({e})")
            return False
        # Stocke le chunk
        chunks_buffer[sender][num] = hexdata
        chunks_total[sender] = total
        logging.info(f"📦 Partie {num}/{total} reçue de {sender} ({len(hexdata)} chars)")
        # Vérifie si tous les chunks sont reçus
        if len(chunks_buffer[sender]) == total:
            # Assemble la transaction
            tx_hex = ''.join(chunks_buffer[sender][i] for i in range(1, total+1))
            logging.info(f"✅ TX Bitcoin complète détectée! ({total} parties)")
            logging.info(f"TX HEX: {tx_hex[:32]}... ({len(tx_hex)} chars)")
            # Nettoie le buffer
            del chunks_buffer[sender]
            del chunks_total[sender]
        else:
            logging.info(f"   Total accumulé: {sum(len(v) for v in chunks_buffer[sender].values())} chars")
            logging.info(f"   ⏳ En attente de plus de données...")
        return True
    except Exception as e:
        logging.error(f"❌ Exception BTX: {e}")
        return False

# Exemple d'utilisation :
if __name__ == "__main__":
    # Simulation de réception de 3 chunks
    handle_btx_chunk("BTX:1/3:01000000abcdef", sender="alice")
    handle_btx_chunk("BTX:2/3:1234567890", sender="alice")
    handle_btx_chunk("BTX:3/3:deadbeef", sender="alice")
