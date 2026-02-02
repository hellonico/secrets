(ns secrets.examples.vault-simple-test
  "Simple test to verify Vault connection"
  (:require [secrets.plugins.vault :as vault]))

(defn -main [& _args]
  (println "=== Vault Connection Test ===\n")

  ;; Uses VAULT_ADDR and VAULT_TOKEN environment variables
  (let [config (vault/vault-config)
        mount "kv"]  ; Your KV mount point

    (println "Testing with:")
    (println "  Address:" (:addr config))
    (println "  Token configured:" (boolean (:token config)))
    (println "  Mount:" mount)
    (println)

    ;; Test 1: Write a secret
    (println "1. Writing test secret...")
    (try
      (vault/write-secret! config mount "test/hello"
                           {:greeting "Hello Vault!"
                            :timestamp (str (System/currentTimeMillis))})
      (println "   ✓ SUCCESS: Secret written to" mount "/test/hello")
      (catch Exception e
        (println "   ✗ FAILED:" (.getMessage e))
        (println "   Error details:" (ex-data e))))
    (println)

    ;; Test 2: Read it back
    (println "2. Reading test secret...")
    (try
      (let [result (vault/read-secret config mount "test/hello")]
        (if result
          (do
            (println "   ✓ SUCCESS: Secret retrieved")
            (println "   Data:" result))
          (println "   ✗ Secret not found")))
      (catch Exception e
        (println "   ✗ FAILED:" (.getMessage e))))
    (println)

    ;; Test 3: List secrets
    (println "3. Listing secrets under 'test' path...")
    (try
      (let [secrets (vault/list-secrets config mount "test")]
        (println "   ✓ Found" (count secrets) "secret(s):")
        (doseq [s secrets]
          (println "     -" s)))
      (catch Exception e
        (println "   ✗ FAILED:" (.getMessage e))))

    (println "\n=== Test Complete ===")))

(comment
  ;; Run this from REPL or command line:
  ;; 1. Edit the config map above with your actual Vault address and token
  ;; 2. Run: clojure -M -m secrets.examples.vault-simple-test
  ;; Or from REPL:
  (-main))
