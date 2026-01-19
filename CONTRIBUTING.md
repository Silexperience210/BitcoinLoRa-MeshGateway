# 🤝 Contributing to BitcoinMesh Gateway

Thank you for your interest in contributing to BitcoinMesh Gateway! This project exists to make Bitcoin truly uncensorable by enabling transaction broadcast over mesh networks.

---

## 🌟 Ways to Contribute

### 1. 📖 Documentation
- Improve existing documentation
- Translate to other languages
- Add usage examples
- Write tutorials

### 2. 🐛 Bug Reports
- Test on different hardware
- Report issues with detailed information
- Help reproduce and diagnose bugs

### 3. 💡 Feature Requests
- Propose new features
- Discuss implementation approaches
- Help prioritize roadmap

### 4. 🔧 Code Contributions
- Fix bugs
- Implement new features
- Improve performance
- Add tests

---

## 🚀 Getting Started

### Prerequisites

1. **Hardware**: LILYGO T-Beam v1.2 (or compatible)
2. **Software**:
   - Python 3.8+
   - PlatformIO
   - Git

### Setting Up Development Environment

```bash
# Clone the repository
git clone https://github.com/Silexperience/BitcoinMeshGateway.git
cd BitcoinMeshGateway

# Install Python dependencies
pip install -r requirements.txt

# Clone Meshtastic firmware (for building)
git clone https://github.com/meshtastic/firmware.git meshtastic-firmware
cd meshtastic-firmware
git submodule update --init --recursive

# Copy BitcoinTxModule files
cp ../src/firmware/BitcoinTxModule.* src/modules/
```

---

## 📝 Code Style

### C++ (Firmware)

Follow Meshtastic coding conventions:

```cpp
// Use meaningful names
class BitcoinTxModule : public SinglePortModule {
  public:
    BitcoinTxModule();
    
  protected:
    // Use camelCase for methods
    virtual ProcessMessage handleReceived(const meshtastic_MeshPacket &mp) override;
    
  private:
    // Use camelCase for member variables
    std::vector<uint8_t> txBuffer;
    uint32_t lastChunkTime;
};

// Use LOG_* macros for logging
LOG_INFO("BitcoinTx: Transaction received, size=%d", size);
```

### Python (Gateway & Client)

Follow PEP 8 with some flexibility:

```python
# Use type hints where helpful
def broadcast_transaction(self, tx_hex: str) -> Tuple[bool, str]:
    """
    Broadcast a transaction to the Bitcoin network.
    
    Args:
        tx_hex: The transaction in hexadecimal format
        
    Returns:
        Tuple of (success, txid_or_error_message)
    """
    pass

# Use descriptive variable names
chunk_sequence_number = 0
transaction_buffer = bytearray()
```

---

## 🔀 Pull Request Process

### 1. Fork & Branch

```bash
# Fork on GitHub, then:
git clone https://github.com/YOUR_USERNAME/BitcoinMeshGateway.git
cd BitcoinMeshGateway
git checkout -b feature/your-feature-name
```

### 2. Make Changes

- Keep commits atomic and focused
- Write clear commit messages:
  ```
  feat: Add Tor onion address rotation
  
  - Implements automatic .onion endpoint rotation
  - Falls back to clearnet if all onions fail
  - Adds configuration option for retry count
  ```

### 3. Test Your Changes

- Test on actual hardware if possible
- Test both client and gateway
- Test edge cases (large transactions, network failures)

### 4. Submit PR

- Create Pull Request against `main` branch
- Fill out the PR template
- Link related issues
- Be responsive to feedback

---

## 🏗️ Project Structure

```
BitcoinMeshGateway/
├── src/
│   ├── firmware/           # Meshtastic module (C++)
│   │   ├── BitcoinTxModule.h
│   │   └── BitcoinTxModule.cpp
│   ├── gateway/            # Gateway application (Python)
│   │   └── bitcoin_mesh_gateway.py
│   └── client/             # Client GUI (Python)
│       └── bitcoin_mesh_gui.py
├── docs/                   # Documentation
│   ├── PROTOCOL.md
│   ├── SETUP_GUIDE.md
│   └── TROUBLESHOOTING.md
├── tests/                  # Test files (to be added)
└── README.md
```

---

## 🔬 Testing

### Manual Testing Checklist

- [ ] Firmware compiles for `tbeam`
- [ ] Firmware uploads successfully
- [ ] Client GUI connects to device
- [ ] Small transaction (<500 bytes) transmits
- [ ] Large transaction (>1000 bytes) transmits
- [ ] Gateway receives and reassembles
- [ ] Gateway broadcasts to testnet
- [ ] Error handling works (invalid TX)
- [ ] Tor mode works (if enabled)

### Future: Automated Tests

We plan to add:
- Unit tests for chunking logic
- Integration tests with mock mesh
- Protocol conformance tests

---

## 🎯 Roadmap

### Phase 1: Beta (Current)
- [x] Basic chunked protocol
- [x] Client GUI
- [x] Gateway with Tor support
- [ ] Comprehensive testing
- [ ] Documentation complete

### Phase 2: Stability
- [ ] Error recovery improvements
- [ ] Multiple gateway support
- [ ] Transaction deduplication
- [ ] Rate limiting

### Phase 3: Features
- [ ] Lightning Network support
- [ ] Multi-signature coordination
- [ ] PSBT support
- [ ] Mobile app

### Phase 4: Scale
- [ ] Multiple mesh network bridge
- [ ] Satellite gateway (Blockstream)
- [ ] Ham radio gateway

---

## 💬 Communication

- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: General questions and ideas
- **Twitter**: [@Silexperience](https://twitter.com/Silexperience)

---

## 📜 License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

## 🙏 Recognition

Contributors will be:
- Listed in README.md
- Mentioned in release notes
- Eternally grateful from the Bitcoin community

---

**Every contribution, no matter how small, helps make Bitcoin more resilient and censorship-resistant. Thank you! 🧡**
