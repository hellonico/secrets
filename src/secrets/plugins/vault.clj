(ns secrets.plugins.vault
  "HashiCorp Vault integration plugin for secrets management.
   Supports KV v2 secrets engine."
  (:require [clojure.string :as str]
            [clojure.data.json :as json])
  (:import [java.net URI HttpURLConnection]
           [java.io BufferedReader InputStreamReader]))

;; ---------- HTTP client utilities

(defn- make-request
  "Makes an HTTP request to Vault. Returns parsed JSON response."
  [{:keys [url method headers]}]
  (let [conn (-> (URI/create url)
                 .toURL
                 .openConnection)]
    (doto ^HttpURLConnection conn
      (.setRequestMethod (name (or method :GET)))
      (.setRequestProperty "Content-Type" "application/json"))

    ;; Set custom headers (e.g., X-Vault-Token)
    (doseq [[k v] headers]
      (.setRequestProperty conn k v))

    (try
      (let [status (.getResponseCode conn)]
        (if (< status 400)
          (with-open [reader (BufferedReader.
                              (InputStreamReader.
                               (.getInputStream conn)))]
            (json/read-str (slurp reader) :key-fn keyword))
          (throw (ex-info (str "Vault request failed with status " status)
                          {:status status
                           :url url}))))
      (finally
        (.disconnect conn)))))

;; ---------- Vault configuration

(defn- get-env-name
  "Get the current environment name from ENV_NAME environment variable.
   Returns nil if not set."
  []
  (let [env-name (System/getenv "ENV_NAME")]
    (when-not (str/blank? env-name)
      env-name)))

(defn- env-aware-path
  "Prepend environment name to path if ENV_NAME is set.
   
   Examples:
   - ENV_NAME=staging, path='myapp/config' => 'staging/myapp/config'
   - ENV_NAME not set, path='myapp/config' => 'myapp/config'"
  [path]
  (if-let [env-name (get-env-name)]
    (if (str/blank? path)
      env-name
      (str env-name "/" path))
    path))

