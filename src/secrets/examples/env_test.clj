(ns secrets.examples.env-test
  "Quick test to verify environment-based secret loading works."
  (:require [secrets.core :as secrets]))

(defn test-without-env
  "Test secret loading without ENV_NAME set."
  []
  (println "\n=== Test 1: Without ENV_NAME ===")
  (println "ENV_NAME:" (or (System/getenv "ENV_NAME") "(not set)"))

  ;; Reload to pick up test-resources files
  (secrets/reload-secrets!)

  (let [api-key (secrets/get-secret :api-key)
        db-host (secrets/get-secret [:database :host])
        feature (secrets/get-secret :feature-x-enabled)]
    (println "API Key:" api-key)
    (println "Database Host:" db-host)
    (println "Feature X Enabled:" feature)

    (if (= api-key "default-key")
      (println "✓ Correctly loaded default secrets")
      (println "✗ Expected 'default-key', got:" api-key))))

(defn test-with-staging-env
  "Test secret loading with ENV_NAME=staging.
   Note: This requires running with ENV_NAME=staging set externally."
  []
  (println "\n=== Test 2: With ENV_NAME=staging ===")
  (println "ENV_NAME:" (or (System/getenv "ENV_NAME") "(not set)"))

  (if-let [env-name (System/getenv "ENV_NAME")]
    (do
      ;; Reload to pick up environment-specific files
      (secrets/reload-secrets!)

      (let [api-key (secrets/get-secret :api-key)
            db-host (secrets/get-secret [:database :host])
            feature (secrets/get-secret :feature-x-enabled)]
        (println "API Key:" api-key)
        (println "Database Host:" db-host)
        (println "Feature X Enabled:" feature)

        (if (and (= api-key "staging-key")
                 (= db-host "staging.example.com")
                 (= feature true))
          (println "✓ Correctly loaded staging-specific secrets")
          (println "✗ Expected staging values, got:" {:api-key api-key :db-host db-host :feature feature}))))
    (println "⚠ Skipping test - ENV_NAME not set. Run with: ENV_NAME=staging clojure -M:env-test")))

(defn -main
  "Run all tests."
  [& _args]
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║  Environment-Based Secret Loading Test                    ║")
  (println "╚════════════════════════════════════════════════════════════╝")

  (test-without-env)
  (test-with-staging-env)

  (println "\n=== Instructions ===")
  (println "To test environment-specific loading:")
  (println "  cd test-resources")
  (println "  ENV_NAME=staging clojure -M:env-test")
  (println))
