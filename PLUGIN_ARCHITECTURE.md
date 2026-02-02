# Plugin Architecture Refactoring Summary

## Overview

The secrets library has been refactored to use a plugin-based architecture, making it more extensible and modular.

## Changes Made

### 1. New Plugin System (`secrets.core`)

The core namespace is now a **plugin manager** that:
- Maintains a registry of secret plugins
- Provides unified lookup across all plugins
- Supports priority-based secret resolution
- Manages plugin lifecycle (registration, reload, etc.)

**Key Functions:**
- `register-plugin!` - Register a new secret plugin
- `unregister-plugin!` - Remove a plugin by name
- `list-plugins` - List all registered plugins
- `get-secret` - Get a secret from any plugin (first match wins)
- `get-secret-with-source` - Get secret with source plugin info
- `all-secrets` - Merge secrets from all plugins
- `reload-secrets!` - Reload all plugins that support it

### 2. Files Plugin (`secrets.plugins.files`)

The original file/environment-based implementation is now a plugin (`files`):
- Load from `secrets.edn` (plain)
- Load from `secrets.edn.enc` (encrypted)
- Load from environment variables (`SECRET__*` prefix)
- Deep merging with priority order

**API:**
- `get-secret` - Get a secret
- `all-secrets` - Get all secrets
- `reload!` - Reload from all sources
- `write-encrypted-secrets!` - Write encrypted file
- `deep-merge` - Public utility for deep merging

### 3. Vault Plugin (`secrets.plugins.vault`)

HashiCorp Vault integration (unchanged):
- Read/write/list secrets from Vault KV v2
- Namespace support
- Convert to secrets map

## Plugin Interface

Any plugin must provide:

**Required:**
- `:name` - Unique keyword identifier
- `:get-fn` - Function `(fn [key-or-path] => value-or-nil)`

**Optional:**
- `:description` - Human-readable description
- `:reload-fn` - Function `(fn [] => reloaded-data)`
- `:all-fn` - Function `(fn [] => all-secrets-map)`

## Usage Examples

### Basic Usage (Unchanged)

```clojure
(require '[secrets.core :as secrets])

;; Get a secret (works as before)
(secrets/get-secret :api-key)
;; => "value"

;; Get nested secret
(secrets/get-secret [:openai :api-key])
;; => "sk-..."

;; Reload all plugins
(secrets/reload-secrets!)
;; => {:files {...}}

;; Get all secrets (merged from all plugins)
(secrets/all-secrets)
;; => {:api-key "..." :openai {:api-key "sk-..."} ...}
```

### Working with Plugins

```clojure
(require '[secrets.core :as secrets])

;; List all registered plugins
(secrets/list-plugins)
;; => [{:name :files :description "File and environment-based secrets" ...}]

;; Get a secret with source information
(secrets/get-secret-with-source :api-key)
;; => {:value "..." :source :files}

;; Register a custom plugin
(secrets/register-plugin!
  {:name :custom
   :description "My custom plugin"
   :get-fn (fn [k] (when (= k :custom-key) "custom-value"))})

;; Now get-secret will check custom plugin too
(secrets/get-secret :custom-key)
;; => "custom-value"
```

### Custom Plugin Example

Creating a simple in-memory plugin:

```clojure
(def my-secrets (atom {:db-password "secret123"}))

(secrets/register-plugin!
  {:name :memory
   :description "In-memory secrets store"
   :get-fn (fn [k] (get @my-secrets k))
   :all-fn (fn [] @my-secrets)
   :reload-fn (fn [] 
                (reset! my-secrets {:db-password "reloaded"})
                @my-secrets)})

;; Now secrets will also check the memory store
(secrets/get-secret :db-password)
;; => "secret123"
```

### Plugin Priority

Plugins are checked in registration order. The **first non-nil** result wins:

```clojure
;; Default: files plugin is checked first
(secrets/init-default-plugins!)

;; Register another plugin (checked after files)
(secrets/register-plugin! my-plugin)

;; If both plugins have :api-key, files plugin wins
;; because it was registered first
```

To change priority, re-register in desired order:

```clojure
(secrets/init-default-plugins!)  ; Reset to defaults
(secrets/register-plugin! high-priority-plugin)
(secrets/register-plugin! low-priority-plugin)
```

## Migration Guide

### For Library Users

**No changes required!** The public API remains the same:

```clojure
;; All of these work exactly as before
(secrets/get-secret :key)
(secrets/all-secrets)
(secrets/reload-secrets!)
(secrets/write-encrypted-secrets! "path.enc" {...})
```

### For Advanced Users

If you were relying on internal functions, update your code:

**Before:**
```clojure
(require '[secrets.core :as secrets])

;; These were private functions
(#'secrets/load-all-sources)
(#'secrets/deep-merge a b c)
```

**After:**
```clojure
(require '[secrets.plugins.files :as files])

;; Now in files plugin
(#'files/load-all-sources)
(files/deep-merge a b c)  ; Now public
```

## Test Updates

### Test Organization

- `secrets.core-test` - Plugin manager tests (16 tests)
- `secrets.plugins.files-test` - Files plugin tests (10 tests)
- `secrets.plugins.vault-test` - Vault plugin tests (19 tests)

**Total: 45 tests, 75 assertions** - All passing ✓

### Running Tests

```bash
# All tests
clojure -M:test

# Specific namespace
clojure -M:test -n secrets.core-test
clojure -M:test -n secrets.plugins.files-test
clojure -M:test -n secrets.plugins.vault-test
```

## Benefits of Plugin Architecture

1. **Extensibility**: Easy to add new secret sources (AWS Secrets Manager, Azure Key Vault, etc.)
2. **Modularity**: Each plugin is independent and testable
3. **Flexibility**: Mix and match plugins as needed
4. **Priority Control**: Configure lookup order dynamically
5. **Source Tracking**: Know which plugin provided each secret
6. **Backwards Compatible**: Existing code works without changes

## Future Plugin Ideas

- `secrets.plugins.aws` - AWS Secrets Manager / Parameter Store
- `secrets.plugins.azure` - Azure Key Vault
- `secrets.plugins.gcp` - Google Secret Manager
- `secrets.plugins.database` - Database-backed secrets
- `secrets.plugins.cache` - Caching layer for expensive lookups
- `secrets.plugins.validation` - Secret validation and rotation

## Files Created/Modified

### Created
- `src/secrets/plugins/files.clj` - Files plugin implementation
- `test/secrets/plugins/files_test.clj` - Files plugin tests
- `PLUGIN_ARCHITECTURE.md` - This document

### Modified
- `src/secrets/core.clj` - Now a plugin manager (was monolithic implementation)
- `test/secrets/core_test.clj` - Tests for plugin manager

### Unchanged
- `src/secrets/plugins/vault.clj` - Vault plugin  
- `test/secrets/plugins/vault_test.clj` - Vault tests
- All other files
