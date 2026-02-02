# Vault Upload Scripts Comparison

## Two Different Approaches

The secrets library provides two different scripts for uploading secrets to Vault, each with different use cases:

### 1. migrate-to-vault.clj (Flatten & Categorize)

**Purpose**: Organize flat secrets by category

**What it does**:
- ✅ Flattens nested structures
- ✅ Categorizes by regex patterns (api-keys, database, config)
- ✅ Good for migrating from flat file structures

**Example**:
```clojure
;; Input (secrets.edn)
{:plane {:api-key "xxx"
         :base-url "http://..."}
 :email {:smtp {:host "smtp.gmail.com"
                :port 587}}}

;; Output in Vault
kv/jetlag/api-keys
  :plane-api-key "xxx"

kv/jetlag/config
  :plane-base-url "http://..."
  :email-smtp-host "smtp.gmail.com"
  :email-smtp-port 587
```

**Usage**:
```bash
clojure -M -m secrets.examples.migrate-to-vault --path jetlag
```

---

### 2. upload-to-vault.clj (Preserve Structure) ⭐ NEW

**Purpose**: Upload secrets preserving their original nested structure

**What it does**:
- ✅ Preserves nested structure
- ✅ Each top-level key becomes a separate Vault path
- ✅ No flattening or categorization
- ✅ Maintains the original organization

**Example**:
```clojure
;; Input (secrets.edn)
{:plane {:api-key "xxx"
         :base-url "http://..."}
 :email {:smtp {:host "smtp.gmail.com"
                :port 587}
         :imap {:host "imap.gmail.com"
                :port 993}}}

;; Output in Vault
kv/jetlag/secrets/plane
  :api-key "xxx"
  :base-url "http://..."

kv/jetlag/secrets/email
  :smtp {:host "smtp.gmail.com" :port 587}
  :imap {:host "imap.gmail.com" :port 993}
```

**Usage**:
```bash
clojure -M -m secrets.examples.upload-to-vault <file> <vault-path>

# Example
clojure -M -m secrets.examples.upload-to-vault jetlag/secrets.edn jetlag/secrets
```

---

## Jetlag Secrets - Both Approaches

### Original Structure
```clojure
{:plane
 {:api-key "plane_api_ecc58a1e1a7849fbacdc85415f086aea"
  :base-url "http://plane.karabiner.example.com"
  :workspace-slug "smtb"}

 :email
 {:smtp {:host "smtp.gmail.com"
         :port 587
         :user "mohnicolas02@gmail.com"
         :pass "vogcjdvlodacwjkg"
         :tls true}
  :imap {:host "imap.gmail.com"
         :port 993
         :user "mohnicolas02@gmail.com"
         :pass "vogcjdvlodacwjkg"
         :ssl true}
  :defaults {:from "mohnicolas02@gmail.com"}
  :watcher {:interval-ms 5000
            :folder "INBOX"}}}
```

### Approach 1: migrate-to-vault.clj (Flattened)

**Vault Paths**:
- `kv/jetlag/api-keys`
- `kv/jetlag/config`

**Result**:
```clojure
;; kv/jetlag/api-keys
{:plane-api-key "plane_api_ecc58a1e1a7849fbacdc85415f086aea"}

;; kv/jetlag/config
{:email-smtp-user "mohnicolas02@gmail.com"
 :email-smtp-host "smtp.gmail.com"
 :email-smtp-port 587
 :email-smtp-pass "vogcjdvlodacwjkg"
 :email-smtp-tls true
 :email-imap-user "mohnicolas02@gmail.com"
 :email-imap-host "imap.gmail.com"
 :email-imap-port 993
 :email-imap-pass "vogcjdvlodacwjkg"
 :email-imap-ssl true
 :email-defaults-from "mohnicolas02@gmail.com"
 :email-watcher-interval-ms 5000
 :email-watcher-folder "INBOX"
 :plane-base-url "http://plane.karabiner.example.com"
 :plane-workspace-slug "smtb"}
```

### Approach 2: upload-to-vault.clj (Structured) ⭐

**Vault Paths**:
- `kv/jetlag/secrets/plane`
- `kv/jetlag/secrets/email`

**Result**:
```clojure
;; kv/jetlag/secrets/plane
{:api-key "plane_api_ecc58a1e1a7849fbacdc85415f086aea"
 :base-url "http://plane.karabiner.example.com"
 :workspace-slug "smtb"}

;; kv/jetlag/secrets/email
{:smtp {:host "smtp.gmail.com"
        :port 587
        :user "mohnicolas02@gmail.com"
        :pass "vogcjdvlodacwjkg"
        :tls true}
 :imap {:host "imap.gmail.com"
        :port 993
        :user "mohnicolas02@gmail.com"
        :pass "vogcjdvlodacwjkg"
        :ssl true}
 :defaults {:from "mohnicolas02@gmail.com"}
 :watcher {:interval-ms 5000
           :folder "INBOX"}}
```

---

## Which One to Use?

### Use `migrate-to-vault.clj` when:
- ❌ You have flat, unstructured secrets
- ❌ You want automatic categorization
- ❌ You're migrating from environment variables

### Use `upload-to-vault.clj` when: ⭐
- ✅ You want to preserve your original structure
- ✅ Your secrets are already well-organized
- ✅ You want logical grouping (plane, email, database, etc.)
- ✅ You want to read secrets back in the same structure

---

## Reading Back from Vault

### Flattened Approach
```clojure
(vault/read-secret config "kv" "jetlag/api-keys")
;; => {:plane-api-key "xxx"}

(vault/read-secret config "kv" "jetlag/config")
;; => {:email-smtp-host "..." :email-smtp-port 587 ...}
```

### Structured Approach ⭐
```clojure
(vault/read-secret config "kv" "jetlag/secrets/plane")
;; => {:api-key "xxx" :base-url "..." :workspace-slug "..."}

(vault/read-secret config "kv" "jetlag/secrets/email")
;; => {:smtp {...} :imap {...} :defaults {...} :watcher {...}}
```

---

## Recommendation for Jetlag

**Use the structured approach** (`upload-to-vault.clj`) because:

1. ✅ Preserves logical grouping (plane vs email)
2. ✅ Nested email config stays together (smtp, imap, defaults, watcher)
3. ✅ Easier to read and understand
4. ✅ Matches your original secrets.edn structure
5. ✅ More maintainable

The jetlag secrets are now uploaded using **both** approaches, so you can compare and choose which one works better for your use case!
