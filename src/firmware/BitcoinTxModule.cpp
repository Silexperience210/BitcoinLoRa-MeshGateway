#include "configuration.h"

#ifndef MESHTASTIC_EXCLUDE_BITCOIN_TX

#include "BitcoinTxModule.h"
#include "MeshService.h"
#include "NodeDB.h"
#include "Router.h"
#include "main.h"

BitcoinTxModule *bitcoinTxModule = nullptr;

BitcoinTxModule::BitcoinTxModule()
    : SinglePortModule("BitcoinTx", BITCOIN_TX_PORTNUM)
{
    // Reset receive buffer
    memset(rxBuffer, 0, sizeof(rxBuffer));
    rxBufferLen = 0;
    rxTxId = 0;
    rxExpectedChunks = 0;
    rxReceivedChunks = 0;
    
    LOG_INFO("Bitcoin TX Module initialized - Max TX size: %d bytes", BITCOIN_TX_MAX_SIZE);
}

ProcessMessage BitcoinTxModule::handleReceived(const meshtastic_MeshPacket &mp)
{
    // Only process if we have payload data
    if (mp.decoded.payload.size < 2) {
        LOG_WARN("BitcoinTx: Received packet too small");
        return ProcessMessage::CONTINUE;
    }

    const uint8_t *payload = mp.decoded.payload.bytes;
    size_t len = mp.decoded.payload.size;
    uint8_t msgType = payload[0];

    LOG_DEBUG("BitcoinTx: Received msg type 0x%02X from 0x%08X, len=%d", 
              msgType, mp.from, len);

    switch (msgType) {
        case BTX_MSG_TX_START:
            handleTxStart(payload, len, mp.from);
            break;
        case BTX_MSG_TX_CHUNK:
            handleTxChunk(payload, len, mp.from);
            break;
        case BTX_MSG_TX_END:
            handleTxEnd(payload, len, mp.from);
            break;
        case BTX_MSG_TX_ACK:
            LOG_INFO("BitcoinTx: Received ACK from 0x%08X", mp.from);
            break;
        case BTX_MSG_TX_ERROR:
            LOG_WARN("BitcoinTx: Received ERROR from 0x%08X: %s", 
                     mp.from, (const char *)(payload + 5));
            break;
        default:
            LOG_WARN("BitcoinTx: Unknown message type 0x%02X", msgType);
            break;
    }

    return ProcessMessage::CONTINUE;
}

void BitcoinTxModule::handleTxStart(const uint8_t *payload, size_t len, uint32_t from)
{
    // Format: [type(1)] [txId(4)] [totalLen(2)] [numChunks(1)]
    if (len < 8) {
        LOG_WARN("BitcoinTx: TX_START too short");
        sendError(0, from, "Invalid START packet");
        return;
    }

    uint32_t txId = (payload[1] << 24) | (payload[2] << 16) | (payload[3] << 8) | payload[4];
    uint16_t totalLen = (payload[5] << 8) | payload[6];
    uint8_t numChunks = payload[7];

    LOG_INFO("BitcoinTx: Starting receive of TX 0x%08X, size=%d, chunks=%d", 
             txId, totalLen, numChunks);

    if (totalLen > BITCOIN_TX_MAX_SIZE) {
        LOG_WARN("BitcoinTx: Transaction too large: %d > %d", totalLen, BITCOIN_TX_MAX_SIZE);
        sendError(txId, from, "TX too large");
        return;
    }

    // Initialize receive state
    rxTxId = txId;
    rxExpectedChunks = numChunks;
    rxReceivedChunks = 0;
    rxBufferLen = 0;
    memset(rxBuffer, 0, sizeof(rxBuffer));

    sendAck(txId, from);
}

void BitcoinTxModule::handleTxChunk(const uint8_t *payload, size_t len, uint32_t from)
{
    // Format: [type(1)] [txId(4)] [chunkNum(1)] [data(...)]
    if (len < 7) {
        LOG_WARN("BitcoinTx: TX_CHUNK too short");
        return;
    }

    uint32_t txId = (payload[1] << 24) | (payload[2] << 16) | (payload[3] << 8) | payload[4];
    uint8_t chunkNum = payload[5];
    const uint8_t *chunkData = payload + 6;
    size_t chunkLen = len - 6;

    if (txId != rxTxId) {
        LOG_WARN("BitcoinTx: Unexpected TX ID 0x%08X (expected 0x%08X)", txId, rxTxId);
        return;
    }

    LOG_DEBUG("BitcoinTx: Received chunk %d/%d, len=%d", 
              chunkNum + 1, rxExpectedChunks, chunkLen);

    // Append chunk data to buffer
    if (rxBufferLen + chunkLen > BITCOIN_TX_MAX_SIZE) {
        LOG_WARN("BitcoinTx: Buffer overflow, resetting");
        rxBufferLen = 0;
        sendError(txId, from, "Buffer overflow");
        return;
    }

    memcpy(rxBuffer + rxBufferLen, chunkData, chunkLen);
    rxBufferLen += chunkLen;
    rxReceivedChunks++;
}

