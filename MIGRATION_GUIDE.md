# Migrating Secrets to Vault

This guide shows how to migrate your file-based secrets to HashiCorp Vault.

## Prerequisites

1. **Vault running** and accessible
2. **Environment variables set**:
   ```bash
   export VAULT_ADDR='http://127.0.0.1:8250'
   export VAULT_TOKEN='your-root-token'
   ```

## Quick Start

### 1. Dry Run (Preview)

See what will be uploaded without making changes:

```bash
clojure -M -m secrets.examples.migrate-to-vault --dry-run
```

Example output:
```
=== Secrets Migration to Vault ===

1. Loading secrets from files and environment...
   ✓ Loaded 8 secret(s)

2. Organizing secrets by category...
   ✓ Organized into 2 group(s)

3. Vault Configuration:
   Address: http://127.0.0.1:8250
   Token configured: true
   Mount point: kv
   Base path: pyjama
   Mode: DRY RUN (no changes will be made)

4. Uploading secrets to Vault...

📦 API-KEYS
   Path: kv/pyjama/api-keys
   Secrets: 7
     • brave-api-key : BSA2iECsTyE1DSMmhgCZt...
     • open-ai-key : sk-proj-QZCl8iqGPeky...
     • deepseek-api-key : sk-ee68a1dd56f04a6c...
     • claude-api-key : sk-ant-api03-wz7gxMa...
     • google-api-key : AIzaSyDOUh-x2rxhm8dI_...
     • llama-cloud-api-key : llx-r5xe5Y3xOm4MJ5y2...
     • openrouter-api-key : 
   ⚠️  DRY RUN - Would upload to Vault

=== Migration Complete ===

This was a DRY RUN. Run without --dry-run to actually upload secrets.
```

### 2. Actual Migration

Once you're happy with the preview, run the actual migration:

```bash
clojure -M -m secrets.examples.migrate-to-vault
```

## Command Line Options

```bash
--mount <name>     Vault mount point (default: kv)
--path <path>      Base Vault path (default: pyjama)
--dry-run          Preview without uploading
--force            Overwrite existing secrets
```

### Examples

**Migrate to custom path:**
```bash
clojure -M -m secrets.examples.migrate-to-vault --path myapp
```

**Use different mount point:**
```bash
clojure -M -m secrets.examples.migrate-to-vault --mount secret
```

**Force overwrite existing secrets:**
```bash
clojure -M -m secrets.examples.migrate-to-vault --force
```

**Combine options:**
```bash
clojure -M -m secrets.examples.migrate-to-vault \
  --mount secret \
  --path production/myapp \
  --dry-run
```

## How It Works

### 1. Loads Secrets

The script uses the files plugin to load secrets from:
- `~/secrets.edn`
- `~/secrets.edn.enc`
- `./secrets.edn`
- `./secrets.edn.enc`
- Environment variables (`SECRET__*`)

### 2. Organizes by Category

Automatically groups secrets into categories:

- **api-keys**: Anything with "api", "key", or "token" in the name
- **database**: Anything with "database", "db", "postgres", "mysql", etc.
- **config**: Everything else

### 3. Uploads to Vault

Creates organized structure in Vault:
```
kv/pyjama/api-keys
kv/pyjama/database
kv/pyjama/config
```

### 4. Flattens Nested Secrets

Nested secrets get flattened with hyphenated keys:
```clojure
;; Input (from files)
{:openai {:api-key "sk-..."
          :model "gpt-4"}}

;; Output (in Vault)
{:openai-api-key "sk-..."
 :openai-model "gpt-4"}
```

## After Migration

### Verify Secrets in Vault

```bash
# Via CLI
vault kv get kv/pyjama/api-keys
vault kv get kv/pyjama/database
vault kv get kv/pyjama/config

# Via code
clojure -M -m secrets.examples.vault-simple-test
```

### Use Vault Secrets in Pyjama

```clojure
(require '[secrets.plugins.vault :as vault])

(def config (vault/vault-config))

;; Get all pyjama secrets
(vault/vault->secrets-map config "kv" "pyjama")

;; Or register Vault as a plugin in secrets.core
(require '[secrets.core :as secrets])

(secrets/register-plugin!
  {:name :vault
   :description "HashiCorp Vault"
   :get-fn (fn [k]
             (let [all (vault/vault->secrets-map config "kv" "pyjama")]
               (get all k)))
   :reload-fn (fn [] (vault/vault->secrets-map config "kv" "pyjama"))})

;; Now secrets.core will check Vault
(secrets/get-secret :brave-api-key)
```

## Troubleshooting

### "Vault address is required"
Set environment variables:
```bash
export VAULT_ADDR='http://127.0.0.1:8250'
export VAULT_TOKEN='your-token'
```

### "Failed to write secret, status 404"
Your Vault mount might be different. Check with:
```bash
vault secrets list
```
Then use `--mount` option with the correct name.

### Secrets already exist
Use `--force` to overwrite:
```bash
clojure -M -m secrets.examples.migrate-to-vault --force
```

## Safety Features

- **Dry run by default recommended**: Always run with `--dry-run` first
- **Existing secret protection**: Won't overwrite unless `--force` is used  
- **Detailed logging**: See exactly what's being uploaded
- **Error handling**: Continues on errors, reports failures

## Next Steps

After migration, you can:
1. Keep using file-based secrets as primary source
2. Switch to Vault as primary source
3. Use both with the plugin system (Vault takes priority)
