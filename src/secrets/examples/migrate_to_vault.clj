(ns secrets.examples.migrate-to-vault
  "Migration script to upload file-based secrets to Vault.
   
   This script reads secrets from files and environment variables using the
   files plugin, then uploads them to Vault.
   
   Usage:
   export VAULT_ADDR='http://127.0.0.1:8250'
   export VAULT_TOKEN='your-token'
   clojure -M -m secrets.examples.migrate-to-vault [options]
   
   Options:
   --mount <name>     Vault mount point (default: kv)
   --path <path>      Vault path prefix (default: pyjama)
   --dry-run          Show what would be uploaded without actually doing it
   --force            Overwrite existing secrets"
  (:require [secrets.plugins.files :as files]
            [secrets.plugins.vault :as vault]
            [clojure.string :as str]))

;; ---------- Configuration

(defn parse-args
  "Parse command line arguments"
  [args]
  (loop [args args
         opts {:mount "kv"
               :path "pyjama"
               :dry-run false
               :force false}]
    (if (empty? args)
      opts
      (let [[flag value & rest-args] args]
        (case flag
          "--mount" (recur rest-args (assoc opts :mount value))
          "--path" (recur rest-args (assoc opts :path value))
          "--dry-run" (recur rest-args (assoc opts :dry-run true))
          "--force" (recur rest-args (assoc opts :force true))
          (do
            (println "Unknown option:" flag)
            (recur rest-args opts)))))))

;; ---------- Secrets Organization

(defn organize-secrets
  "Organize secrets into logical groups for Vault.
   
   Groups secrets by category (api-keys, database, config, etc.)
   based on naming patterns."
  [secrets-map]
  (let [api-key-pattern #"(?i).*?(api|key|token).*"
        db-pattern #"(?i).*(database|db|postgres|mysql).*"
        config-pattern #"(?i).*(port|host|url|environment|config).*"]

    (reduce-kv
     (fn [acc k v]
       (cond
         ;; Group API keys
         (re-matches api-key-pattern (name k))
         (update acc :api-keys assoc k v)

         ;; Group database configs
         (re-matches db-pattern (name k))
         (update acc :database assoc k v)

         ;; Everything else goes to config
         :else
         (update acc :config assoc k v)))
     {:api-keys {}
      :database {}
      :config {}}
     secrets-map)))

(defn flatten-nested
  "Flatten nested maps into a single level with namespaced keys.
   {:a {:b 1}} => {:a-b 1}"
  ([m] (flatten-nested m ""))
  ([m prefix]
   (reduce-kv
    (fn [acc k v]
      (let [new-key (if (empty? prefix)
                      k
                      (keyword (str prefix "-" (name k))))]
        (if (map? v)
          (merge acc (flatten-nested v (name new-key)))
          (assoc acc new-key v))))
    {}
    m)))

;; ---------- Migration Logic

(defn check-existing-secret
  "Check if a secret already exists in Vault"
  [config mount path]
  (try
    (some? (vault/read-secret config mount path))
    (catch Exception _
      false)))

(defn migrate-secret-group
  "Migrate a group of secrets to Vault"
  [config mount base-path group-name secrets dry-run? force?]
  (when (seq secrets)
    (let [vault-path (str base-path "/" group-name)
          exists? (check-existing-secret config mount vault-path)]

      (println)
      (println "📦" (str/upper-case group-name))
      (println "   Path:" (str mount "/" vault-path))
      (println "   Secrets:" (count secrets))

      (doseq [[k v] secrets]
        (let [display-val (if (and (string? v) (> (count v) 20))
                            (str (subs v 0 20) "...")
                            v)]
          (println "     •" k ":" display-val)))

      (cond
        dry-run?
        (println "   ⚠️  DRY RUN - Would upload to Vault")

        (and exists? (not force?))
        (println "   ⚠️  SKIPPED - Secret exists (use --force to overwrite)")

        :else
        (try
          (vault/write-secret! config mount vault-path secrets)
          (println "   ✓ Successfully uploaded to Vault")
          (catch Exception e
            (println "   ✗ FAILED:" (.getMessage e))))))))

(defn migrate-all
  "Main migration function"
  [{:keys [mount path dry-run force] :as opts}]
  (println "=== Secrets Migration to Vault ===")
  (println)

  ;; 1. Load secrets from files
  (println "1. Loading secrets from files and environment...")
  (files/reload!)
  (let [all-secrets (files/all-secrets)
        flat-secrets (flatten-nested all-secrets)]

    (println "   ✓ Loaded" (count flat-secrets) "secret(s)")
    (println)

    ;; 2. Organize secrets
    (println "2. Organizing secrets by category...")
    (let [organized (organize-secrets flat-secrets)
          vault-config (vault/vault-config)]

      (println "   ✓ Organized into" (count (filter #(seq (val %)) organized)) "group(s)")

      ;; 3. Show configuration
      (println)
      (println "3. Vault Configuration:")
      (println "   Address:" (:addr vault-config))
      (println "   Token configured:" (boolean (:token vault-config)))
      (println "   Mount point:" mount)
      (println "   Base path:" path)
      (when dry-run
        (println "   Mode: DRY RUN (no changes will be made)"))
      (when force
        (println "   Mode: FORCE (will overwrite existing secrets)"))

      ;; 4. Migrate each group
      (println)
      (println "4. Uploading secrets to Vault...")

      (migrate-secret-group vault-config mount path "api-keys"
                            (:api-keys organized) dry-run force)
      (migrate-secret-group vault-config mount path "database"
                            (:database organized) dry-run force)
      (migrate-secret-group vault-config mount path "config"
                            (:config organized) dry-run force)

      ;; 5. Summary
      (println)
      (println "=== Migration Complete ===")
      (when dry-run
        (println)
        (println "This was a DRY RUN. Run without --dry-run to actually upload secrets.")))))

;; ---------- Main entry point

(defn -main [& args]
  (try
    (let [opts (parse-args args)]
      (migrate-all opts))
    (catch Exception e
      (println "ERROR:" (.getMessage e))
      (when-let [data (ex-data e)]
        (println "Details:" data))
      (System/exit 1))))

(comment
  ;; Test migration with dry run
  (-main "--dry-run")

  ;; Actually migrate
  (-main)

  ;; Migrate to custom path
  (-main "--path" "myapp" "--mount" "secret")

  ;; Force overwrite existing secrets
  (-main "--force"))
