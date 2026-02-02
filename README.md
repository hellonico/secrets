# Secrets Library

A comprehensive secrets management library for Clojure with support for multiple sources and encryption.

## Features

### Core Features
- **Plugin Architecture**: Extensible plugin system for multiple secret sources
- **Priority-Based Lookup**: Configure lookup order across plugins
- **Deep Merging**: Intelligently merge secrets from different sources
- **Encryption**: AES-GCM encryption with PBKDF2 key derivation
- **Flexible Lookup**: Support for both flat and nested secret paths

### Built-in Plugins

#### Files Plugin (Default)
- Load from `secrets.edn` (plain) and `secrets.edn.enc` (encrypted)
- Environment variables support (`SECRET__*` prefix)
- Priority-based source merging

#### Vault Plugin
- HashiCorp Vault integration for enterprise secret management
- Full KV v2 secrets engine support
- Vault namespace support (Enterprise)
- Read, write, and list operations
- See [VAULT_PLUGIN.md](VAULT_PLUGIN.md) for details

## Installation

Add to your `deps.edn`:

```clojure
{:deps {secrets {:local/root "/path/to/secrets"}}}
```

## Quick Start

### Basic Usage

```clojure
(require '[secrets.core :as secrets])

;; Get a secret (searches all sources)
(secrets/get-secret :api-key)
;; => "your-api-key"

;; Get nested secret
(secrets/get-secret [:openai :api-key])
;; => "sk-..."

;; Get all loaded secrets
(secrets/all-secrets)
;; => {:api-key "..." :openai {:api-key "sk-..."}}

;; Reload secrets from all sources
(secrets/reload-secrets!)
```

### Secret Sources (Priority Order)

Secrets are loaded from multiple sources with the following priority (rightmost wins):

1. **Home plain**: `~/secrets.edn`
2. **Home encrypted**: `~/secrets.edn.enc`
3. **Local plain**: `./secrets.edn`
4. **Local encrypted**: `./secrets.edn.enc`
5. **Environment variables**: `SECRET__*` prefixed (highest priority)

Example environment variable:
```bash
export SECRET__OPENAI__API_KEY="sk-..."
# Maps to [:openai :api-key]
```

### Encrypted Secrets

```clojure
(require '[secrets.core :as secrets])

;; Set passphrase
;; export SECRETS_PASSPHRASE="your-secure-passphrase"

;; Write encrypted secrets
(secrets/write-encrypted-secrets! 
  "~/secrets.edn.enc"
  {:api-key "secret-value"
   :database {:password "db-pass"}})
```

### Vault Plugin

```clojure
(require '[secrets.plugins.vault :as vault])

;; Configure Vault (or use VAULT_ADDR and VAULT_TOKEN env vars)
(def config (vault/vault-config))

;; Read secret from Vault
(vault/read-secret config "secret" "myapp/database")
;; => {:username "admin" :password "secret123"}

;; Write secret to Vault
(vault/write-secret! config "secret" "myapp/api-keys"
                     {:openai "sk-..." :anthropic "sk-ant-..."})

;; List secrets
(vault/list-secrets config "secret" "myapp")
;; => ["database" "api-keys"]

;; Convert Vault secrets to map
(vault/vault->secrets-map config "secret" "myapp")
;; => {:database-username "admin" :database-password "secret123" ...}
```

See [VAULT_PLUGIN.md](VAULT_PLUGIN.md) for complete Vault plugin documentation.

## File Formats

### Plain EDN (`secrets.edn`)

```clojure
{:api-key "your-api-key"
 :database {:host "localhost"
            :port 5432
            :password "db-pass"}
 :openai {:api-key "sk-..."}}
```

### Encrypted EDN (`secrets.edn.enc`)

Binary file encrypted with AES-GCM. Use `write-encrypted-secrets!` to create.

## Testing

```bash
# Run all tests
clojure -M:test

# Run specific test namespace
clojure -M:test -n secrets.core-test
clojure -M:test -n secrets.plugins.vault-test
```

## Examples

See the `src/secrets/examples/` directory for examples:

- `vault_demo.clj`: Comprehensive Vault plugin demonstration

Run examples:
```bash
clojure -M -m secrets.examples.vault-demo
```

## Security Considerations

1. **Never commit secrets files**: Add `secrets.edn` and `secrets.edn.enc` to `.gitignore`
2. **Use encrypted files**: For sensitive production secrets, use encrypted files
3. **Protect passphrases**: Store `SECRETS_PASSPHRASE` securely (e.g., in password manager)
4. **Environment isolation**: Use different secrets per environment
5. **Vault for production**: Consider using the Vault plugin for production environments

## API Reference

### Core Functions

- `(get-secret k-or-path)`: Retrieve a secret by key or path vector
- `(all-secrets)`: Get all loaded secrets as a map
- `(reload-secrets!)`: Reload all secrets from sources
- `(write-encrypted-secrets! path m)`: Write encrypted secrets to file

### Vault Plugin Functions

- `(vault-config)`: Get Vault configuration from environment
- `(read-secret config mount path)`: Read secret from Vault
- `(write-secret! config mount path data)`: Write secret to Vault
- `(list-secrets config mount path)`: List secrets at path
- `(vault->secrets-map config mount path)`: Convert Vault secrets to map

## Development

```bash
# Start REPL
clojure

# Run tests
clojure -M:test

# Start Vault dev server (for Vault plugin testing)
vault server -dev
```

## License

Copyright © 2026

## Documentation

- [PLUGIN_ARCHITECTURE.md](PLUGIN_ARCHITECTURE.md) - Plugin system architecture and custom plugin guide
- [VAULT_PLUGIN.md](VAULT_PLUGIN.md) - Vault plugin documentation
- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - Implementation details
