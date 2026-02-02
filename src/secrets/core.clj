(ns secrets.core
  "Plugin-based secrets management core.
   
   Manages multiple secret plugins and provides a unified lookup interface.
   Plugins are checked in priority order until a secret is found."
  (:require [secrets.plugins.files :as files-plugin]))

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
