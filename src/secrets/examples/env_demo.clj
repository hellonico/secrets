(ns secrets.examples.env-demo
  "Demonstrates environment-based secret management.
   
   This example shows how ENV_NAME affects:
   - File loading (secrets.staging.edn)
   - Vault paths (pyjama/staging)
   - Environment variable expansion (SECRET_STAGING__*)"
  (:require [secrets.core :as secrets]
            [secrets.plugins.vault :as vault]))

(defn demo-file-loading
  "Demonstrates environment-specific file loading."
  []
  (println "\n=== File Loading Demo ===\n")

  (println "Current ENV_NAME:" (or (System/getenv "ENV_NAME") "(not set)"))
  (println)

  (println "When ENV_NAME is set to 'staging', the library will look for:")
  (println "  1. ~/secrets.edn (default)")
  (println "  2. ~/secrets.edn.enc (default encrypted)")
  (println "  3. ~/secrets.staging.edn (environment-specific)")
  (println "  4. ~/secrets.staging.edn.enc (environment-specific encrypted)")
  (println "  5. ./secrets.edn (local default)")
  (println "  6. ./secrets.edn.enc (local default encrypted)")
  (println "  7. ./secrets.staging.edn (local environment-specific)")
  (println "  8. ./secrets.staging.edn.enc (local environment-specific encrypted)")
  (println "  9. SECRET__* env vars (generic)")
  (println " 10. SECRET_STAGING__* env vars (environment-specific)")
  (println)

  (println "Example setup:")
  (println "  # secrets.edn (default)")
  (println "  {:api-key \"default-key\"")
  (println "   :database {:host \"localhost\"}}")
  (println)
  (println "  # secrets.staging.edn (overrides for staging)")
  (println "  {:api-key \"staging-key\"")
  (println "   :database {:host \"staging.example.com\"}}")
  (println)

  (println "Result when ENV_NAME=staging:")
  (println "  (secrets/get-secret :api-key)")
  (println "  => \"staging-key\"  ; from secrets.staging.edn")
  (println)
  (println "  (secrets/get-secret [:database :host])")
  (println "  => \"staging.example.com\"  ; from secrets.staging.edn"))

(defn demo-env-variables
  "Demonstrates environment-specific environment variables."
  []
  (println "\n=== Environment Variables Demo ===\n")

  (println "Generic environment variables (work in all environments):")
  (println "  export SECRET__API_KEY=\"default-key\"")
  (println "  => Maps to :api-key")
  (println)

  (println "Environment-specific variables (only when ENV_NAME matches):")
  (println "  export SECRET_STAGING__API_KEY=\"staging-key\"")
  (println "  => Maps to :api-key when ENV_NAME=staging")
  (println)

  (println "Priority order:")
  (println "  1. SECRET_STAGING__API_KEY (highest, when ENV_NAME=staging)")
  (println "  2. SECRET__API_KEY (generic fallback)")
  (println "  3. File-based secrets (lowest)")
  (println)

  (println "Example:")
  (println "  # Set both variables")
  (println "  export SECRET__API_KEY=\"default-key\"")
  (println "  export SECRET_STAGING__API_KEY=\"staging-key\"")
  (println "  export ENV_NAME=staging")
  (println)
  (println "  (secrets/get-secret :api-key)")
  (println "  => \"staging-key\"  ; environment-specific wins"))

(defn demo-vault-paths
  "Demonstrates environment-specific Vault paths."
  []
  (println "\n=== Vault Paths Demo ===\n")

  (println "Without ENV_NAME:")
  (println "  (vault/read-secret config \"secret\" \"pyjama/database\")")
  (println "  => Reads from: secret/data/pyjama/database")
  (println)

  (println "With ENV_NAME=staging:")
  (println "  (vault/read-secret config \"secret\" \"pyjama/database\")")
  (println "  => Reads from: secret/data/staging/pyjama/database")
  (println)

  (println "This allows you to organize Vault secrets by environment:")
  (println "  secret/data/staging/pyjama/database")
  (println "  secret/data/production/pyjama/database")
  (println "  secret/data/development/pyjama/database")
  (println)

  (println "All Vault operations are environment-aware:")
  (println "  - read-secret")
  (println "  - write-secret!")
  (println "  - list-secrets")
  (println "  - vault->secrets-map"))

(defn demo-use-cases
  "Shows practical use cases for environment-based secrets."
  []
  (println "\n=== Practical Use Cases ===\n")

  (println "1. Development vs Production:")
  (println "   # Development")
  (println "   export ENV_NAME=development")
  (println "   # Uses secrets.development.edn with local database")
  (println)
  (println "   # Production")
  (println "   export ENV_NAME=production")
  (println "   # Uses secrets.production.edn with production database")
  (println)

  (println "2. CI/CD Pipelines:")
  (println "   # In your CI/CD config")
  (println "   export ENV_NAME=staging")
  (println "   export SECRET_STAGING__DATABASE__PASSWORD=\"ci-db-pass\"")
  (println "   # Automatically uses staging-specific secrets")
  (println)

  (println "3. Multi-tenant Applications:")
  (println "   # Tenant A")
  (println "   export ENV_NAME=tenant-a")
  (println "   # Uses secrets.tenant-a.edn")
  (println)
  (println "   # Tenant B")
  (println "   export ENV_NAME=tenant-b")
  (println "   # Uses secrets.tenant-b.edn")
  (println)

  (println "4. Feature Flags:")
  (println "   # secrets.edn (default)")
  (println "   {:feature-x-enabled false}")
  (println)
  (println "   # secrets.staging.edn (test new features)")
  (println "   {:feature-x-enabled true}")
  (println)
  (println "   # Toggle by changing ENV_NAME"))

(defn -main
  "Run all demos."
  [& _args]
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║  Environment-Based Secret Management Demo                 ║")
  (println "╚════════════════════════════════════════════════════════════╝")

  (demo-file-loading)
  (demo-env-variables)
  (demo-vault-paths)
  (demo-use-cases)

  (println "\n=== Summary ===\n")
  (println "The ENV_NAME environment variable enables:")
  (println "  ✓ Environment-specific file loading")
  (println "  ✓ Environment-specific Vault paths")
  (println "  ✓ Environment-specific environment variables")
  (println)
  (println "This provides a unified way to manage secrets across")
  (println "development, staging, and production environments.")
  (println))
