# Security Policy

## Supported versions

WearWallet is **experimental**. There is no supported production
release and no guarantee of fitness for storing or transferring real funds.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Report privately via GitHub Security Advisories on this repository
(Security → Advisories → New draft security advisory), or email
`security@cbstudio.dev` if advisories are unavailable.

Include:
- affected commit SHA / tag
- description and impact
- reproduction steps or proof-of-concept (no mainnet keys or funds)

We will acknowledge receipt when possible. There is no formal SLA while the
project remains experimental.

## Safe disclosure expectations

- Never commit mnemonics, private keys, keystores, or production API credentials.
- Use `.env.example` / `gradle.properties.example` and local untracked files only.
- Fork pull requests must not expect access to private package credentials beyond
  the default `GITHUB_TOKEN` permissions of this public repository.
