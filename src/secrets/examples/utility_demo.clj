(ns secrets.examples.utility-demo
  "Demo of the new utility functions in secrets.core"
  (:require [secrets.core :as secrets]))

(defn -main [& _args]
  (println "=== Secrets Utility Functions Demo ===\n")

  ;; Example 1: get-secret-or-env (simple lookup)
  (println "1. get-secret-or-env - Simple API key lookup")
  (println "   (secrets/get-secret-or-env :google-api-key)")
  (let [key (secrets/get-secret-or-env :google-api-key)]
    (if key
      (println "   ✓ Found:" (subs key 0 (min 20 (count key))) "...")
      (println "   ✗ Not found")))
  (println "   Checks: secrets.edn OR GOOGLE_API_KEY env var")
  (println)

  ;; Example 2: require-secret! (with helpful error messages)
  (println "2. require-secret! - With user-friendly errors")
  (println "   (secrets/require-secret! :brave-api-key)")
  (let [key (secrets/require-secret! :brave-api-key)]
    (when key
      (println "   ✓ Found:" (subs key 0 (min 20 (count key))) "...")))
  (println "   Auto-derives everything:")
  (println "     Display name: :brave-api-key => \"Brave API Key\"")
  (println "     Env var name: :brave-api-key => BRAVE_API_KEY")
  (println)

  ;; Example 3: Nested keys
  (println "3. Nested secret keys")
  (println "   (secrets/get-secret-or-env [:openai :api-key])")
  (let [key (secrets/get-secret-or-env [:openai :api-key])]
    (if key
      (println "   ✓ Found:" (subs key 0 (min 20 (count key))) "...")
      (println "   ✗ Not found")))
  (println "   Checks: secrets.edn OR OPENAI_API_KEY env var")
  (println)

  ;; Example 4: Non-existent key (shows error handling)
  (println "4. Non-existent key (shows error message)")
  (println "   (secrets/require-secret! :nonexistent-key)")
  (secrets/require-secret! :nonexistent-key)
  (println)

  ;; Example 5: Throwing version
  (println "5. require-secret!! - Throws if not found")
  (println "   (secrets/require-secret!! :another-nonexistent)")
  (try
    (secrets/require-secret!! :another-nonexistent)
    (catch Exception e
      (println "   ✗ Threw exception:" (.getMessage e))
      (println "      ex-data:" (ex-data e))))
  (println)

  (println "=== Demo Complete ==="))

(comment
  ;; Run from REPL:
  (-main)

  ;; Or from command line:
  ;; clojure -M -m secrets.examples.utility-demo
  )
