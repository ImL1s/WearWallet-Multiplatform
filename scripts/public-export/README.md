# Public snapshot sync

This directory powers a **one-way sanitized export** from the private canonical
repository to the public snapshot repository.

```
private main  --(scripts/export-public.sh)-->  public main
```

- Canonical history, credentials, and internal ops stay in the private repo.
- The public repo is a clean tree (no private git history).
- External contributions on the public repo are ported back manually.

## Usage

```bash
# Dry-run: build export dir + run secret guards (no push)
./scripts/export-public.sh --dry-run

# Create/update public repo tip (requires gh auth)
./scripts/export-public.sh --push --repo ImL1s/WearWallet-public
```

## Guards (fail the export)

The script refuses to publish if the export tree still contains:
- real `google-services.json` (only `*.example` allowed)
- `*.jks` / `*.keystore` / `*.p12` / private key PEM blocks
- self-hosted runner labels (`ww-ci`, `self-hosted`)
- high-confidence secret signatures (gitleaks on the export tree when installed)
- 1Password vault URI schemes (the `op` + `://` form) or `deploy_to_play_console.py`

## Overlay

Files under `scripts/public-export/overlay/` replace or add public-safe content
after the blacklist is applied.
