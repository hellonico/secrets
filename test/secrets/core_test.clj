(ns secrets.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [secrets.core :as sut])
  (:import [java.nio.file Files]
           [java.util UUID]
           [javax.crypto AEADBadTagException]))

;; ---------- helpers

(defn tmp-dir []
  (.toFile (Files/createTempDirectory "secrets-core-test-" (make-array java.nio.file.attribute.FileAttribute 0))))

(defn read-bytes [f]
  (with-open [in (io/input-stream f)]
    (.readAllBytes in)))

(defn write-bytes! [f ^bytes bs]
  (when-let [p (.getParentFile (io/file f))] (.mkdirs p))
  (with-open [out (io/output-stream f)]
    (.write out bs))
  f)

;; ---------- unit: deep-merge

(deftest deep-merge-rightmost-wins
  (let [a {:a 1 :m {:x 1 :z 0}}
        b {:a 2 :m {:y 2}}
        c {:m {:x :over :y :over2} :c 3}]
    (is (= {:a 2
            :m {:x :over :y :over2 :z 0}
            :c 3}
           (#'sut/deep-merge a b c)))))

;; ---------- unit: env-name

(deftest env-name-conversion
  (is (= "BRAVE_API_KEY" (#'sut/env-name [:brave :api-key])))
  (is (= "FOO_BAR" (#'sut/env-name :foo-bar)))
  (is (= "A__B__C" (#'sut/env-name [:a "-b-" :c]))))

;; ---------- unit: crypto (AES-GCM + PBKDF2)

(deftest encrypt-decrypt-roundtrip
  (let [pass "test-pass"
        plain (.getBytes (str "hello-" (UUID/randomUUID)) "UTF-8")
        ct (#'sut/encrypt-bytes pass plain)
        dec (#'sut/decrypt-bytes pass ct)]
    (is (not= (seq plain) (seq ct)))
    (is (= (seq plain) (seq dec)))))

(deftest decrypt-with-wrong-pass-throws
  (let [plain (.getBytes "secret" "UTF-8")
        ct (#'sut/encrypt-bytes "correct" plain)]
    (is (thrown? AEADBadTagException
                 (#'sut/decrypt-bytes "wrong" ct)))))

(deftest read-encrypted-edn-roundtrip
  (let [dir (tmp-dir)
        f   (io/file dir "round.edn.enc")
        pass "demo-pass"
        m {:a 1 :nested {:x "y"}}
        bytes (#'sut/encrypt-bytes pass (.getBytes (pr-str m) "UTF-8"))]
    (write-bytes! f bytes)
    (is (= m (#'sut/read-encrypted-edn (.getPath f) pass)))))

;; ---------- integration-lite: write-encrypted-secrets! (conditional)

(deftest ^:integration write-encrypted-secrets-conditional
  ;; Only runs if SECRETS_PASSPHRASE is set in the real env.
  (when-let [pp (System/getenv "SECRETS_PASSPHRASE")]
    (let [dir (tmp-dir)
          f   (io/file dir "wrote.enc")
          m   {:foo "bar" :n 42}]
      (sut/write-encrypted-secrets! (.getPath f) m)
      (is (.exists f))
      (is (= m (#'sut/read-encrypted-edn (.getPath f) pp))))))

;; ---------- sources: priority via stubbing

(deftest load-all-sources-priority
  ;; Priority: home → local → env, with env winning on conflicts; merge is deep.
  (with-redefs [sut/load-edn-file
                (fn [path]
                  (cond
                    (= path "secrets.edn")
                    {:local 1 :m {:x 1}}

                    ;; tolerate absolute paths and both / and \ separators
                    (and (string? path)
                         (or (str/ends-with? path "/secrets.edn")
                             (str/ends-with? path "\\secrets.edn")))
                    {:home 1 :m {:y 2}}

                    :else nil))

                sut/maybe-read-encrypted
                (fn [path]
                  (cond
                    (= path "secrets.edn.enc")
                    {:locale 10 :m {:x :enc-local}}

                    (and (string? path)
                         (or (str/ends-with? path "/secrets.edn.enc")
                             (str/ends-with? path "\\secrets.edn.enc")))
                    {:homee 20 :m {:y :enc-home}}

                    :else nil))

                sut/env->secrets-map
                (fn [] {:env 99 :m {:x :env-x :y :env-y}})]
    (is (= {:home 1
            :homee 20
            :local 1
            :locale 10
            :env 99
            :m {:x :env-x
                :y :env-y}}
           (#'sut/load-all-sources)))))


;; ---------- state flow (public API only)

(deftest reload-secrets-updates-and-all-secrets-reflects
  (with-redefs [sut/load-all-sources (fn [] {:new 2})]
    (is (= {:new 2} (sut/reload-secrets!)))
    (is (= {:new 2} (sut/all-secrets)))))

;; ---------- lookup: get-secret (prime via reload-secrets!)

(deftest get-secret-prefers-merged
  (with-redefs [sut/load-all-sources (fn [] {:brave {:api-key "123"} :flat "ok"})]
    (sut/reload-secrets!)
    (is (= "123" (sut/get-secret [:brave :api-key])))
    (is (= "ok" (sut/get-secret :flat)))))

(deftest get-secret-missing-returns-nil-when-no-env
  (with-redefs [sut/load-all-sources (fn [] {})]
    (sut/reload-secrets!)
    (is (nil? (sut/get-secret [:no-such :path])))))
