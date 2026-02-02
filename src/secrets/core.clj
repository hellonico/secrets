(ns secrets.core
  "Plugin-based secrets management core.
   
   Manages multiple secret plugins and provides a unified lookup interface.
   Plugins are checked in priority order until a secret is found."
  (:require [secrets.plugins.files :as files-plugin]
            [clojure.string]))

;; ---------- Plugin Registry

(def ^:private plugins
  "Registry of secret plugins.
   Each plugin must implement: (get-secret plugin-instance key-or-path)
   Plugins are checked in order; first non-nil result wins."
  (atom []))

(def ^:private default-plugins
  "Default plugins loaded on initialization."
  [{:name :files
    :description "File and environment-based secrets"
    :get-fn files-plugin/get-secret
    :reload-fn files-plugin/reload!
    :all-fn files-plugin/all-secrets}])

;; ---------- Plugin Management

(defn register-plugin!
  "Register a secret plugin.
   
   Plugin map should contain:
   - :name        - Unique keyword identifier
   - :description - Human-readable description
   - :get-fn      - Function (fn [key-or-path] => value or nil)
   - :reload-fn   - Optional function (fn [] => reloaded-data)
   - :all-fn      - Optional function (fn [] => all-secrets-map)
   
   Plugins are checked in registration order."
  [plugin]
  (when-not (:name plugin)
    (throw (ex-info "Plugin must have a :name" {:plugin plugin})))
  (when-not (:get-fn plugin)
    (throw (ex-info "Plugin must have a :get-fn" {:plugin plugin})))
  (swap! plugins conj plugin)
  plugin)

(defn unregister-plugin!
  "Remove a plugin by name."
  [plugin-name]
  (swap! plugins (fn [ps] (remove #(= (:name %) plugin-name) ps)))
  nil)

(defn list-plugins
  "List all registered plugins."
  []
  @plugins)

(defn get-plugin
  "Get a plugin by name."
  [plugin-name]
  (first (filter #(= (:name %) plugin-name) @plugins)))

;; ---------- Initialization

(defn init-default-plugins!
  "Initialize default plugins (files and environment).
   This is called automatically on namespace load."
  []
  (reset! plugins [])
  (doseq [plugin default-plugins]
    (register-plugin! plugin))
  @plugins)

;; Initialize on load
(init-default-plugins!)

;; ---------- Unified Secret Lookup

(defn get-secret
  "Get a secret by key or path, checking all registered plugins in order.
   
   Examples:
   (get-secret :api-key)          ; => \"value\"
   (get-secret [:openai :api-key]) ; => \"sk-...\"
   
   Returns nil if no plugin can provide the secret."
  [k-or-path]
  (loop [ps @plugins]
    (when (seq ps)
      (let [plugin (first ps)
            result ((:get-fn plugin) k-or-path)]
        (if (some? result)
          result
          (recur (rest ps)))))))

(defn get-secret-with-source
  "Like get-secret, but returns a map with :value and :source (plugin name).
   Returns nil if secret not found."
  [k-or-path]
  (loop [ps @plugins]
    (when (seq ps)
      (let [plugin (first ps)
            result ((:get-fn plugin) k-or-path)]
        (if (some? result)
          {:value result :source (:name plugin)}
          (recur (rest ps)))))))

;; ---------- Bulk Operations

(defn all-secrets
  "Get all secrets from all plugins, merged together.
   Later plugins override earlier ones on conflicts."
  []
  (let [all-fns (keep :all-fn @plugins)]
    (apply files-plugin/deep-merge (map #(%) all-fns))))

(defn reload-secrets!
  "Reload secrets from all plugins that support reloading.
   Returns a map of plugin-name => reloaded data."
  []
  (reduce
   (fn [acc plugin]
     (if-let [reload-fn (:reload-fn plugin)]
       (assoc acc (:name plugin) (reload-fn))
       acc))
   {}
   @plugins))

;; ---------- Backwards Compatibility Utilities

(defn write-encrypted-secrets!
  "Write encrypted secrets to file (delegates to files plugin).
   For backwards compatibility."
  [path m]
  (files-plugin/write-encrypted-secrets! path m))

;; ---------- Convenience Utilities

(defn- key->env-name
  "Convert a secret key to its conventional environment variable name.
   :google-api-key => \"GOOGLE_API_KEY\"
   [:brave :api-key] => \"BRAVE_API_KEY\""
  [k]
  (-> (if (keyword? k) (name k) (clojure.string/join "-" (map name k)))
      (clojure.string/upper-case)
      (clojure.string/replace "-" "_")))

(defn- key->display-name
  "Convert a secret key to a human-readable display name.
   :google-api-key => \"Google API Key\"
   [:brave :api-key] => \"Brave API Key\""
  [k]
  (-> (if (keyword? k) (name k) (clojure.string/join "-" (map name k)))
      (clojure.string/replace "-" " ")
      (clojure.string/split #"\s+")
      (->> (map clojure.string/capitalize)
           (clojure.string/join " "))))

(defn get-secret-or-env
  "Get a secret with fallback to an environment variable.
   
   Automatically derives the env var name from the secret key:
   :google-api-key => GOOGLE_API_KEY
   [:brave :api :key] => BRAVE_API_KEY
   
   This is useful for API keys that might be in secrets.edn OR a direct env var.
   
   Parameters:
   - secret-key: Keyword or path vector for the secret (e.g., :google-api-key)
   
   Examples:
   (get-secret-or-env :google-api-key)
   ;; Checks: secrets.edn first, then GOOGLE_API_KEY env var
   
   (get-secret-or-env [:brave :api-key])
   ;; Checks: secrets.edn first, then BRAVE_API_KEY env var"
  [secret-key]
  (or (get-secret secret-key)
      (System/getenv (key->env-name secret-key))))

(defn require-secret!
  "Get a secret with env var fallback, or print helpful error and return nil.
   
   Automatically derives both the env var name and display name from the key.
   
   Parameters:
   - secret-key: Keyword or path for the secret (e.g., :google-api-key)
   
   Example:
   (require-secret! :google-api-key)
   ;; If not found, prints:
   ;;   ⚠️  Google API Key not found!
   ;;      Please add :google-api-key to your secrets.edn
   ;;      OR set GOOGLE_API_KEY environment variable.
   ;; If found, returns the secret value"
  [secret-key]
  (let [value (get-secret-or-env secret-key)]
    (when-not value
      (let [display-name (key->display-name secret-key)
            env-name (key->env-name secret-key)]
        (println (str "⚠️  " display-name " not found!"))
        (println (str "   Please add " secret-key " to your secrets.edn"))
        (println (str "   OR set " env-name " environment variable."))))
    value))

(defn require-secret!!
  "Get a secret with env var fallback, or throw an exception.
   
   Like require-secret! but throws instead of returning nil.
   Automatically derives both the env var name and display name from the key.
   
   Parameters:
   - secret-key: Keyword or path for the secret (e.g., :google-api-key)
   
   Example:
   (require-secret!! :google-api-key)
   ;; If not found, prints helpful message AND throws exception
   ;; If found, returns the secret value"
  [secret-key]
  (let [value (require-secret! secret-key)]
    (when-not value
      (throw (ex-info (str (key->display-name secret-key) " is required")
                      {:secret-key secret-key
                       :env-var (key->env-name secret-key)})))
    value))
