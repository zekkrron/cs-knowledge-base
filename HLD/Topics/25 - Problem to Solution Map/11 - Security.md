---
tags: [hld/topics, hld/map, status/draft]
created: 2026-10-11
updated: 2026-10-11
---
# Security

> [!abstract] What this is
> Problems about **who can do what, and keeping data and systems safe**: authenticating users and services, tokens and their pitfalls, authorization models and where checks live, encryption and secrets, tenant isolation, abuse at the edge, and compliance-shaped constraints.

---

## Authentication

### How should callers authenticate?

| Caller | Option | Costs you | Pick when |
|---|---|---|---|
| Web users | **Server-side session** (id in cookie, state in Redis/DB) | Shared store; CSRF to handle | One web app; instant logout/revocation matters |
| Many services / mobile | **JWT** (signed claims, verified locally) | Hard to revoke; payload readable | Stateless verification across services |
| Third-party login | **OAuth2 + OIDC** via a managed IdP | Redirects, provider dependency | "Log in with Google", enterprise SSO |
| External developers | **API keys** | Leak in logs; rotate; pair with rate limits | Public APIs |
| Service to service | **OAuth2 client credentials** | Secret storage and rotation | Across teams or companies, with scopes |
| Internal services | **mTLS** (usually via a mesh) | Certificate issuance and rotation | Internal traffic in scope |

- **Placement:** authenticate once at the gateway; authorize in the service that owns the data.
- **Depth:** [[18 - Security]], [[03 - Networking and APIs]]

### Revoking access with stateless JWTs

| Option | Effect |
|---|---|
| **Short-lived access tokens (minutes) + revocable refresh token** | A ban takes effect within one access-token lifetime |
| **Refresh-token rotation** | Reuse of an old refresh token signals theft |
| **Denylist** | Instant, but reintroduces a lookup |

- **Depth:** [[18 - Security]]

### JWT validation mistakes

- **Rules:** pin the algorithm server-side (no `alg: none`), validate `iss`, `aud` and `exp`, put no secrets in the payload (signed, not encrypted), and avoid `localStorage` if XSS is possible (`HttpOnly` cookies move the risk to CSRF).
- **Depth:** [[18 - Security]]

### Secure login for SPAs and mobile apps

- **Fix:** authorization code flow + **PKCE** (an intercepted code is useless without the verifier). Send the ID token to the client, the access token to the API, never the ID token to an API.
- **Depth:** [[18 - Security]]

### Credential stuffing on login

- **Fixes:** rate limit per account and per IP, bot detection, MFA, breached-password checks. **Depth:** [[18 - Security]]

---

## Authorization

### Choosing an authorization model

| Model | Costs you | Pick when |
|---|---|---|
| **RBAC** (roles) | Roles multiply when rules depend on context | Internal tools, a few fixed roles |
| **ABAC** (attributes) | Hard to reason about and audit | Rules depend on department, region, amount, time |
| **ReBAC** (relationship tuples, Zanzibar / SpiceDB / OpenFGA) | A dedicated service | Sharing models: docs, folders, repos |

- **Depth:** [[18 - Security]]

### Where permission checks live (and IDOR)

- **Signal:** `GET /invoices/1234` checks login but not ownership.
- **Fix:** coarse checks at the gateway (token, route, scope) **plus** an ownership/permission check on every by-id read and write in the owning service. A policy engine (OPA) keeps rules consistent across many services.
- **Depth:** [[18 - Security]]

---

## Data Protection

### Encryption in transit and at rest

| Option | Costs you | Pick when |
|---|---|---|
| **TLS at the edge, plain HTTP inside the VPC** | Internal traffic unencrypted | Common default |
| **TLS everywhere / mTLS via mesh** | More ops | Internal traffic is in scope |
| **Encryption at rest** | Doesn't stop anyone with valid DB credentials | Stolen disks and backups |

- **Depth:** [[18 - Security]]

### Encrypting lots of data with centrally controlled keys

- **Fix:** **envelope encryption**: a local data key (DEK) encrypts the data, a KMS master key (KEK) encrypts the DEK, both stored together. Rotating the master key re-encrypts only DEKs. Least-privilege IAM on KMS, audited decrypts, separate keys per service or tenant.
- **Depth:** [[18 - Security]]

