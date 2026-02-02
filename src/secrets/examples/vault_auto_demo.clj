(ns secrets.examples.vault-auto-demo
  "Demo showing automatic Vault integration with ENV_NAME"
  (:require [secrets.core :as secrets]
            [secrets.plugins.vault :as vault]
            [clojure.pprint :as pp]))

(defn -main [& _args]
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║  Vault Auto-Integration with ENV_NAME                     ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  (println)

  ;; Show environment
  (println "Environment Variables:")
  (println "  VAULT_ADDR:" (System/getenv "VAULT_ADDR"))
  (println "  VAULT_TOKEN:" (if (System/getenv "VAULT_TOKEN") "***set***" "not set"))
  (println "  VAULT_PATH:" (or (System/getenv "VAULT_PATH") "secrets (default)"))
  (println "  ENV_NAME:" (or (System/getenv "ENV_NAME") "not set"))
  (println)

  ;; Register Vault plugin
  (println "Registering Vault plugin...")
  (secrets/register-plugin! (vault/make-plugin))
  (println "✓ Registered")
  (println)

  (println "Active plugins:")
  (doseq [plugin (secrets/list-plugins)]
    (println "  •" (:name plugin) "-" (:description plugin)))
  (println)

  ;; Use secrets
  (println "═══ Reading Secrets ═══")
  (println)

  (println "📦 (secrets/get-secret :plane)")
  (pp/pprint (secrets/get-secret :plane))
  (println)

  (println "📦 (secrets/get-secret [:plane :api-key])")
  (println "  =>" (secrets/get-secret [:plane :api-key]))
  (println)

  (println "📦 (secrets/get-secret :email)")
  (pp/pprint (secrets/get-secret :email))
  (println)

  (println "📦 (secrets/get-secret [:email :smtp :host])")
  (println "  =>" (secrets/get-secret [:email :smtp :host]))
  (println)

  (println "📦 (secrets/get-secret [:email :imap :port])")
  (println "  =>" (secrets/get-secret [:email :imap :port]))
  (println)

  ;; Show where it came from
  (println "═══ Source Information ═══")
  (println)
  (let [result (secrets/get-secret-with-source :email)]
    (println "Secret: :email")
    (println "Source:" (:source result))
    (println "Keys:" (keys (:value result))))
  (println)

  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║  ✓ Complete!                                               ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  (println)
  (println "💡 The Vault plugin automatically uses:")
  (println "   - ENV_NAME for environment-aware paths")
  (println "   - VAULT_PATH for the base path")
  (println "   - Same API as file-based secrets!"))

(comment
  ;; Run with environment variables
  (-main))
