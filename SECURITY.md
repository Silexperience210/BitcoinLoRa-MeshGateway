# Security Policy

## 🔐 Security Considerations

BitcoinMeshRelay handles Bitcoin transactions. While the protocol is designed with security in mind, please understand the following:

### What This Project Does NOT Protect Against

1. **Compromised signing device** - If your transaction is signed on a compromised device, the attacker already has your keys
2. **Invalid transactions** - The mesh will relay any data; validation happens on the Bitcoin network
3. **Traffic analysis** - While Tor hides the gateway's IP, mesh radio transmissions can be physically located
4. **Physical access** - Anyone with physical access to a node can extract stored transactions

### What This Project DOES Provide

1. **Censorship resistance** - Transactions can be broadcast even without direct Internet access
2. **IP privacy** (with Tor) - The gateway's IP is hidden from Bitcoin network observers
3. **Redundancy** - Multiple paths to reach the Bitcoin network

## 🚨 Reporting Security Vulnerabilities

If you discover a security vulnerability, please:

1. **DO NOT** open a public GitHub issue
2. **Email** security concerns to the maintainer privately
3. **Include**:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

## ⚠️ Disclaimer

This software is provided "AS IS" without warranty of any kind. The authors are not responsible for:

- Lost or stolen Bitcoin
- Failed transaction broadcasts
- Legal issues in your jurisdiction
- Any other damages arising from use of this software

**Always test with small amounts first. Use testnet for development.**

## 🔒 Best Practices

1. **Use hardware wallets** for signing transactions
2. **Verify transaction details** before broadcasting
3. **Keep your node's firmware updated**
4. **Use Tor** when possible for gateway connections
5. **Don't reuse node IDs** if operational security is critical
