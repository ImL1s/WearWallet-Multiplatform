# Security Policy

This public repo (`ImL1s/WearWallet-Multiplatform`) is the **canonical development
tree**. The private repo (`ImL1s/WearWallet`) is a historical / ops vault and
stays **private forever**. We will **not** rewrite private git history and
push it here. This tree has **no private git ancestry**. **Do not use with
real funds.**

## Supported versions

WearWallet is **experimental**. There is no supported production
release and no guarantee of fitness for storing or transferring real funds.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Report privately via **GitHub Security Advisories** on
[ImL1s/WearWallet-Multiplatform](https://github.com/ImL1s/WearWallet-Multiplatform)
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
