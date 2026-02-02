# Environment-Based Secret Management

## Overview

The secrets library now supports environment-specific secret management through the `ENV_NAME` environment variable. This feature enables seamless configuration across development, staging, and production environments.

## Features

### 1. Environment-Specific File Loading

When `ENV_NAME` is set, the library automatically loads environment-specific files:

```bash
export ENV_NAME=staging

# The library will now look for:
# - secrets.staging.edn
# - secrets.staging.edn.enc
# In addition to the default secrets.edn files
```

**File Priority Order** (when `ENV_NAME=staging`):

1. `~/secrets.edn` (home default)
2. `~/secrets.edn.enc` (home default encrypted)
3. `~/secrets.staging.edn` (home environment-specific) ⭐
4. `~/secrets.staging.edn.enc` (home environment-specific encrypted) ⭐
5. `./secrets.edn` (local default)
6. `./secrets.edn.enc` (local default encrypted)
7. `./secrets.staging.edn` (local environment-specific) ⭐
8. `./secrets.staging.edn.enc` (local environment-specific encrypted) ⭐
9. `SECRET__*` environment variables (generic)
10. `SECRET_STAGING__*` environment variables (environment-specific) ⭐

### 2. Environment-Specific Vault Paths

When `ENV_NAME` is set, all Vault operations automatically use environment-prefixed paths:

```clojure
;; Without ENV_NAME
(vault/read-secret config "secret" "pyjama/database")
;; => Reads from: secret/data/pyjama/database

;; With ENV_NAME=staging
(vault/read-secret config "secret" "pyjama/database")
;; => Reads from: secret/data/staging/pyjama/database
```

This applies to all Vault operations:
- `read-secret`
- `write-secret!`
- `list-secrets`
- `vault->secrets-map`

### 3. Environment-Specific Environment Variables

The library supports environment-specific environment variables:

```bash
# Generic (works in all environments)
export SECRET__API_KEY="default-key"

# Environment-specific (only when ENV_NAME=staging)
export SECRET_STAGING__API_KEY="staging-key"

# When ENV_NAME=staging, SECRET_STAGING__API_KEY takes precedence
```

**Variable Naming Convention:**
- Generic: `SECRET__<KEY>__<SUBKEY>`
- Environment-specific: `SECRET_<ENV>__<KEY>__<SUBKEY>`

## Implementation Details

### Files Plugin Changes

**File:** `src/secrets/plugins/files.clj`

Added:
- `get-env-name`: Retrieves `ENV_NAME` from environment
- Updated `env->secrets-map`: Checks for environment-specific variables (e.g., `SECRET_STAGING__*`)
- Updated `load-all-sources`: Loads environment-specific files (e.g., `secrets.staging.edn`)

### Vault Plugin Changes

**File:** `src/secrets/plugins/vault.clj`

Added:
- `get-env-name`: Retrieves `ENV_NAME` from environment
- `env-aware-path`: Prepends environment name to Vault paths
- Updated `read-secret`, `write-secret!`, `list-secrets`: All use `env-aware-path`

## Usage Examples

### Example 1: Development vs Production

```bash
# Development
export ENV_NAME=development
# Uses secrets.development.edn with local database

# Production
export ENV_NAME=production
# Uses secrets.production.edn with production database
```

### Example 2: CI/CD Pipelines

```bash
# In your CI/CD config
export ENV_NAME=staging
export SECRET_STAGING__DATABASE__PASSWORD="ci-db-pass"
# Automatically uses staging-specific secrets
```

### Example 3: Multi-tenant Applications

```bash
# Tenant A
export ENV_NAME=tenant-a
# Uses secrets.tenant-a.edn

# Tenant B
export ENV_NAME=tenant-b
# Uses secrets.tenant-b.edn
```

### Example 4: Feature Flags

```clojure
;; secrets.edn (default)
{:feature-x-enabled false}

;; secrets.staging.edn (test new features)
{:feature-x-enabled true}

;; Toggle by changing ENV_NAME
```

## Testing

### Run the Demo

```bash
clojure -M:env-demo
```

### Run the Test

```bash
# From test-resources directory
cd test-resources
ENV_NAME=staging clojure -Sdeps '{:paths ["../src"]}' -M -m secrets.examples.env-test
```

## Migration Guide

### For Existing Users

No changes required! The feature is backward compatible:

- If `ENV_NAME` is not set, behavior is identical to before
- Existing `secrets.edn` files continue to work
- No code changes needed

### To Adopt Environment-Based Secrets

1. **Set ENV_NAME** in your deployment environment:
   ```bash
   export ENV_NAME=staging
   ```

2. **Create environment-specific files**:
   ```bash
   # Copy your default secrets
   cp secrets.edn secrets.staging.edn
   
   # Edit staging-specific values
   vim secrets.staging.edn
   ```

3. **Or use environment variables**:
   ```bash
   export SECRET_STAGING__DATABASE__PASSWORD="staging-password"
   ```

4. **Or use Vault** with environment-specific paths:
   ```bash
   # Secrets automatically go to staging/pyjama/* when ENV_NAME=staging
   vault kv put secret/staging/pyjama/database username=admin password=secret
   ```

## Benefits

1. **Single Codebase**: Same code works across all environments
2. **Clear Separation**: Environment-specific secrets are clearly separated
3. **Flexible Override**: Multiple layers of override (files → env vars)
4. **Vault Integration**: Automatic environment-based path routing
5. **Backward Compatible**: Existing setups continue to work
6. **No Code Changes**: Pure configuration-based approach

## Security Considerations

1. **Never commit environment-specific files**: Add `secrets.*.edn` to `.gitignore`
2. **Use encrypted files for sensitive environments**: `secrets.production.edn.enc`
3. **Protect environment variables**: Use secure CI/CD secret management
4. **Vault for production**: Consider using Vault plugin for production environments
5. **Audit environment names**: Ensure ENV_NAME is set correctly in each environment
