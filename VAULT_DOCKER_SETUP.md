# Vault Docker Setup with Persistent Storage

This directory contains scripts for running HashiCorp Vault in Docker with persistent storage.

## Quick Start

### 1. Start Vault

```bash
./start-vault.sh
```

This will:
- Create a Docker volume named `vault-data` for persistent storage
- Start Vault on port 8250
- Enable the Vault UI at http://localhost:8250/ui

### 2. Initialize and Setup Vault (First Time Only)

```bash
# In a new terminal
./vault-helper.sh setup
```

This will:
- Initialize Vault
- Save unseal keys and root token to `.vault-keys` (gitignored)
- Unseal Vault
- Login with the root token

**⚠️ IMPORTANT**: The `.vault-keys` file contains sensitive information. Keep it safe!

### 3. After Restart

When you restart the Vault container, you need to unseal it:

```bash
./vault-helper.sh unseal
```

## Scripts

### start-vault.sh

Starts Vault in Docker with persistent storage.

**Usage:**
```bash
./start-vault.sh          # Start Vault
./start-vault.sh clean    # Remove volume and start fresh
```

**Features:**
- Persistent storage via Docker volume (`vault-data`)
- File-based storage backend
- UI enabled on port 8250
- TLS disabled for local development
- Named container (`vault-dev`) for easy management

### vault-helper.sh

Helper script for common Vault operations.

**Commands:**

```bash
./vault-helper.sh init      # Initialize Vault (first time only)
./vault-helper.sh unseal    # Unseal Vault using saved keys
./vault-helper.sh login     # Login to Vault using saved root token
./vault-helper.sh status    # Check Vault status
./vault-helper.sh setup     # Complete setup (init + unseal + login)
./vault-helper.sh env       # Show environment variables to export
./vault-helper.sh logs      # Show Vault container logs
./vault-helper.sh stop      # Stop Vault container
./vault-helper.sh restart   # Restart Vault container
```

## Common Workflows

### First Time Setup

```bash
# 1. Start Vault
./start-vault.sh

# 2. In a new terminal, complete setup
./vault-helper.sh setup

# 3. Export environment variables
eval "$(./vault-helper.sh env)"

# 4. Enable KV secrets engine
vault secrets enable -path=secret kv-v2

# 5. Test it
vault kv put secret/test hello=world
vault kv get secret/test
```

### Daily Usage

```bash
# Start Vault (if not running)
./start-vault.sh

# Unseal (required after each restart)
./vault-helper.sh unseal

# Login
./vault-helper.sh login

# Or use environment variables
eval "$(./vault-helper.sh env)"
```

### Using with Environment-Based Secrets

When using with the secrets library's environment-based feature:

```bash
# 1. Set environment
export ENV_NAME=staging

# 2. Write secrets to environment-specific path
vault kv put secret/staging/pyjama/database \
  username=admin \
  password=secret123

# 3. The secrets library will automatically read from staging path
# when ENV_NAME=staging
```

### Stopping and Restarting

```bash
# Stop Vault
./vault-helper.sh stop

# Restart Vault
./vault-helper.sh restart

# After restart, unseal is required
./vault-helper.sh unseal
```

### Starting Fresh

```bash
# Stop and remove all data
./start-vault.sh clean

# Start fresh
./start-vault.sh

# Setup again
./vault-helper.sh setup
```

## Persistence

### What is Persisted

- All secrets stored in Vault
- Vault initialization state
- Audit logs
- Vault configuration

### What is NOT Persisted

- Unseal keys and root token (stored in `.vault-keys` file)
- Vault seal state (must unseal after each restart)

### Docker Volume

The persistent data is stored in a Docker volume named `vault-data`.

**Inspect the volume:**
```bash
docker volume inspect vault-data
```

**Backup the volume:**
```bash
docker run --rm -v vault-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/vault-backup.tar.gz -C /data .
```

**Restore from backup:**
```bash
docker run --rm -v vault-data:/data -v $(pwd):/backup \
  alpine tar xzf /backup/vault-backup.tar.gz -C /data
```

## Troubleshooting

### Vault is sealed

```bash
./vault-helper.sh unseal
```

### Lost unseal keys

If you lost the `.vault-keys` file, you'll need to start fresh:

```bash
./start-vault.sh clean
./start-vault.sh
./vault-helper.sh setup
```

### Container won't start

Check if port 8250 is already in use:

```bash
lsof -i :8250
```

### Check logs

```bash
./vault-helper.sh logs
```

## Security Considerations

1. **Never commit `.vault-keys`**: This file is gitignored by default
2. **Protect the Docker volume**: Contains all your secrets
3. **Use TLS in production**: This setup disables TLS for local development
4. **Rotate tokens regularly**: Use Vault's token lifecycle features
5. **Backup regularly**: Export and securely store backups of the Docker volume

## Integration with Secrets Library

The secrets library automatically integrates with Vault when configured:

```clojure
(require '[secrets.plugins.vault :as vault])

;; Configure Vault
(def config (vault/vault-config))
;; Uses VAULT_ADDR and VAULT_TOKEN from environment

;; Read secret
(vault/read-secret config "secret" "myapp/database")

;; With ENV_NAME=staging, automatically reads from:
;; secret/data/staging/myapp/database
```

See [ENV_BASED_SECRETS.md](ENV_BASED_SECRETS.md) for more details on environment-based secrets.

## References

- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [Vault Docker Image](https://hub.docker.com/_/vault)
- [Vault KV Secrets Engine](https://www.vaultproject.io/docs/secrets/kv)
