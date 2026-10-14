---
tags: [languages/python, status/draft]
created: 2026-10-14
---
# Python Packaging and Environments

> [!abstract] What this is
> How Python projects declare, resolve, lock and ship dependencies: **`pyproject.toml`** (PEP 621 metadata, build backends), **virtual environments**, **wheels vs sdists** and platform tags, the tools (**uv**, pip + pip-tools, Poetry, PDM), **lockfiles** (tool-specific and the standard `pylock.toml`, PEP 751), dependency groups, building **Docker images**, and supply-chain hygiene. The decision this helps with: setting up a reproducible project and image, and fixing "works on my machine" and dependency-conflict problems. The Java equivalent is [[14 - Java Build and Dependencies]].

---

## Why It Matters

- Python's packaging history (setup.py, requirements.txt, eggs, conda, Poetry) left many teams with unreproducible builds: unpinned transitive dependencies, different results on Mac and Linux, wheels missing for a platform.
- Since 2024, **uv** has become the default choice for many teams: one fast tool for Python versions, environments, resolution, locking and running. In March 2026 OpenAI announced it was acquiring Astral, the maker of uv, Ruff and ty; both sides committed to keeping the tools open source.

---

## pyproject.toml

```toml
[project]
name = "orders-service"
version = "1.4.0"
requires-python = ">=3.13"
dependencies = [
    "fastapi>=0.120",
    "sqlalchemy[asyncio]>=2.0",
    "asyncpg>=0.30",
    "httpx>=0.28",
]

[project.optional-dependencies]          # extras: pip install "orders-service[otel]"
otel = ["opentelemetry-distro"]

[dependency-groups]                       # PEP 735: dev-only groups, not published
dev = ["pytest>=8", "pytest-asyncio", "mypy", "ruff"]

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[tool.ruff]
line-length = 100
```

- **`[project]`** (PEP 621): name, version, Python range, dependencies, extras, entry points (`[project.scripts]`).
- **`[build-system]`** names the **build backend** (hatchling, setuptools, flit-core, pdm-backend, `uv_build`): it turns the source tree into a wheel. `setup.py` is only needed for legacy or custom build logic.
- **Applications vs libraries**: libraries declare **ranges** (`>=2.0,<3`) so users can resolve; applications **lock** exact versions of everything.
- Tool configuration (ruff, mypy, pytest, coverage) lives in `[tool.*]` tables.

---

## Environments

- A **virtual environment** is a directory with its own `site-packages` and a link to a base interpreter: per-project dependency isolation. Never install project dependencies into the system Python (distros mark it "externally managed", PEP 668).
- **Python version management**: `uv python install 3.15`, pyenv, or the official installers; pin with `.python-version` or `requires-python`.
- **Free-threaded interpreters** are separate installations (`uv python install 3.14t`) with their own environments ([[03 - The GIL and Free-Threaded Python]]).

---

## Wheels, sdists and Platform Tags

| Artifact | What | Install cost |
|---|---|---|
| **Wheel** (`.whl`) | Pre-built archive, unpacked into site-packages | Fast; no compiler |
| **sdist** (`.tar.gz`) | Source; built locally by the backend at install time | Needs compilers and headers for C/Rust extensions; slow; can fail |

- Wheel filenames encode compatibility: `numpy-2.3.0-cp314-cp314-manylinux_2_28_x86_64.whl` = CPython 3.14 ABI, glibc ≥ 2.28, x86-64. **`musllinux`** wheels are for Alpine; **`cp314t`** for free-threaded 3.14; **`abi3`** for the stable ABI across versions, and **`abi3t`** (3.15, PEP 803) for free-threaded stable ABI.
- If no wheel matches the platform, the installer falls back to the sdist: the "why is Docker building NumPy from source" problem (often Alpine/musl, or a brand-new Python version before wheels are published).

---

## Tools

| Tool | Does | Notes |
|---|---|---|
| **uv** | Python installs, venvs, resolution, `uv.lock`, `uv sync`, `uv run`, `uv tool`, `uv build`/`publish`; pip-compatible interface (`uv pip`) | Very fast (Rust), cross-platform lockfile, de facto default for new projects |
| **pip** | Installer | No lockfile on its own; `pip install -r requirements.txt` |
| **pip-tools** | `pip-compile` → fully pinned `requirements.txt` with hashes | Simple, widely understood |
| **Poetry** | Resolution, `poetry.lock`, publishing | Popular; PEP 621 `[project]` support since Poetry 2 |
| **PDM, Hatch** | Project managers / build tools | Standards-oriented |
| **conda / pixi** | Non-Python binaries too (CUDA, compilers, GDAL) | Data science and native-heavy stacks |