void BitcoinTxModule::handleTxEnd(const uint8_t *payload, size_t len, uint32_t from)
{
    // Format: [type(1)] [txId(4)] [checksum(4)]
    if (len < 9) {
        LOG_WARN("BitcoinTx: TX_END too short");
        return;
    }

    uint32_t txId = (payload[1] << 24) | (payload[2] << 16) | (payload[3] << 8) | payload[4];
    uint32_t checksum = (payload[5] << 24) | (payload[6] << 16) | (payload[7] << 8) | payload[8];

    if (txId != rxTxId) {
        LOG_WARN("BitcoinTx: Unexpected TX ID in END");
        return;
    }

    // Simple checksum verification (sum of all bytes mod 2^32)
    uint32_t calcChecksum = 0;
    for (size_t i = 0; i < rxBufferLen; i++) {
        calcChecksum += rxBuffer[i];
    }

    if (calcChecksum != checksum) {
        LOG_WARN("BitcoinTx: Checksum mismatch: calc=0x%08X, recv=0x%08X", 
                 calcChecksum, checksum);
        sendError(txId, from, "Checksum error");
        return;
    }

    LOG_INFO("BitcoinTx: Complete TX received! Size=%d bytes, from=0x%08X", 
             rxBufferLen, from);

    // Process the complete transaction
    onTransactionReceived(rxBuffer, rxBufferLen, from);

    // Send acknowledgement
    sendAck(txId, from);

    // Reset state
    rxTxId = 0;
    rxBufferLen = 0;
    rxExpectedChunks = 0;
    rxReceivedChunks = 0;
}

void BitcoinTxModule::sendAck(uint32_t txId, uint32_t to)
{
    uint8_t ackPacket[5];
    ackPacket[0] = BTX_MSG_TX_ACK;
    ackPacket[1] = (txId >> 24) & 0xFF;
    ackPacket[2] = (txId >> 16) & 0xFF;
    ackPacket[3] = (txId >> 8) & 0xFF;
    ackPacket[4] = txId & 0xFF;

    meshtastic_MeshPacket *p = allocDataPacket();
    if (p) {
        p->to = to;
        p->decoded.portnum = BITCOIN_TX_PORTNUM;
        p->decoded.payload.size = sizeof(ackPacket);
        memcpy(p->decoded.payload.bytes, ackPacket, sizeof(ackPacket));
        p->want_ack = false;
        
        service->sendToMesh(p, RX_SRC_LOCAL, true);
        LOG_DEBUG("BitcoinTx: Sent ACK for TX 0x%08X to 0x%08X", txId, to);
    }
}

void BitcoinTxModule::sendError(uint32_t txId, uint32_t to, const char *errorMsg)
{
    size_t msgLen = strlen(errorMsg);
    size_t packetLen = 5 + msgLen + 1;
    
    uint8_t *errorPacket = new uint8_t[packetLen];
    errorPacket[0] = BTX_MSG_TX_ERROR;
    errorPacket[1] = (txId >> 24) & 0xFF;
    errorPacket[2] = (txId >> 16) & 0xFF;
    errorPacket[3] = (txId >> 8) & 0xFF;
    errorPacket[4] = txId & 0xFF;
    memcpy(errorPacket + 5, errorMsg, msgLen + 1);

    meshtastic_MeshPacket *p = allocDataPacket();
    if (p) {
        p->to = to;
        p->decoded.portnum = BITCOIN_TX_PORTNUM;
        p->decoded.payload.size = packetLen;
        memcpy(p->decoded.payload.bytes, errorPacket, packetLen);
        p->want_ack = false;
        
        service->sendToMesh(p, RX_SRC_LOCAL, true);
        LOG_WARN("BitcoinTx: Sent ERROR '%s' for TX 0x%08X to 0x%08X", errorMsg, txId, to);
    }
    
    delete[] errorPacket;
}

