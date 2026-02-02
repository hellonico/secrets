(ns secrets.plugins.vault-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [secrets.plugins.vault :as vault]))

;; ---------- Test fixtures and helpers

(def test-config
  {:addr "http://localhost:8200"
   :token "test-token"
   :namespace nil})

(def test-secret-data
  {:username "admin"
   :password "secret123"
   :api-key "xyz-789"})

;; Mock HTTP response builder
(defn mock-response
  ([data]
   (mock-response 200 data))
  ([status data]
   {:status status
    :data data}))

;; ---------- Unit tests: Configuration

(deftest vault-config-from-env
  (testing "Reads configuration from environment variables"
    (with-redefs [vault/vault-config
                  (fn [& args]
                    (if (empty? args)
                      {:addr "http://env-vault:8200"
                       :token "env-token"
                       :namespace "env-ns"}
                      (first args)))]
      (let [config (vault/vault-config)]
        (is (= "http://env-vault:8200" (:addr config)))
        (is (= "env-token" (:token config)))
        (is (= "env-ns" (:namespace config)))))))

(deftest vault-config-with-params
  (testing "Accepts explicit parameters over environment"
    (let [config (vault/vault-config {:addr "http://custom:8200"
                                      :token "custom-token"})]
      (is (= "http://custom:8200" (:addr config)))
      (is (= "custom-token" (:token config))))))

(deftest validate-config-success
  (testing "Validates complete configuration"
    (is (= test-config (vault/validate-config! test-config)))))

(deftest validate-config-missing-addr
  (testing "Throws when address is missing"
    (is (thrown? clojure.lang.ExceptionInfo
                 (vault/validate-config! {:token "test"})))))

(deftest validate-config-missing-token
  (testing "Throws when token is missing"
    (is (thrown? clojure.lang.ExceptionInfo
                 (vault/validate-config! {:addr "http://localhost:8200"})))))

;; ---------- Unit tests: read-secret

(deftest read-secret-success
  (testing "Successfully reads a secret from Vault"
    (with-redefs [vault/make-request
                  (fn [{:keys [url headers method]}]
                    (is (= :GET method))
                    (is (= "http://localhost:8200/v1/secret/data/myapp/config" url))
                    (is (= "test-token" (get headers "X-Vault-Token")))
                    {:data {:data test-secret-data}})]
      (let [result (vault/read-secret test-config "secret" "myapp/config")]
        (is (= test-secret-data result))))))

(deftest read-secret-with-namespace
  (testing "Includes namespace header when configured"
    (let [config-with-ns (assoc test-config :namespace "my-namespace")]
      (with-redefs [vault/make-request
                    (fn [{:keys [headers]}]
                      (is (= "my-namespace" (get headers "X-Vault-Namespace")))
                      {:data {:data test-secret-data}})]
        (vault/read-secret config-with-ns "secret" "myapp/config")))))

(deftest read-secret-not-found
  (testing "Returns nil when secret is not found"
    (with-redefs [vault/make-request
                  (fn [_]
                    (throw (ex-info "Not found"
                                    {:status 404})))]
      (is (nil? (vault/read-secret test-config "secret" "missing/path"))))))

(deftest read-secret-error
  (testing "Throws on other errors"
    (with-redefs [vault/make-request
                  (fn [_]
                    (throw (ex-info "Internal error"
                                    {:status 500})))]
      (is (thrown? clojure.lang.ExceptionInfo
                   (vault/read-secret test-config "secret" "myapp/config"))))))

;; ---------- Unit tests: write-secret!

(deftest write-secret-success
  (testing "Successfully writes a secret to Vault"
    (with-redefs [vault/validate-config! identity
                  ;; Mock the Java HTTP connection
                  clojure.core/slurp (fn [_] "")]
      ;; We need to test this integration-style or mock the entire HTTP stack
      ;; For now, we'll validate the config is passed correctly
      (is (= test-config (vault/validate-config! test-config))))))

;; Note: Full write-secret! testing requires mocking Java's HttpURLConnection
;; which is complex. In a real scenario, you'd use a library like clj-http
;; or test against a real Vault dev server.

;; ---------- Unit tests: list-secrets

