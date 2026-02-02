(ns secrets.examples.vault-demo
  "Example demonstrating Vault plugin usage"
  (:require [secrets.plugins.vault :as vault]))

(defn -main [& _args]
  (println "=== Vault Plugin Demo ===\n")

  ;; 1. Configuration
  (println "1. Configuring Vault connection...")
  (let [config (vault/vault-config)]
    (println "   Vault address:" (:addr config))
    (println "   Token configured:" (boolean (:token config)))
    (println)

    ;; 2. Write a sample secret
    (println "2. Writing a sample secret...")
    (try
      (vault/write-secret! config "kv" "demo/sample"
                           {:message "Hello from Vault!"
                            :created-at (str (java.time.Instant/now))})
      (println "   ✓ Secret written successfully")
      (catch Exception e
        (println "   ✗ Error:" (.getMessage e))))
    (println)

    ;; 3. Read the secret back
    (println "3. Reading the secret back...")
    (try
      (let [secret (vault/read-secret config "kv" "demo/sample")]
        (println "   ✓ Secret retrieved:")
        (println "     Message:" (:message secret))
        (println "     Created:" (:created-at secret)))
      (catch Exception e
        (println "   ✗ Error:" (.getMessage e))))
    (println)

    ;; 4. List secrets
    (println "4. Listing secrets under 'demo' path...")
    (try
      (let [secrets (vault/list-secrets config "kv" "demo")]
        (println "   ✓ Found" (count secrets) "secret(s):")
        (doseq [s secrets]
          (println "     -" s)))
      (catch Exception e
        (println "   ✗ Error:" (.getMessage e))))
    (println)

    ;; 5. Vault to secrets map
    (println "5. Converting Vault secrets to map...")
    (try
      (let [secrets-map (vault/vault->secrets-map config "kv" "demo")]
        (println "   ✓ Secrets map:")
        (doseq [[k v] secrets-map]
          (println "    " k "=>" v)))
      (catch Exception e
        (println "   ✗ Error:" (.getMessage e))))
    (println)

    ;; 6. Test error handling (reading non-existent secret)...")
    (println "6. Testing error handling (reading non-existent secret)...")
    (let [result (vault/read-secret config "kv" "does/not/exist")]
      (if (nil? result)
        (println "   ✓ Correctly returned nil for missing secret")
        (println "   ✗ Expected nil but got:" result)))
    (println)

    (println "=== Demo Complete ===")))

(comment
  ;; To run this demo:
  ;; 1. Start a Vault dev server:
  ;;    vault server -dev
  ;;
  ;; 2. Export the credentials (shown in vault output):
  ;;    export VAULT_ADDR='http://127.0.0.1:8200'
  ;;    export VAULT_TOKEN='...'
  ;;
  ;; 3. Run the demo:
  ;;    clojure -M -m secrets.examples.vault-demo

  ;; Or run from REPL:
  (-main))
