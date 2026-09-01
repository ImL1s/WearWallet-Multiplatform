# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability in kotlin-caip-standards, please report it responsibly.

### How to Report

1. **Do NOT** open a public GitHub issue for security vulnerabilities
2. Send a detailed report to the maintainers via GitHub Security Advisories
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

### Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 7 days
- **Resolution**: Depends on severity and complexity

## Security Considerations

### What This Library Does

kotlin-caip-standards is a **pure data formatting library** that:
- Parses and formats CAIP-2 chain identifiers
- Parses and formats CAIP-10 account addresses
- Parses and formats CAIP-19 asset identifiers
- Provides validation utilities

### What This Library Does NOT Do

- Handle private keys or cryptographic operations
- Store sensitive data
- Make network requests
- Access system resources

### Safe Usage

This library is designed to be safe by default:
- No native dependencies
- No network access
- No file system access
- Pure data transformation only

## Acknowledgments

We appreciate security researchers who help keep our users safe.
