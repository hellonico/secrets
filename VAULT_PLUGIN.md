# Vault Plugin

HashiCorp Vault integration plugin for the secrets library.

## Features

- **Read secrets** from Vault KV v2 secrets engine
- **Write secrets** to Vault KV v2 secrets engine
- **List secrets** at a given path
- **Convert Vault secrets** to secrets map format
- Full support for **Vault namespaces** (Enterprise feature)
- Comprehensive error handling with 404 detection

## Configuration

The Vault plugin requires the following environment variables:

- `VAULT_ADDR`: Vault server address (e.g., `http://localhost:8200`)
- `VAULT_TOKEN`: Authentication token for Vault
- `VAULT_NAMESPACE`: (Optional) Vault namespace (for Vault Enterprise)

Alternatively, you can pass configuration explicitly:

```clojure
(require '[secrets.plugins.vault :as vault])

(def my-config
  {:addr "http://localhost:8200"
   :token "s.1234567890abcdef"
   :namespace "my-namespace"})  ; optional
```

## Usage

### Reading Secrets

```clojure
(require '[secrets.plugins.vault :as vault])

;; Read from environment-configured Vault
(vault/read-secret "secret" "myapp/database")
;; => {:username "admin" :password "secret123"}

;; Read with explicit configuration
(vault/read-secret my-config "secret" "myapp/database")
;; => {:username "admin" :password "secret123"}
```

### Writing Secrets

```clojure
;; Write a secret
(vault/write-secret! "secret" "myapp/database"
                     {:username "admin"
                      :password "secret123"})
;; => :ok

;; Write with explicit configuration
(vault/write-secret! my-config "secret" "myapp/api-keys"
                     {:openai "sk-..."
                      :anthropic "sk-ant-..."})
;; => :ok
```

### Listing Secrets

```clojure
;; List all secrets under a path
(vault/list-secrets "secret" "myapp")
;; => ["database" "api-keys" "config"]

;; List secrets at root
(vault/list-secrets "secret" "")
;; => ["myapp" "other-app"]
```

### Converting Vault Secrets to Map

The `vault->secrets-map` function fetches all secrets from a path and converts them to a flat map, which can be merged with other secret sources:

```clojure
(vault/vault->secrets-map "secret" "myapp")
;; => {:database-username "admin"
;;     :database-password "secret123"
;;     :api-keys-openai "sk-..."
;;     :api-keys-anthropic "sk-ant-..."}

;; With custom key transformation
(vault/vault->secrets-map my-config "secret" "myapp"
                          (fn [k] (keyword (str "vault-" (name k)))))
;; => {:vault-database-username "admin"
;;     :vault-database-password "secret123"
;;     ...}
```

## Integration with Secrets Core

The Vault plugin can be integrated with the main secrets library to add Vault as a secret source:

```clojure
(require '[secrets.core :as secrets]
         '[secrets.plugins.vault :as vault])

;; Load secrets from Vault and merge with other sources
(defn load-all-sources-with-vault []
  (let [vault-secrets (vault/vault->secrets-map "secret" "myapp")
        file-secrets (secrets/all-secrets)]
    (merge file-secrets vault-secrets)))

;; Or extend the secrets.core namespace
(in-ns 'secrets.core)

(defn- load-all-sources []
  (let [local-plain (load-edn-file "secrets.edn")
        local-enc (maybe-read-encrypted "secrets.edn.enc")
        home-plain (load-edn-file (home "secrets.edn"))
        home-enc (maybe-read-encrypted (home "secrets.edn.enc"))
        env-map (env->secrets-map)
        vault-secrets (when (System/getenv "VAULT_ADDR")
                        (vault/vault->secrets-map "secret" "myapp"))]
    ;; Priority: home → local → env → vault (vault wins overall)
    (deep-merge home-plain home-enc local-plain local-enc env-map vault-secrets)))
```

## Error Handling

The plugin provides clear error messages for common scenarios:

```clojure
;; Missing configuration
(vault/read-secret {} "secret" "path")
;; => ExceptionInfo: Vault address is required (VAULT_ADDR)

;; Secret not found
(vault/read-secret config "secret" "non/existent")
;; => nil

;; Server error
(vault/read-secret config "secret" "path")
;; => ExceptionInfo: Vault request failed with status 500
```

## Testing

Run the Vault plugin tests:

```bash
clojure -M:test -n secrets.plugins.vault-test
```

For integration testing against a real Vault server, ensure `VAULT_ADDR` and `VAULT_TOKEN` are set:

```bash
# Start Vault dev server
vault server -dev

# In another terminal
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='...'  # From the dev server output

# Run tests (including integration tests)
clojure -M:test -n secrets.plugins.vault-test
```

## KV v2 Engine

This plugin is designed for Vault's KV v2 secrets engine. The KV v2 engine provides:

- Versioning of secrets
- Check-and-Set operations
- Metadata tracking

The plugin uses the following API endpoints:

- **Read**: `GET /v1/{mount}/data/{path}`
- **Write**: `POST /v1/{mount}/data/{path}`
- **List**: `GET /v1/{mount}/metadata/{path}?list=true`

For more information, see the [Vault KV v2 documentation](https://www.vaultproject.io/docs/secrets/kv/kv-v2).

## Dependencies

The Vault plugin requires:

- `org.clojure/data.json` for JSON parsing and serialization
- Java 11+ for HTTP client utilities

These are automatically included via `deps.edn`.
