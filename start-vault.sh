#!/bin/bash
# Start HashiCorp Vault in Docker with persistent storage
#
# This script starts Vault with:
# - Persistent storage via Docker volume (vault-data)
# - File-based storage backend
# - UI enabled on port 8250
# - TLS disabled for local development
#
# Usage:
#   ./start-vault.sh          # Start Vault
#   ./start-vault.sh clean    # Remove volume and start fresh

set -e

VAULT_VOLUME="vault-data"
VAULT_PORT="8250"
CONTAINER_NAME="vault-dev"

# Check if we should clean the volume
if [ "$1" = "clean" ]; then
    echo "🧹 Cleaning up existing Vault data..."
    docker rm -f $CONTAINER_NAME 2>/dev/null || true
    docker volume rm $VAULT_VOLUME 2>/dev/null || true
    echo "✓ Cleaned up"
fi

# Create volume if it doesn't exist
if ! docker volume inspect $VAULT_VOLUME >/dev/null 2>&1; then
    echo "📦 Creating Docker volume: $VAULT_VOLUME"
    docker volume create $VAULT_VOLUME
fi

# Stop existing container if running
docker rm -f $CONTAINER_NAME 2>/dev/null || true

echo "🚀 Starting Vault with persistent storage..."
echo "   Volume: $VAULT_VOLUME"
echo "   Port: $VAULT_PORT"
echo "   UI: http://localhost:$VAULT_PORT/ui"
echo ""

docker run \
    --name $CONTAINER_NAME \
    --cap-add=IPC_LOCK \
    -v $VAULT_VOLUME:/vault/file \
    -e 'VAULT_LOCAL_CONFIG={
        "storage": {
            "file": {
                "path": "/vault/file"
            }
        },
        "listener": [{
            "tcp": {
                "address": "0.0.0.0:8250",
                "tls_disable": true
            }
        }],
        "default_lease_ttl": "168h",
        "max_lease_ttl": "720h",
        "ui": true
    }' \
    -p $VAULT_PORT:8250 \
    hashicorp/vault server

# Note: After first run, you'll need to initialize and unseal Vault:
#
# 1. Initialize (first time only):
#    export VAULT_ADDR='http://localhost:8250'
#    vault operator init
#    # Save the unseal keys and root token!
#
# 2. Unseal (after each restart):
#    vault operator unseal <key1>
#    vault operator unseal <key2>
#    vault operator unseal <key3>
#
# 3. Login:
#    vault login <root-token>