(defn vault-config
  "Returns Vault configuration from environment or explicit params.
   
   Required env vars (if not provided as params):
   - VAULT_ADDR: Vault server address (e.g., http://localhost:8200)
   - VAULT_TOKEN: Authentication token
   
   Optional env vars:
   - VAULT_NAMESPACE: Namespace (for Vault Enterprise)
   - ENV_NAME: Environment name (e.g., 'staging', 'production')
                When set, all paths are automatically prefixed with the environment name"
  ([]
   (vault-config {}))
  ([{:keys [addr token namespace]}]
   {:addr (or addr (System/getenv "VAULT_ADDR"))
    :token (or token (System/getenv "VAULT_TOKEN"))
    :namespace (or namespace (System/getenv "VAULT_NAMESPACE"))
    :env-name (get-env-name)}))

(defn validate-config!
  "Validates Vault configuration. Throws if required fields are missing."
  [{:keys [addr token] :as config}]
  (when (str/blank? addr)
    (throw (ex-info "Vault address is required (VAULT_ADDR)" {:config config})))
  (when (str/blank? token)
    (throw (ex-info "Vault token is required (VAULT_TOKEN)" {:config config})))
  config)

;; ---------- Vault API operations

(defn read-secret
  "Reads a secret from Vault KV v2 engine.
   
   Parameters:
   - config: Vault configuration map (from vault-config)
   - mount: KV secrets engine mount path (e.g., 'secret')
   - path: Secret path within the mount (e.g., 'myapp/config')
   
   Returns the secret data map, or nil if not found.
   
   Example:
   (read-secret (vault-config) \"secret\" \"myapp/database\")
   => {:username \"admin\" :password \"secret123\"}"
  ([mount path]
   (read-secret (vault-config) mount path))
  ([config mount path]
   (validate-config! config)
   (let [{:keys [addr token namespace]} config
         ;; Apply environment-aware path transformation
         effective-path (env-aware-path path)
         ;; KV v2 format: /v1/{mount}/data/{path}
         url (str addr "/v1/" mount "/data/" effective-path)
         headers (cond-> {"X-Vault-Token" token}
                   (not (str/blank? namespace))
                   (assoc "X-Vault-Namespace" namespace))]
     (try
       (-> (make-request {:url url
                          :method :GET
                          :headers headers})
           :data
           :data)
       (catch Exception e
         (if (and (instance? clojure.lang.ExceptionInfo e)
                  (= 404 (:status (ex-data e))))
           nil  ;; Secret not found
           (throw e)))))))

(defn write-secret!
  "Writes a secret to Vault KV v2 engine.
   
   Parameters:
   - config: Vault configuration map (from vault-config)
   - mount: KV secrets engine mount path (e.g., 'secret')
   - path: Secret path within the mount (e.g., 'myapp/config')
   - data: Map of secret key-value pairs
   
   Example:
   (write-secret! (vault-config) \"secret\" \"myapp/database\"
                  {:username \"admin\" :password \"secret123\"})"
  ([mount path data]
   (write-secret! (vault-config) mount path data))
  ([config mount path data]
   (validate-config! config)
   (let [{:keys [addr token namespace]} config
         ;; Apply environment-aware path transformation
         effective-path (env-aware-path path)
         url (str addr "/v1/" mount "/data/" effective-path)
         headers (cond-> {"X-Vault-Token" token}
                   (not (str/blank? namespace))
                   (assoc "X-Vault-Namespace" namespace))
         conn (-> (URI/create url)
                  .toURL
                  .openConnection)]
     (doto ^HttpURLConnection conn
       (.setRequestMethod "POST")
       (.setRequestProperty "Content-Type" "application/json")
       (.setDoOutput true))

     ;; Set custom headers
     (doseq [[k v] headers]
       (.setRequestProperty conn k v))

     ;; Write the request body (wrapped in {"data": {...}})
     (with-open [os (.getOutputStream conn)]
       (.write os (.getBytes (json/write-str {:data data}) "UTF-8")))

     (try
       (let [status (.getResponseCode conn)]
         (when (>= status 400)
           (throw (ex-info (str "Failed to write secret, status " status)
                           {:status status
                            :url url
                            :path path})))
         :ok)
       (finally
         (.disconnect conn))))))

(defn list-secrets
  "Lists secret names at the given path in Vault KV v2 engine.
   
   Parameters:
   - config: Vault configuration map (from vault-config)
   - mount: KV secrets engine mount path (e.g., 'secret')
   - path: Directory path to list (use \"\" for root)
   
   Returns a vector of secret names.
   
   Example:
   (list-secrets (vault-config) \"secret\" \"myapp\")
   => [\"database\" \"api-keys\"]"
  ([mount path]
   (list-secrets (vault-config) mount path))
  ([config mount path]
   (validate-config! config)
   (let [{:keys [addr token namespace]} config
         ;; Apply environment-aware path transformation
         effective-path (env-aware-path path)
         ;; KV v2 list format: /v1/{mount}/metadata/{path}?list=true
         base-path (if (str/blank? effective-path) "" (str effective-path "/"))
         url (str addr "/v1/" mount "/metadata/" base-path "?list=true")
         headers (cond-> {"X-Vault-Token" token}
                   (not (str/blank? namespace))
                   (assoc "X-Vault-Namespace" namespace))]
     (try
       (-> (make-request {:url url
                          :method :GET
                          :headers headers})
           :data
           :keys
           vec)
       (catch Exception e
         (if (and (instance? clojure.lang.ExceptionInfo e)
                  (= 404 (:status (ex-data e))))
           []  ;; Path not found or empty
           (throw e)))))))

;; ---------- Integration with secrets.core

(defn vault->secrets-map
  "Fetches secrets from Vault and converts them to a flat map.
   
   Parameters:
   - config: Vault configuration
   - mount: KV secrets engine mount path
   - path: Base path for secrets
   - key-fn: Optional function to transform keys (default: identity)
   
   Example:
   (vault->secrets-map (vault-config) \"secret\" \"myapp\")
   => {:database-username \"admin\"
       :database-password \"secret123\"
       :api-key \"xyz\"}"
  ([mount path]
   (vault->secrets-map (vault-config) mount path))
  ([config mount path]
   (vault->secrets-map config mount path identity))
  ([config mount path key-fn]
   (validate-config! config)
   (let [secret-names (list-secrets config mount path)]
     (reduce
      (fn [acc name]
        (let [full-path (if (str/blank? path)
                          name
                          (str path "/" name))
              secret-data (read-secret config mount full-path)]
          (if secret-data
            (reduce-kv
             (fn [m k v]
               (let [namespaced-key (keyword (str name "-" (clojure.core/name k)))]
                 (assoc m (key-fn namespaced-key) v)))
             acc
             secret-data)
            acc)))
      {}
      secret-names))))
