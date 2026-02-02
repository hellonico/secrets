(ns secrets.examples.upload-to-vault
  "Upload secrets to Vault preserving their original structure.
   
   This script uploads secrets as-is without flattening or reorganizing.
   
   Usage:
   export VAULT_ADDR='http://127.0.0.1:8250'
   export VAULT_TOKEN='your-token'
   clojure -M -m secrets.examples.upload-to-vault <secrets-file> <vault-path>
   
   Example:
   clojure -M -m secrets.examples.upload-to-vault jetlag/secrets.edn jetlag/secrets"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [secrets.plugins.vault :as vault]))

(defn read-secrets-file
  "Read secrets from an EDN file"
  [file-path]
  (with-open [r (io/reader file-path)]
    (edn/read (java.io.PushbackReader. r))))

(defn upload-nested-secrets
  "Upload secrets to Vault, preserving nested structure.
   
   Each top-level key becomes a separate Vault path:
   {:plane {...} :email {...}} =>
     vault-path/plane
     vault-path/email"
  [config mount base-path secrets-map dry-run?]
  (println "=== Uploading Secrets to Vault ===\n")

  (println "Vault Configuration:")
  (println "  Address:" (:addr config))
  (println "  Mount:" mount)
  (println "  Base path:" base-path)
  (when dry-run?
    (println "  Mode: DRY RUN"))
  (println)

  (doseq [[k v] secrets-map]
    (let [vault-path (str base-path "/" (name k))]
      (println "📦" (str/upper-case (name k)))
      (println "   Path:" (str mount "/" vault-path))

      (if (map? v)
        (do
          (println "   Secrets:" (count v))
          (doseq [[sk sv] v]
            (let [display-val (if (and (string? sv) (> (count sv) 30))
                                (str (subs sv 0 30) "...")
                                sv)]
              (println "     •" sk ":" display-val)))

          (if dry-run?
            (println "   ⚠️  DRY RUN - Would upload to Vault")
            (try
              (vault/write-secret! config mount vault-path v)
              (println "   ✓ Successfully uploaded")
              (catch Exception e
                (println "   ✗ FAILED:" (.getMessage e))))))

        (do
          (println "   Value:" (if (and (string? v) (> (count v) 30))
                                 (str (subs v 0 30) "...")
                                 v))
          (if dry-run?
            (println "   ⚠️  DRY RUN - Would upload to Vault")
            (try
              (vault/write-secret! config mount vault-path {:value v})
              (println "   ✓ Successfully uploaded")
              (catch Exception e
                (println "   ✗ FAILED:" (.getMessage e)))))))
      (println)))

  (println "=== Upload Complete ==="))

(defn -main [& args]
  (let [[secrets-file vault-path & flags] args
        dry-run? (some #{"--dry-run"} flags)]

    (when (or (nil? secrets-file) (nil? vault-path))
      (println "Usage: clojure -M -m secrets.examples.upload-to-vault <secrets-file> <vault-path> [--dry-run]")
      (println)
      (println "Example:")
      (println "  clojure -M -m secrets.examples.upload-to-vault jetlag/secrets.edn jetlag/secrets")
      (System/exit 1))

    (try
      (let [secrets (read-secrets-file secrets-file)
            vault-config (vault/vault-config)
            mount (or (System/getenv "VAULT_MOUNT") "kv")]

        (upload-nested-secrets vault-config mount vault-path secrets dry-run?))

      (catch Exception e
        (println "ERROR:" (.getMessage e))
        (when-let [data (ex-data e)]
          (println "Details:" data))
        (System/exit 1)))))

(comment
  ;; Test with dry run
  (-main "/Users/nico/cool/pyjama-commercial/jetlag/secrets.edn" "jetlag/secrets" "--dry-run")

  ;; Actually upload
  (-main "/Users/nico/cool/pyjama-commercial/jetlag/secrets.edn" "jetlag/secrets"))
