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

### Environment-Based Secrets

The library supports environment-specific secret management via the `ENV_NAME` environment variable. When set, the library automatically:

1. **Loads environment-specific files** (e.g., `secrets.staging.edn`)
2. **Uses environment-specific Vault paths** (e.g., `pyjama/staging`)
3. **Checks environment-specific env variables** (e.g., `SECRET_STAGING__*`)

#### Setting Up Environments

```bash
# Set the environment name
export ENV_NAME=staging

# Now the library will look for:
# - Files: secrets.staging.edn, secrets.staging.edn.enc
# - Vault: pyjama/staging/* (instead of pyjama/*)
# - Env vars: SECRET_STAGING__* (in addition to SECRET__*)
```

#### File Priority with Environments

When `ENV_NAME=staging`, the priority order becomes:

1. `~/secrets.edn` (home default)
2. `~/secrets.edn.enc` (home default encrypted)
3. `~/secrets.staging.edn` (home environment-specific)
4. `~/secrets.staging.edn.enc` (home environment-specific encrypted)
5. `./secrets.edn` (local default)
6. `./secrets.edn.enc` (local default encrypted)
7. `./secrets.staging.edn` (local environment-specific)
8. `./secrets.staging.edn.enc` (local environment-specific encrypted)
9. `SECRET__*` environment variables (generic)
10. `SECRET_STAGING__*` environment variables (environment-specific, highest priority)

This allows you to:
- Keep common secrets in `secrets.edn`
- Override with environment-specific values in `secrets.staging.edn`
- Further override with environment variables

#### Vault Paths with Environments

When `ENV_NAME=staging`:

```clojure
;; Without ENV_NAME:
(vault/read-secret config "secret" "pyjama/database")
;; Reads from: secret/data/pyjama/database

;; With ENV_NAME=staging:
(vault/read-secret config "secret" "pyjama/database")
;; Reads from: secret/data/staging/pyjama/database
```

#### Environment Variables with Environments

```bash
# Generic (works in all environments)
export SECRET__API_KEY="default-key"

# Environment-specific (only used when ENV_NAME=staging)
export SECRET_STAGING__API_KEY="staging-key"

# When ENV_NAME=staging, SECRET_STAGING__API_KEY takes precedence
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
- `env_demo.clj`: Environment-based secret management demonstration

Run examples:
```bash
# Vault plugin demo
clojure -M -m secrets.examples.vault-demo

# Environment-based secrets demo
clojure -M:env-demo
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

- [ENV_BASED_SECRETS.md](ENV_BASED_SECRETS.md) - Environment-based secret management guide
- [VAULT_DOCKER_SETUP.md](VAULT_DOCKER_SETUP.md) - Vault Docker setup with persistent storage
- [PLUGIN_ARCHITECTURE.md](PLUGIN_ARCHITECTURE.md) - Plugin system architecture and custom plugin guide
- [VAULT_PLUGIN.md](VAULT_PLUGIN.md) - Vault plugin documentation
- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - Implementation details
