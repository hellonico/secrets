#!/bin/bash
# Helper script for common Vault operations
#
# This script provides shortcuts for common Vault operations
# when using the Docker-based Vault server.

set -e

VAULT_ADDR="http://localhost:8250"
export VAULT_ADDR

UNSEAL_KEYS_FILE=".vault-keys"
CONTAINER_NAME="vault-dev"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_usage() {
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  init          Initialize Vault (first time only)"
    echo "  unseal        Unseal Vault using saved keys"
    echo "  login         Login to Vault using saved root token"
    echo "  status        Check Vault status"
    echo "  setup         Complete setup (init + unseal + login)"
    echo "  env           Show environment variables to export"
    echo "  logs          Show Vault container logs"
    echo "  stop          Stop Vault container"
    echo "  restart       Restart Vault container"
    echo ""
    echo "Examples:"
    echo "  $0 setup      # First time setup"
    echo "  $0 unseal     # Unseal after restart"
    echo "  $0 status     # Check if Vault is sealed/unsealed"
}

check_vault_running() {
    if ! docker ps | grep -q $CONTAINER_NAME; then
        echo -e "${RED}✗ Vault container is not running${NC}"
        echo "  Start it with: ./start-vault.sh"
        exit 1
    fi
}

init_vault() {
    echo -e "${BLUE}🔐 Initializing Vault...${NC}"
    
    if [ -f "$UNSEAL_KEYS_FILE" ]; then
        echo -e "${YELLOW}⚠  Vault keys file already exists: $UNSEAL_KEYS_FILE${NC}"
        read -p "Do you want to re-initialize? This will overwrite existing keys! (yes/no): " confirm
        if [ "$confirm" != "yes" ]; then
            echo "Aborted."
            exit 0
        fi
    fi
    
    vault operator init -key-shares=5 -key-threshold=3 > "$UNSEAL_KEYS_FILE"
    chmod 600 "$UNSEAL_KEYS_FILE"
    
    echo -e "${GREEN}✓ Vault initialized${NC}"
    echo -e "${YELLOW}⚠  IMPORTANT: Keys saved to $UNSEAL_KEYS_FILE${NC}"
    echo -e "${YELLOW}   Keep this file safe and secure!${NC}"
    echo ""
    cat "$UNSEAL_KEYS_FILE"
}

unseal_vault() {
    echo -e "${BLUE}🔓 Unsealing Vault...${NC}"
    
    if [ ! -f "$UNSEAL_KEYS_FILE" ]; then
        echo -e "${RED}✗ Keys file not found: $UNSEAL_KEYS_FILE${NC}"
        echo "  Run: $0 init"
        exit 1
    fi
    
    # Extract first 3 unseal keys
    key1=$(grep "Unseal Key 1:" "$UNSEAL_KEYS_FILE" | awk '{print $NF}')
    key2=$(grep "Unseal Key 2:" "$UNSEAL_KEYS_FILE" | awk '{print $NF}')
    key3=$(grep "Unseal Key 3:" "$UNSEAL_KEYS_FILE" | awk '{print $NF}')
    
    vault operator unseal "$key1" > /dev/null
    echo "  Key 1/3 applied"
    vault operator unseal "$key2" > /dev/null
    echo "  Key 2/3 applied"
    vault operator unseal "$key3" > /dev/null
    echo "  Key 3/3 applied"
    
    echo -e "${GREEN}✓ Vault unsealed${NC}"
}

login_vault() {
    echo -e "${BLUE}🔑 Logging in to Vault...${NC}"
    
    if [ ! -f "$UNSEAL_KEYS_FILE" ]; then
        echo -e "${RED}✗ Keys file not found: $UNSEAL_KEYS_FILE${NC}"
        echo "  Run: $0 init"
        exit 1
    fi
    
    root_token=$(grep "Initial Root Token:" "$UNSEAL_KEYS_FILE" | awk '{print $NF}')
    vault login "$root_token" > /dev/null
    
    echo -e "${GREEN}✓ Logged in${NC}"
    echo "  Token: $root_token"
}

show_status() {
    echo -e "${BLUE}📊 Vault Status${NC}"
    echo ""
    vault status || true
}

show_env() {
    echo -e "${BLUE}🌍 Environment Variables${NC}"
    echo ""
    echo "export VAULT_ADDR='$VAULT_ADDR'"
    
    if [ -f "$UNSEAL_KEYS_FILE" ]; then
        root_token=$(grep "Initial Root Token:" "$UNSEAL_KEYS_FILE" | awk '{print $NF}')
        echo "export VAULT_TOKEN='$root_token'"
    fi
    
    echo ""
    echo "To use in your shell:"
    echo "  eval \"\$($0 env)\""
}

show_logs() {
    echo -e "${BLUE}📜 Vault Logs${NC}"
    echo ""
    docker logs -f $CONTAINER_NAME
}

stop_vault() {
    echo -e "${BLUE}🛑 Stopping Vault...${NC}"
    docker stop $CONTAINER_NAME
    echo -e "${GREEN}✓ Vault stopped${NC}"
}

restart_vault() {
    echo -e "${BLUE}🔄 Restarting Vault...${NC}"
    docker restart $CONTAINER_NAME
    echo -e "${GREEN}✓ Vault restarted${NC}"
    echo ""
    echo "Vault needs to be unsealed after restart:"
    echo "  $0 unseal"
}

setup_vault() {
    echo -e "${BLUE}🚀 Complete Vault Setup${NC}"
    echo ""
    
    init_vault
    echo ""
    sleep 2
    
    unseal_vault
    echo ""
    sleep 1
    
    login_vault
    echo ""
    
    echo -e "${GREEN}✓ Setup complete!${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Export environment variables:"
    echo "     eval \"\$($0 env)\""
    echo ""
    echo "  2. Enable KV secrets engine:"
    echo "     vault secrets enable -path=secret kv-v2"
    echo ""
    echo "  3. Write a test secret:"
    echo "     vault kv put secret/test hello=world"
}

# Main command dispatcher
case "${1:-}" in
    init)
        check_vault_running
        init_vault
        ;;
    unseal)
        check_vault_running
        unseal_vault
        ;;
    login)
        check_vault_running
        login_vault
        ;;
    status)
        check_vault_running
        show_status
        ;;
    setup)
        check_vault_running
        setup_vault
        ;;
    env)
        show_env
        ;;
    logs)
        check_vault_running
        show_logs
        ;;
    stop)
        stop_vault
        ;;
    restart)
        restart_vault
        ;;
    *)
        print_usage
        exit 1
        ;;
esac