bool BitcoinTxModule::sendBitcoinTx(const uint8_t *txData, size_t txLen, uint32_t destNode)
{
    if (txLen > BITCOIN_TX_MAX_SIZE) {
        LOG_WARN("BitcoinTx: Transaction too large to send: %d > %d", txLen, BITCOIN_TX_MAX_SIZE);
        return false;
    }

    // Generate a random transaction ID
    uint32_t txId = random(0, UINT32_MAX);
    uint8_t numChunks = (txLen + BITCOIN_TX_CHUNK_SIZE - 1) / BITCOIN_TX_CHUNK_SIZE;

    LOG_INFO("BitcoinTx: Sending TX 0x%08X, size=%d, chunks=%d", txId, txLen, numChunks);

    // Send TX_START
    {
        uint8_t startPacket[8];
        startPacket[0] = BTX_MSG_TX_START;
        startPacket[1] = (txId >> 24) & 0xFF;
        startPacket[2] = (txId >> 16) & 0xFF;
        startPacket[3] = (txId >> 8) & 0xFF;
        startPacket[4] = txId & 0xFF;
        startPacket[5] = (txLen >> 8) & 0xFF;
        startPacket[6] = txLen & 0xFF;
        startPacket[7] = numChunks;

        meshtastic_MeshPacket *p = allocDataPacket();
        if (!p) return false;
        
        p->to = destNode;
        p->decoded.portnum = BITCOIN_TX_PORTNUM;
        p->decoded.payload.size = sizeof(startPacket);
        memcpy(p->decoded.payload.bytes, startPacket, sizeof(startPacket));
        p->want_ack = true;
        
        service->sendToMesh(p, RX_SRC_LOCAL, true);
    }

    // Send chunks with delay between them to avoid overwhelming the mesh
    for (uint8_t i = 0; i < numChunks; i++) {
        size_t offset = i * BITCOIN_TX_CHUNK_SIZE;
        size_t chunkLen = min((size_t)BITCOIN_TX_CHUNK_SIZE, txLen - offset);
        
        uint8_t *chunkPacket = new uint8_t[6 + chunkLen];
        chunkPacket[0] = BTX_MSG_TX_CHUNK;
        chunkPacket[1] = (txId >> 24) & 0xFF;
        chunkPacket[2] = (txId >> 16) & 0xFF;
        chunkPacket[3] = (txId >> 8) & 0xFF;
        chunkPacket[4] = txId & 0xFF;
        chunkPacket[5] = i;
        memcpy(chunkPacket + 6, txData + offset, chunkLen);

        meshtastic_MeshPacket *p = allocDataPacket();
        if (p) {
            p->to = destNode;
            p->decoded.portnum = BITCOIN_TX_PORTNUM;
            p->decoded.payload.size = 6 + chunkLen;
            memcpy(p->decoded.payload.bytes, chunkPacket, 6 + chunkLen);
            p->want_ack = false;
            
            service->sendToMesh(p, RX_SRC_LOCAL, true);
            LOG_DEBUG("BitcoinTx: Sent chunk %d/%d", i + 1, numChunks);
        }
        
        delete[] chunkPacket;
        
        // Small delay between chunks (non-blocking would be better in production)
        delay(100);
    }

    // Send TX_END with checksum
    {
        uint32_t checksum = 0;
        for (size_t i = 0; i < txLen; i++) {
            checksum += txData[i];
        }

        uint8_t endPacket[9];
        endPacket[0] = BTX_MSG_TX_END;
        endPacket[1] = (txId >> 24) & 0xFF;
        endPacket[2] = (txId >> 16) & 0xFF;
        endPacket[3] = (txId >> 8) & 0xFF;
        endPacket[4] = txId & 0xFF;
        endPacket[5] = (checksum >> 24) & 0xFF;
        endPacket[6] = (checksum >> 16) & 0xFF;
        endPacket[7] = (checksum >> 8) & 0xFF;
        endPacket[8] = checksum & 0xFF;

        meshtastic_MeshPacket *p = allocDataPacket();
        if (!p) return false;
        
        p->to = destNode;
        p->decoded.portnum = BITCOIN_TX_PORTNUM;
        p->decoded.payload.size = sizeof(endPacket);
        memcpy(p->decoded.payload.bytes, endPacket, sizeof(endPacket));
        p->want_ack = true;
        
        service->sendToMesh(p, RX_SRC_LOCAL, true);
    }

    LOG_INFO("BitcoinTx: TX 0x%08X sent successfully", txId);
    return true;
}

void BitcoinTxModule::onTransactionReceived(const uint8_t *txData, size_t txLen, uint32_t from)
{
    // Log the transaction in hex format
    LOG_INFO("BitcoinTx: === TRANSACTION RECEIVED ===");
    LOG_INFO("BitcoinTx: From: 0x%08X", from);
    LOG_INFO("BitcoinTx: Size: %d bytes", txLen);
    
    // Log first and last 32 bytes for identification
    char hexStr[65];
    size_t previewLen = min(txLen, (size_t)32);
    for (size_t i = 0; i < previewLen; i++) {
        sprintf(hexStr + (i * 2), "%02x", txData[i]);
    }
    hexStr[previewLen * 2] = '\0';
    LOG_INFO("BitcoinTx: Start: %s...", hexStr);
    
    if (txLen > 32) {
        size_t endOffset = txLen - min(txLen - 32, (size_t)32);
        for (size_t i = endOffset; i < txLen; i++) {
            sprintf(hexStr + ((i - endOffset) * 2), "%02x", txData[i]);
        }
        hexStr[(txLen - endOffset) * 2] = '\0';
        LOG_INFO("BitcoinTx: End: ...%s", hexStr);
    }
    
    LOG_INFO("BitcoinTx: ===========================");

    // TODO: In a production version, this is where you would:
    // 1. If this node has internet: broadcast to Bitcoin network via HTTP/RPC
    // 2. If no internet: relay to other nodes in the mesh
    // 3. Store transaction for later broadcasting
    
    // For nodes with WiFi/internet connectivity, you could add:
    // - HTTP POST to a Bitcoin node's RPC interface
    // - Use an Electrum server
    // - Use a public broadcast API (mempool.space, blockstream.info, etc.)
}

#endif // MESHTASTIC_EXCLUDE_BITCOIN_TX
