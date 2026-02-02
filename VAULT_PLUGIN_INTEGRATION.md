# Vault Plugin Integration with ENV_NAME

## Overview

The Vault plugin now integrates seamlessly with `secrets.core`, supporting automatic environment-aware secret loading using the `ENV_NAME` feature.

## How It Works

### 1. Environment Variables

Set these environment variables to configure the Vault plugin:

```bash
export VAULT_ADDR='http://localhost:8250'
export VAULT_TOKEN='hvs.YOUR_VAULT_TOKEN_HERE'
export VAULT_PATH='jetlag/all'        # Base path in Vault
export ENV_NAME='demo'                 # Environment name (optional)
```

### 2. Register the Plugin

```clojure
(require '[secrets.core :as secrets])
(require '[secrets.plugins.vault :as vault])

;; Register Vault plugin
(secrets/register-plugin! (vault/make-plugin))
```

### 3. Use Secrets

```clojure
;; Works exactly like file-based secrets!
(secrets/get-secret :email)
;; => {:smtp {...} :imap {...} :defaults {...}}

(secrets/get-secret [:email :smtp :host])
;; => "smtp.gmail.com"

(secrets/get-secret [:plane :api-key])
;; => "plane_api_ecc58a1e1a7849fbacdc85415f086aea"
```

## Environment-Aware Path Resolution

When `ENV_NAME` is set, the Vault plugin automatically prefixes paths:

```bash
# Without ENV_NAME
VAULT_PATH='jetlag/all'
# Reads from: kv/jetlag/all

# With ENV_NAME=demo
ENV_NAME='demo'
VAULT_PATH='jetlag/all'
# Reads from: kv/demo/jetlag/all

# With ENV_NAME=production
ENV_NAME='production'
VAULT_PATH='jetlag/all'
# Reads from: kv/production/jetlag/all
```

## Complete Example

### Step 1: Upload Secrets to Vault

```bash
export VAULT_ADDR='http://localhost:8250'
export VAULT_TOKEN='hvs.YOUR_VAULT_TOKEN_HERE'
export ENV_NAME='demo'

# Upload entire secrets map to a single path
clojure -M -m secrets.examples.vault-integration-demo
```

This uploads to: `kv/demo/jetlag/all`

### Step 2: Use in Your Application

```clojure
(ns myapp.core
  (:require [secrets.core :as secrets]
            [secrets.plugins.vault :as vault]))

;; Register Vault plugin (do this once at startup)
(secrets/register-plugin! (vault/make-plugin))

;; Use secrets anywhere in your app
(defn send-email [to subject body]
  (let [smtp-config (secrets/get-secret [:email :smtp])]
    ;; smtp-config => {:host "smtp.gmail.com" :port 587 ...}
    (send-via-smtp smtp-config to subject body)))

(defn call-plane-api []
  (let [api-key (secrets/get-secret [:plane :api-key])
        base-url (secrets/get-secret [:plane :base-url])]
    (http/get (str base-url "/api/...") 
              {:headers {"Authorization" (str "Bearer " api-key)}})))
```

### Step 3: Run with Different Environments

```bash
# Development
export ENV_NAME='dev'
lein run

# Staging
export ENV_NAME='staging'
lein run

# Production
export ENV_NAME='production'
lein run
```

Each environment automatically reads from its own Vault path!

## Plugin Priority

Plugins are checked in registration order. The **first** plugin that returns a non-nil value wins.

```clojure
;; Default: files plugin is registered first
(secrets/list-plugins)
;; => [{:name :files ...}]

;; After registering Vault
(secrets/register-plugin! (vault/make-plugin))
(secrets/list-plugins)
;; => [{:name :files ...} {:name :vault ...}]

;; Files plugin is checked first, then Vault
(secrets/get-secret :email)
;; If found in files => returns from files
;; If not in files => checks Vault
```

### Vault-Only Mode

To use **only** Vault (skip files):

```clojure
;; Unregister files plugin
(secrets/unregister-plugin! :files)

;; Register only Vault
(secrets/register-plugin! (vault/make-plugin))

;; Now all secrets come from Vault
(secrets/get-secret :email)
;; Always reads from Vault
```

## Configuration Summary

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `VAULT_ADDR` | ✅ Yes | - | Vault server address |
| `VAULT_TOKEN` | ✅ Yes | - | Authentication token |
| `VAULT_MOUNT` | No | `kv` | KV secrets engine mount |
| `VAULT_PATH` | No | `secrets` | Base path for secrets |
| `ENV_NAME` | No | - | Environment name for path prefixing |

## Benefits

1. **✅ Same API**: Use `secrets/get-secret` for both files and Vault
2. **✅ Environment-Aware**: Automatic path prefixing with `ENV_NAME`
3. **✅ Flexible**: Mix file-based and Vault-based secrets
4. **✅ No Code Changes**: Switch between environments with env vars only
5. **✅ Backward Compatible**: Existing file-based code works unchanged

## Demo Scripts

### vault-integration-demo.clj
Uploads secrets and demonstrates full integration:
```bash
clojure -M -m secrets.examples.vault-integration-demo
```

### vault-auto-demo.clj
Shows automatic ENV_NAME integration:
```bash
export VAULT_ADDR='http://localhost:8250'
export VAULT_TOKEN='hvs.xxx'
export VAULT_PATH='jetlag/all'
export ENV_NAME='demo'

clojure -M -m secrets.examples.vault-auto-demo
```

## Jetlag Example

For the jetlag project:

```bash
# Set environment
export VAULT_ADDR='http://localhost:8250'
export VAULT_TOKEN='hvs.YOUR_VAULT_TOKEN_HERE'
export VAULT_PATH='jetlag/all'
export ENV_NAME='demo'

# In your code (one-time setup)
(secrets/register-plugin! (vault/make-plugin))

# Use secrets
(secrets/get-secret [:email :smtp])
(secrets/get-secret [:plane :api-key])
```

Secrets are automatically read from: `kv/demo/jetlag/all`

## Troubleshooting

### Secrets not found

Check the Vault path:
```clojure
(require '[secrets.plugins.vault :as vault])

(let [config (vault/vault-config)]
  (println "Reading from:" 
           (str (System/getenv "VAULT_MOUNT") "/"
                (when-let [env (System/getenv "ENV_NAME")]
                  (str env "/"))
                (System/getenv "VAULT_PATH"))))
```

### Check what's in Vault

```clojure
(vault/read-secret 
  {:addr "http://localhost:8250" 
   :token "hvs.xxx"}
  "kv"
  "demo/jetlag/all")
```

### Verify plugin is registered

```clojure
(secrets/list-plugins)
;; Should include {:name :vault ...}
```

## Next Steps

1. ✅ Upload your secrets to Vault
2. ✅ Set environment variables
3. ✅ Register the Vault plugin in your app
4. ✅ Use `secrets/get-secret` as usual
5. ✅ Deploy with different `ENV_NAME` for each environment
