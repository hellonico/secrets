(ns secrets.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [secrets.core :as sut]))

;; ---------- Test fixtures

(defn reset-plugins-fixture
  "Reset plugins to default state before each test"
  [f]
  (sut/init-default-plugins!)
  (f))

(use-fixtures :each reset-plugins-fixture)

;; ---------- Plugin Management Tests

(deftest register-plugin-success
  (testing "Can register a new plugin"
    (let [plugin {:name :test
                  :description "Test plugin"
                  :get-fn (fn [_] "test-value")}]
      (sut/register-plugin! plugin)
      (is (= 2 (count (sut/list-plugins))))
      (is (some #(= :test (:name %)) (sut/list-plugins))))))

(deftest register-plugin-requires-name
  (testing "Plugin registration requires :name"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/register-plugin! {:get-fn (fn [_] nil)})))))

(deftest register-plugin-requires-get-fn
  (testing "Plugin registration requires :get-fn"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/register-plugin! {:name :test})))))

(deftest unregister-plugin-removes-by-name
  (testing "Can unregister a plugin by name"
    (let [plugin {:name :test :get-fn (fn [_] nil)}]
      (sut/register-plugin! plugin)
      (is (some #(= :test (:name %)) (sut/list-plugins)))
      (sut/unregister-plugin! :test)
      (is (not (some #(= :test (:name %)) (sut/list-plugins)))))))

(deftest get-plugin-by-name
  (testing "Can retrieve a plugin by name"
    (let [plugin (sut/get-plugin :files)]
      (is (some? plugin))
      (is (= :files (:name plugin)))
      (is (= "File and environment-based secrets" (:description plugin))))))

(deftest list-plugins-shows-default
  (testing "Default plugins are registered on init"
    (is (= 1 (count (sut/list-plugins))))
    (is (= :files (:name (first (sut/list-plugins)))))))

;; ---------- Secret Lookup Tests

(deftest get-secret-checks-plugins-in-order
  (testing "get-secret checks plugins in registration order"
    (let [plugin1 {:name :p1
                   :get-fn (fn [k] (when (= k :key1) "value1"))}
          plugin2 {:name :p2
                   :get-fn (fn [k] (when (= k :key2) "value2"))}]
      (sut/init-default-plugins!)
      (sut/register-plugin! plugin1)
      (sut/register-plugin! plugin2)
      (is (= "value1" (sut/get-secret :key1)))
      (is (= "value2" (sut/get-secret :key2))))))

(deftest get-secret-first-match-wins
  (testing "First non-nil result wins"
    (let [plugin1 {:name :p1
                   :get-fn (fn [k] (when (= k :shared) "first"))}
          plugin2 {:name :p2
                   :get-fn (fn [_] "second")}]
      (sut/init-default-plugins!)
      (sut/register-plugin! plugin1)
      (sut/register-plugin! plugin2)
      (is (= "first" (sut/get-secret :shared))))))

(deftest get-secret-returns-nil-when-not-found
  (testing "Returns nil when no plugin provides the secret"
    (sut/init-default-plugins!)
    (is (nil? (sut/get-secret :nonexistent-key-xyz)))))

(deftest get-secret-with-source-includes-plugin-name
  (testing "get-secret-with-source returns value and source plugin"
    (let [plugin {:name :test
                  :get-fn (fn [k] (when (= k :test-key) "test-value"))}]
      (sut/init-default-plugins!)
      (sut/register-plugin! plugin)
      (let [result (sut/get-secret-with-source :test-key)]
        (is (= "test-value" (:value result)))
        (is (= :test (:source result)))))))

(deftest get-secret-with-source-returns-nil-when-not-found
  (testing "get-secret-with-source returns nil when not found"
    (sut/init-default-plugins!)
    (is (nil? (sut/get-secret-with-source :nonexistent)))))

;; ---------- Bulk Operations Tests

(deftest all-secrets-merges-from-all-plugins
  (testing "all-secrets merges results from all plugins"
    (let [plugin1 {:name :p1
                   :get-fn (fn [_] nil)
                   :all-fn (fn [] {:key1 "value1" :shared "p1"})}
          plugin2 {:name :p2
                   :get-fn (fn [_] nil)
                   :all-fn (fn [] {:key2 "value2" :shared "p2"})}]
      (sut/init-default-plugins!)
      (sut/register-plugin! plugin1)
      (sut/register-plugin! plugin2)
      (let [all (sut/all-secrets)]
        (is (= "value1" (:key1 all)))
        (is (= "value2" (:key2 all)))
        ;; Later plugin wins on conflicts
        (is (= "p2" (:shared all)))))))

(deftest reload-secrets-calls-all-reload-fns
  (testing "reload-secrets! calls reload-fn on all plugins that support it"
    (let [reloaded (atom {:p1 false :p2 false})
          plugin1 {:name :p1
                   :get-fn (fn [_] nil)
                   :reload-fn (fn [] (swap! reloaded assoc :p1 true) {:p1 :reloaded})}
          plugin2 {:name :p2
                   :get-fn (fn [_] nil)
                   :reload-fn (fn [] (swap! reloaded assoc :p2 true) {:p2 :reloaded})}]
      (sut/init-default-plugins!)
      (sut/register-plugin! plugin1)
      (sut/register-plugin! plugin2)
      (let [result (sut/reload-secrets!)]
        (is (:p1 @reloaded))
        (is (:p2 @reloaded))
        (is (= {:p1 :reloaded} (:p1 result)))
        (is (= {:p2 :reloaded} (:p2 result)))))))

(deftest reload-secrets-skips-plugins-without-reload-fn
  (testing "reload-secrets! skips plugins without reload-fn"
    (let [plugin {:name :no-reload
                  :get-fn (fn [_] nil)}]
      (sut/init-default-plugins!)
      (sut/register-plugin! plugin)
      (let [result (sut/reload-secrets!)]
        (is (not (contains? result :no-reload)))))))

;; ---------- Integration with Default Files Plugin

(deftest get-secret-works-with-files-plugin
  (testing "get-secret works with default files plugin"
    ;; The files plugin will check env vars as fallback
    ;; We'll test that the integration works
    (is (some? (sut/get-plugin :files)))
    ;; Getting a non-existent secret should return nil
    (is (nil? (sut/get-secret :definitely-not-a-real-secret-key-xyz)))))

;; ---------- Backwards Compatibility

(deftest write-encrypted-secrets-delegates-to-files
  (testing "write-encrypted-secrets! delegates to files plugin for backwards compatibility"
    ;; This just verifies the function exists and is callable
    ;; Actual encryption testing is in files plugin tests
    (is (fn? sut/write-encrypted-secrets!))))
