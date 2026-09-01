# Security Policy

## Reporting a Vulnerability

The kotlin-utxo team takes security vulnerabilities seriously. We appreciate your efforts to responsibly disclose your findings and will make every effort to acknowledge your contributions.

### How to Report

**Please do NOT report security vulnerabilities through public GitHub issues.**

Instead, please report security vulnerabilities by emailing:

**security@cbstudio.com**

Please include the following information in your report:

- Type of vulnerability (e.g., integer overflow, incorrect UTXO selection, fee calculation errors)
- Full paths of source file(s) related to the vulnerability
- Location of the affected source code (tag/branch/commit or direct URL)
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact assessment and potential attack scenarios

### What to Expect

- **Initial Response**: We will acknowledge receipt of your vulnerability report within 48 hours.
- **Status Updates**: We will provide updates on the progress of addressing the vulnerability at least every 7 days.
- **Resolution Timeline**: We aim to resolve critical vulnerabilities within 30 days of disclosure.
- **Disclosure**: We will coordinate with you on the timing and content of any public disclosure.

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |
| < 1.0   | :x:                |

Only the latest minor version of each major release receives security updates. We recommend always using the latest stable version.

## Security Update Policy

### Severity Levels

We classify vulnerabilities using the following severity levels:

| Severity | Description | Target Resolution Time |
|----------|-------------|------------------------|
| Critical | Fund loss potential, incorrect transaction construction | 7 days |
| High | Fee manipulation, UTXO selection bypass | 14 days |
| Medium | Denial of service, resource exhaustion | 30 days |
| Low | Minor issues, hardening improvements | Next regular release |

### Update Process

1. **Security patches** are released as soon as a fix is available for Critical and High severity issues.
2. **Security advisories** are published on GitHub with CVE identifiers when applicable.
3. **Release notes** will clearly indicate security-related changes.

## Security Best Practices

When using kotlin-utxo in your projects:

### UTXO Management

- Always validate UTXO data before using it in transactions
- Verify transaction outputs before broadcasting
- Use appropriate dust thresholds for the target blockchain
- Double-check fee calculations before signing

### Transaction Construction

- Validate all inputs and outputs
- Use appropriate fee rates from reliable sources
- Consider privacy implications of UTXO selection
- Implement proper error handling for selection failures

### Integration Security

- Use this library as part of a comprehensive security architecture
- Combine with proper key management solutions
- Implement transaction signing in secure environments
- Keep all dependencies up to date

## Scope

This security policy applies to the kotlin-utxo library codebase. It does not cover:

- Third-party dependencies (report to respective maintainers)
- Applications built using this library
- Deployment or infrastructure issues
- External fee estimation APIs

## Acknowledgments

We maintain a list of security researchers who have responsibly disclosed vulnerabilities:

- *This list will be updated as contributions are made*

## Contact

For general security questions (not vulnerability reports), you can:

- Open a GitHub Discussion
- Email: security@cbstudio.com

---

Thank you for helping keep kotlin-utxo and its users safe!