```bash
uv init orders-service && cd orders-service
uv add fastapi "sqlalchemy[asyncio]" asyncpg
uv add --dev pytest mypy ruff
uv lock                      # resolve and write uv.lock (all platforms)
uv sync --frozen             # install exactly what the lock says; fail if pyproject and lock disagree
uv run pytest
```

### Lockfiles

- A lockfile pins **every** package (including transitive ones) to exact versions and **hashes**, for every target platform: the same install on laptops, CI and production.
- Tool formats: `uv.lock`, `poetry.lock`, `pdm.lock`; compiled `requirements.txt` with `--hash` lines.
- **`pylock.toml`** (PEP 751, accepted 2025) is the **standard lockfile format**, so lockfiles can be consumed across tools (uv can export it).
- Commit the lockfile for applications; update deliberately (`uv lock --upgrade-package httpx`) with automated PRs (Renovate/Dependabot).

### Dependency conflicts

- Resolvers **backtrack** to find versions satisfying all constraints; failures mean two packages need incompatible versions of a third (`pydantic<2` vs `pydantic>=2`).
- Diagnose with the resolver's explanation (`uv` prints the conflict chain), `uv tree` / `pipdeptree`. Fix by upgrading the outdated dependent, using overrides (`[tool.uv] override-dependencies`) as a last resort, or splitting tools into separate environments (`uv tool`, pipx).
- Tight **upper bounds** in libraries (`<2.0` everywhere) cause unnecessary conflicts for users; applications get safety from the lockfile instead.

---

## Docker Images

```dockerfile
FROM python:3.15-slim AS build
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv   # pin a specific uv version in real builds
WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project          # dependency layer, cached unless the lock changes
COPY src ./src
RUN uv sync --frozen --no-dev

FROM python:3.15-slim
WORKDIR /app
COPY --from=build /app /app
ENV PATH="/app/.venv/bin:$PATH"
USER 10001
CMD ["uvicorn", "orders.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

- Copy the lock first so dependency layers cache; install the project afterwards.
- **`-slim`** (glibc) over **Alpine** (musl): manylinux wheels work on slim; Alpine often forces source builds and has DNS/allocator differences.
- Compile bytecode at build time for faster start; run as non-root; no compilers in the final stage.
- Exec-form `CMD` so the server receives SIGTERM ([[06 - WSGI vs ASGI and Python Servers]]).

---

## Supply-Chain Hygiene

- Pin and **hash** everything (lockfiles do); install with `--frozen`/`--require-hashes` in CI and images.
- Scan for known vulnerabilities: `pip-audit`, `uv` audit integrations, Dependabot, Trivy on images; generate SBOMs.
- Beware **typosquatting** (`reqeusts`) and **dependency confusion** (internal names on public PyPI): use a private index with explicit index configuration per package.
- Publishing: **Trusted Publishing** (OIDC from CI to PyPI) instead of long-lived API tokens; PyPI supports attestations.
- Single-file scripts declare dependencies inline (**PEP 723**): `uv run script.py` builds an ephemeral environment.

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Unpinned transitive dependencies** | Builds change without code changes; surprise breakages | Lockfile; `--frozen` installs |
| **Lock and pyproject out of sync** | CI installs something else than intended | `uv sync --frozen` / `--locked` fails fast |
| **Alpine base image** | Source builds, slow images, musl issues | `-slim`, or musllinux wheels explicitly |
| **New Python before wheels exist** | Builds from sdists fail | Wait for wheels of heavy deps; test RCs in CI |
| **Installing into system Python** | Breaks OS tools; conflicts | Virtual environments always |
| **Library with tight upper bounds** | Users can't resolve | Lower bounds plus tested ranges |
| **Typosquatted/confused package** | Malicious code runs at install or import | Review new deps, private index config, hashes |
| **Dev dependencies in production images** | Bigger attack surface and image size | `--no-dev`, multi-stage builds |

---

## Interview Lines

> [!tip] Say it like this
> - "Projects declare metadata and dependencies in `pyproject.toml`; libraries use ranges, applications lock every transitive version with hashes."
> - "I use uv: it manages Python versions, venvs, resolution and a cross-platform lockfile, and `uv sync --frozen` installs exactly the lock in CI and Docker."
> - "Wheels are pre-built and tagged by Python ABI and platform; when no wheel matches, pip builds from source, which is why Alpine images are slow."
> - "PEP 751 standardised the lockfile as `pylock.toml`, and PEP 735 added dependency groups for dev tools."
> - "Images: slim base, dependencies layer from the lockfile first, no dev deps, non-root, exec-form entrypoint."

## Related Notes

- [[00 - Python Version History]] · [[06 - WSGI vs ASGI and Python Servers]] · [[12 - Python Testing with pytest]]
- [[14 - Java Build and Dependencies]] · [[10 - Go Modules Build and Tooling]] · [[Languages/README]]
