#pragma once

#include "SinglePortModule.h"
#include "configuration.h"

#ifndef MESHTASTIC_EXCLUDE_BITCOIN_TX

/**
 * Bitcoin Transaction Module for Meshtastic
 * 
 * Allows relaying raw Bitcoin transactions over the LoRa mesh network.
 * Transactions are chunked into smaller packets for transmission over the
 * bandwidth-limited mesh network.
 * 
 * Use cases:
 * - Send Bitcoin transactions from remote locations without internet
 * - Relay transactions through mesh to a gateway node with internet
 * - Emergency Bitcoin transactions via mesh network
 */

// Maximum size for a Bitcoin transaction we'll accept (in bytes)
#define BITCOIN_TX_MAX_SIZE 2048

// Chunk size for splitting large transactions (must fit in mesh packet)
#define BITCOIN_TX_CHUNK_SIZE 180

// Port number for Bitcoin TX (using private app range)
#define BITCOIN_TX_PORTNUM meshtastic_PortNum_PRIVATE_APP

// Message types for the protocol
enum BitcoinTxMsgType : uint8_t {
    BTX_MSG_TX_START = 0x01,    // Start of a new transaction
    BTX_MSG_TX_CHUNK = 0x02,    // Transaction data chunk
    BTX_MSG_TX_END = 0x03,      // End of transaction / request broadcast
    BTX_MSG_TX_ACK = 0x04,      // Acknowledgement
    BTX_MSG_TX_ERROR = 0x05,   // Error message
};

class BitcoinTxModule : public SinglePortModule
{
  public:
    BitcoinTxModule();

  protected:
    /**
     * Handle received Bitcoin TX packets from the mesh
     */
    virtual ProcessMessage handleReceived(const meshtastic_MeshPacket &mp) override;

    /**
     * Called when we want to send our own Bitcoin transaction
     */
    bool sendBitcoinTx(const uint8_t *txData, size_t txLen, uint32_t destNode = NODENUM_BROADCAST);

  private:
    // Buffer for assembling incoming transaction chunks
    uint8_t rxBuffer[BITCOIN_TX_MAX_SIZE];
    size_t rxBufferLen = 0;
    uint32_t rxTxId = 0;        // Transaction ID being received
    uint8_t rxExpectedChunks = 0;
    uint8_t rxReceivedChunks = 0;

    // Process different message types
    void handleTxStart(const uint8_t *payload, size_t len, uint32_t from);
    void handleTxChunk(const uint8_t *payload, size_t len, uint32_t from);
    void handleTxEnd(const uint8_t *payload, size_t len, uint32_t from);
    
    // Send acknowledgement or error
    void sendAck(uint32_t txId, uint32_t to);
    void sendError(uint32_t txId, uint32_t to, const char *errorMsg);
    
    // Callback when full transaction is received
    void onTransactionReceived(const uint8_t *txData, size_t txLen, uint32_t from);
};

extern BitcoinTxModule *bitcoinTxModule;

#endif // MESHTASTIC_EXCLUDE_BITCOIN_TX
