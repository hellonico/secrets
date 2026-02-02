(ns secrets.examples.vault-integration-demo
  "Demonstrates how to use Vault as a plugin for secrets.core
   
   This allows you to use (secrets/get-secret :email) and have it
   automatically read from Vault, just like it would from secrets.edn"
  (:require [secrets.core :as secrets]
            [secrets.plugins.vault :as vault]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]))

;; ---------- Step 1: Upload secrets to Vault in the right structure

(defn upload-secrets-for-plugin-use
  "Upload secrets to Vault in a structure that matches secrets.edn
   
   Instead of splitting into separate paths, we upload the entire
   secrets map to a single Vault path."
  [secrets-file vault-path]
  (let [secrets (with-open [r (io/reader secrets-file)]
                  (edn/read (java.io.PushbackReader. r)))
        config (vault/vault-config)
        mount "kv"]

    (println "=== Uploading Secrets to Vault ===")
    (println "  File:" secrets-file)
    (println "  Vault path:" vault-path)
    (println "  Secrets:" (keys secrets))
    (println)

    ;; Upload the entire secrets map to a single Vault path
    (vault/write-secret! config mount vault-path secrets)
    (println "✓ Uploaded successfully!")
    (println)

    ;; Verify
    (println "Verifying...")
    (let [retrieved (vault/read-secret config mount vault-path)]
      (println "✓ Retrieved" (count retrieved) "top-level keys")
      (println "  Keys:" (keys retrieved)))

    secrets))

;; ---------- Step 2: Create a Vault plugin for secrets.core

(defn make-vault-plugin
  "Create a Vault plugin that reads from a specific path"
  [vault-path]
  (let [config (vault/vault-config)
        mount "kv"
        ;; Cache the secrets map
        secrets-cache (atom nil)]

    {:name :vault
     :description (str "Vault secrets from " vault-path)

     ;; Get function - reads from cached secrets map
     :get-fn (fn [k-or-path]
               (when-not @secrets-cache
                 (reset! secrets-cache (vault/read-secret config mount vault-path)))
               (if (vector? k-or-path)
                 (get-in @secrets-cache k-or-path)
                 (get @secrets-cache k-or-path)))

     ;; Reload function - refreshes cache from Vault
     :reload-fn (fn []
                  (let [data (vault/read-secret config mount vault-path)]
                    (reset! secrets-cache data)
                    data))

     ;; All secrets function
     :all-fn (fn []
               (when-not @secrets-cache
                 (reset! secrets-cache (vault/read-secret config mount vault-path)))
               @secrets-cache)}))

;; ---------- Step 3: Demo

(defn -main [& _args]
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║  Vault Integration Demo                                   ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  (println)

  ;; 1. Upload jetlag secrets to Vault (as a single map)
  (println "STEP 1: Upload secrets to Vault")
  (println "---")
  (upload-secrets-for-plugin-use
   "/Users/nico/cool/pyjama-commercial/jetlag/secrets.edn"
   "jetlag/all")
  (println)

  ;; 2. Register Vault plugin
  (println "STEP 2: Register Vault plugin")
  (println "---")
  (let [vault-plugin (make-vault-plugin "jetlag/all")]
    (secrets/register-plugin! vault-plugin)
    (println "✓ Vault plugin registered")
    (println "  Active plugins:" (map :name (secrets/list-plugins))))
  (println)

  ;; 3. Use secrets.core API (reads from Vault!)
  (println "STEP 3: Use secrets/get-secret (reads from Vault)")
  (println "---")

  ;; Get top-level key
  (println "📦 (secrets/get-secret :plane)")
  (pp/pprint (secrets/get-secret :plane))
  (println)

  ;; Get nested value
  (println "📦 (secrets/get-secret [:email :smtp :host])")
  (println "  =>" (secrets/get-secret [:email :smtp :host]))
  (println)

  (println "📦 (secrets/get-secret [:email :imap])")
  (pp/pprint (secrets/get-secret [:email :imap]))
  (println)

  (println "📦 (secrets/get-secret [:plane :api-key])")
  (println "  =>" (secrets/get-secret [:plane :api-key]))
  (println)

  ;; 4. Show that it works exactly like secrets.edn
  (println "STEP 4: Verify it works like secrets.edn")
  (println "---")
  (println "All secrets:")
  (let [all (secrets/all-secrets)]
    (println "  Top-level keys:" (keys all))
    (println "  :email keys:" (keys (:email all)))
    (println "  :plane keys:" (keys (:plane all))))
  (println)

  ;; 5. Show source
  (println "STEP 5: Check where secrets come from")
  (println "---")
  (let [result (secrets/get-secret-with-source :email)]
    (println "  Secret: :email")
    (println "  Source:" (:source result))
    (println "  Keys:" (keys (:value result))))
  (println)

  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║  ✓ Complete! Secrets are now read from Vault              ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  (println)
  (println "💡 Usage:")
  (println "   (secrets/get-secret :email)")
  (println "   (secrets/get-secret [:email :smtp :host])")
  (println "   (secrets/get-secret [:plane :api-key])")
  (println)
  (println "   Works exactly like secrets.edn, but reads from Vault!"))

(comment
  ;; Run the demo
  (-main)

  ;; Or set up manually
  (require '[secrets.core :as secrets])
  (require '[secrets.examples.vault-integration-demo :as demo])

  ;; Register Vault plugin
  (secrets/register-plugin! (demo/make-vault-plugin "jetlag/all"))

  ;; Use it
  (secrets/get-secret :email)
  (secrets/get-secret [:email :smtp :host]))
