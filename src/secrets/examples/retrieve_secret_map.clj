(ns secrets.examples.retrieve-secret-map
  "Example demonstrating how to retrieve secrets as a map"
  (:require [secrets.core :as secrets]))

(defn -main [& _args]
  (println "=== Retrieve Secret Map Demo ===\n")

  ;; 1. Get all secrets as a map
  (println "1. Getting all secrets as a map...")
  (let [all-secrets (secrets/all-secrets)]
    (println "   ✓ Retrieved" (count all-secrets) "top-level secret(s)")
    (when (seq all-secrets)
      (println "   Secret keys available:")
      (doseq [k (keys all-secrets)]
        (println "     -" k)))
    (println))

  ;; 2. Get a specific nested secret map
  (println "2. Getting a specific nested secret map...")
  (println "   Example: retrieving [:database] as a map")
  (let [db-secrets (secrets/get-secret :database)]
    (if (map? db-secrets)
      (do
        (println "   ✓ Database secrets map:")
        (doseq [[k v] db-secrets]
          (println "    " k "=>" v)))
      (println "   ⓘ No database secrets found")))
  (println)

  ;; 3. Get specific values from a map
  (println "3. Getting specific values from nested maps...")
  (let [db-host (secrets/get-secret [:database :host])
        db-port (secrets/get-secret [:database :port])
        db-password (secrets/get-secret [:database :password])]
    (println "   Database Host:" (or db-host "not configured"))
    (println "   Database Port:" (or db-port "not configured"))
    (println "   Database Password:" (if db-password "***configured***" "not configured")))
  (println)

  ;; 4. Get API keys map
  (println "4. Getting API keys (common use case)...")
  (let [openai-key (secrets/get-secret [:openai :api-key])
        anthropic-key (secrets/get-secret [:anthropic :api-key])
        google-key (secrets/get-secret [:google :api-key])]
    (println "   OpenAI API Key:" (if openai-key "***configured***" "not configured"))
    (println "   Anthropic API Key:" (if anthropic-key "***configured***" "not configured"))
    (println "   Google API Key:" (if google-key "***configured***" "not configured")))
  (println)

  ;; 5. Using require-secret! for mandatory secrets
  (println "5. Using require-secret! for mandatory secrets...")
  (println "   (This will print helpful error if secret is missing)")
  (let [api-key (secrets/require-secret! [:myapp :api-key])]
    (if api-key
      (println "   ✓ API key is configured")
      (println "   ⚠ API key is NOT configured (see above error)")))
  (println)

  ;; 6. Reload secrets (useful if files changed)
  (println "6. Reloading secrets from all sources...")
  (let [reload-result (secrets/reload-secrets!)]
    (println "   ✓ Reloaded from" (count reload-result) "plugin(s)")
    (doseq [[plugin-name data] reload-result]
      (println "    " plugin-name "=>" (count (or data {})) "secret(s)")))
  (println)

  (println "=== Demo Complete ===")
  (println)
  (println "📝 To configure secrets, create one of:")
  (println "   - ~/secrets.edn (plain text)")
  (println "   - ~/secrets.edn.enc (encrypted)")
  (println "   - ./secrets.edn (local to this project)")
  (println "   - ./secrets.edn.enc (local encrypted)")
  (println)
  (println "Example secrets.edn format:")
  (println "{:database {:host \"localhost\"")
  (println "            :port 5432")
  (println "            :password \"secret123\"}")
  (println " :openai {:api-key \"sk-...\"}")
  (println " :myapp {:api-key \"your-key-here\"}}"))

(comment
  ;; To run this demo:
  ;; clojure -M -m secrets.examples.retrieve-secret-map

  ;; Or from REPL:
  (-main)

  ;; Create a sample secrets.edn file:
  (require '[clojure.java.io :as io])
  (spit "secrets.edn"
        (pr-str {:database {:host "localhost"
                            :port 5432
                            :password "db-secret"}
                 :openai {:api-key "sk-test-key"}
                 :myapp {:api-key "my-app-secret"}}))

  ;; Then run the demo again
  (-main))