### Storing secrets

| Option | Costs you | Pick when |
|---|---|---|
| ~~Hard-coded or `.env` in git~~ | Leaked forever in history | Never |
| **Env vars injected from a secret manager at deploy** | Readable by anything that reads the env | Acceptable default |
| **Fetched from a secret manager at runtime** (Vault, cloud managers) | Manager must be up at startup | Preferred; rotation and audit |
| **Dynamic secrets** (short-lived per-service DB credentials) | Vault setup | Auto-revoked credentials |

- **Also:** pre-commit secret scanning; rotate immediately on a leak.
- **Depth:** [[18 - Security]], [[19 - Deployment and Infrastructure]]

### Untrusted input reaching SQL, HTML or a shell

- **Fix:** parameterised queries, allow-list validation at the boundary, context-aware output encoding. **Depth:** [[18 - Security]]

---

## Multi-Tenancy

### Keeping SaaS tenants isolated

| Model | Costs you | Pick when |
|---|---|---|
| **Silo** (DB or stack per tenant) | Expensive; many deploys | Large enterprise or compliance-driven tenants |
| **Pool** (shared tables, `tenant_id` on every row) | One query bug leaks data; noisy neighbours | Many small tenants; SaaS default |
| **Bridge** (pool small, silo big; schema per tenant) | A routing layer | Mixed tenant sizes |

- **Pool safety:** take the tenant from the token, never the request; one shared data-access layer; Postgres row-level security; tenant in every cache key and search filter; per-tenant rate limits and quotas.
- **Depth:** [[18 - Security]]

---

## Abuse and the Edge

### DDoS and volumetric floods

- **Fix:** absorb at the CDN/edge (Cloudflare, AWS Shield, CloudFront), WAF + rate limits, and never expose the origin IP. You can't out-provision a flood.
- **Depth:** [[18 - Security]]

### Scraping, bad bots, abusive clients

| Option | Pick when |
|---|---|
| **Token bucket per user/key/IP at the gateway**, 429 + `Retry-After` | Every public API |
| **WAF** (managed rules, IP/geo blocks) | Public web apps; a filter, not a fix |
| **Bot protection** (CAPTCHA, fingerprinting, behaviour scoring) | Login, signup, checkout, ticket and flash-sale flows |

- **Depth:** [[18 - Security]], [[08 - Overload and Failure Handling]]

### Cross-origin browser access (CORS) and forged requests (CSRF)

| Problem | Fix |
|---|---|
| **SPA on another origin** | CORS allow-list of trusted origins; never `*` with credentials or a reflected origin. CORS protects browsers, not your API |
| **CSRF with cookie sessions** | `SameSite=Lax/Strict`, CSRF tokens, check `Origin` on state-changing requests; or bearer tokens in a header (moves risk to XSS) |

- **Depth:** [[18 - Security]]

---

## Compliance-Shaped Constraints

### Handling PII

- **Rules:** store only what's needed in a few owned stores; scrub logs; pseudonymise downstream; mask PII before it lands in the warehouse (ETL rather than ELT for those fields).
- **Depth:** [[18 - Security]], [[20 - Data Processing and Analytics]]

### Right to deletion across every copy

| Option | Pick when |
|---|---|
| **Registry of PII stores + a deletion event every store consumes** | Replicas, caches, search, warehouse, Kafka |
| **Crypto-shredding** (per-user key; delete the key) | Backups you can't edit |

- **Depth:** [[18 - Security]]

### Card payments without PCI scope

- **Fix:** the card form posts straight to the provider (Stripe, Adyen), which returns a token you store and charge; raw card numbers never touch your servers.
- **Depth:** [[18 - Security]]

### Tamper-proof record of sensitive actions

- **Fix:** an append-only audit log in a store separate from the app database, retained per policy (admin access, permission changes, exports).
- **Depth:** [[18 - Security]]

## Related Notes

- [[25 - Problem to Solution Map]]
- [[18 - Security]] · [[03 - Networking and APIs]] · [[04 - Load Balancing CDN and Gateway]]