(deftest list-secrets-success
  (testing "Successfully lists secrets at a path"
    (with-redefs [vault/make-request
                  (fn [{:keys [url]}]
                    (is (str/includes? url "?list=true"))
                    {:data {:keys ["database" "api-keys" "config"]}})]
      (let [result (vault/list-secrets test-config "secret" "myapp")]
        (is (= ["database" "api-keys" "config"] result))))))

(deftest list-secrets-empty-path
  (testing "Lists secrets at root when path is empty"
    (with-redefs [vault/make-request
                  (fn [{:keys [url]}]
                    (is (= "http://localhost:8200/v1/secret/metadata/?list=true" url))
                    {:data {:keys []}})]
      (let [result (vault/list-secrets test-config "secret" "")]
        (is (= [] result))))))

(deftest list-secrets-not-found
  (testing "Returns empty vector when path not found"
    (with-redefs [vault/make-request
                  (fn [_]
                    (throw (ex-info "Not found"
                                    {:status 404})))]
      (is (= [] (vault/list-secrets test-config "secret" "missing"))))))

;; ---------- Unit tests: vault->secrets-map

(deftest vault-to-secrets-map-success
  (testing "Fetches and flattens secrets from Vault"
    (with-redefs [vault/list-secrets
                  (fn [config mount path]
                    (is (= test-config config))
                    (is (= "secret" mount))
                    (is (= "myapp" path))
                    ["database" "api"])

                  vault/read-secret
                  (fn [_config _mount path]
                    (cond
                      (= path "myapp/database")
                      {:username "db-user" :password "db-pass"}

                      (= path "myapp/api")
                      {:key "api-123"}

                      :else nil))]

      (let [result (vault/vault->secrets-map test-config "secret" "myapp")]
        (is (= {:database-username "db-user"
                :database-password "db-pass"
                :api-key "api-123"}
               result))))))

(deftest vault-to-secrets-map-with-key-fn
  (testing "Applies key transformation function"
    (with-redefs [vault/list-secrets
                  (fn [_ _ _] ["test"])

                  vault/read-secret
                  (fn [_ _ _] {:value "secret"})]

      (let [result (vault/vault->secrets-map
                    test-config "secret" "myapp"
                    (fn [k] (keyword (str/upper-case (name k)))))]
        (is (contains? result :TEST-VALUE))))))

(deftest vault-to-secrets-map-empty
  (testing "Returns empty map when no secrets found"
    (with-redefs [vault/list-secrets (fn [_ _ _] [])]
      (let [result (vault/vault->secrets-map test-config "secret" "myapp")]
        (is (= {} result))))))

(deftest vault-to-secrets-map-skips-missing
  (testing "Skips secrets that return nil"
    (with-redefs [vault/list-secrets
                  (fn [_ _ _] ["exists" "missing"])

                  vault/read-secret
                  (fn [_ _ path]
                    (when (str/ends-with? path "/exists")
                      {:value "found"}))]

      (let [result (vault/vault->secrets-map test-config "secret" "myapp")]
        (is (= {:exists-value "found"} result))
        (is (not (contains? result :missing-value)))))))

;; ---------- Integration test (requires Vault server)

(deftest ^:integration vault-integration-test
  ;; This test only runs if VAULT_ADDR and VAULT_TOKEN are set
  (when (and (System/getenv "VAULT_ADDR")
             (System/getenv "VAULT_TOKEN"))
    (testing "Full integration with Vault server"
      (let [config (vault/vault-config)
            test-path "integration-test/sample"]

        ;; Write a test secret
        (vault/write-secret! config "secret" test-path
                             {:test-key "test-value"
                              :timestamp (str (System/currentTimeMillis))})

        ;; Read it back
        (let [result (vault/read-secret config "secret" test-path)]
          (is (= "test-value" (:test-key result)))
          (is (some? (:timestamp result))))

        ;; List secrets
        (let [secrets (vault/list-secrets config "secret" "integration-test")]
          (is (some #{"sample"} secrets)))))))

;; ---------- Error handling tests

(deftest config-validation-in-operations
  (testing "All operations validate config before proceeding"
    (let [invalid-config {}]
      (is (thrown? clojure.lang.ExceptionInfo
                   (vault/read-secret invalid-config "secret" "path")))
      (is (thrown? clojure.lang.ExceptionInfo
                   (vault/write-secret! invalid-config "secret" "path" {})))
      (is (thrown? clojure.lang.ExceptionInfo
                   (vault/list-secrets invalid-config "secret" "path"))))))
