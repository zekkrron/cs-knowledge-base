---
tags: [languages/python, status/draft]
created: 2026-10-14
---
# Python Version History

> [!abstract] What this is
> The Python releases that matter and what each brought, from the 2 → 3 break to **3.15** (Oct 2026): the annual release cycle and support windows, the async/typing milestones, the **"Faster CPython"** era (3.11+), and the recent runtime work (experimental JIT, subinterpreters, the optional **free-threaded** build). Default CPython builds **still have the GIL**; free-threading is a separate, opt-in build. The decision this helps with: knowing what a codebase on a given version can use, and answering "what changed in 3.11 / 3.12 / 3.14?" precisely.

---

## Why It Matters

- Python services pin a version in images and lockfiles; upgrades are planned around the October release and the libraries' support for it (NumPy, PyTorch, Pydantic wheels).
- Interview questions are often version questions in disguise: `match` (3.10), `ExceptionGroup` and `TaskGroup` (3.11), the new generic syntax (3.12), free-threading (3.13/3.14), lazy imports (3.15).

---

## Release Model

- **One feature release per year, in October** (PEP 602, since 3.9).
- Support per release: **2 years of bugfix releases** (with binary installers), then **3 years of security-only** source releases: **~5 years total** (bugfix period lengthened to 2 years from 3.13 on).
- Status in Oct 2026: **3.15** and **3.14** in bugfix; **3.13–3.11** security-only; **3.10** reaching end of life (Oct 2026); 3.9 and older are EOL.
- Alphas → betas (feature freeze in May) → release candidates (Aug–Sep) → final (Oct). Wheels for major libraries typically follow within weeks to months.
- Calendar versioning (PEP 2026, "3.26 in 2026") was proposed but not adopted: versions stay 3.N.

---

## The Big Eras

### Python 3.0 (2008) and the end of Python 2

- `str` is Unicode, `bytes` separate; `print()` function; integer division `/` returns float; iterators instead of lists (`range`, `dict.items()`).
- **Python 2.7 reached end of life in Jan 2020.**

### 3.5–3.8 (2015–2019) — async, typing and modern syntax

| Version | Highlights |
|---|---|
| **3.5** | **`async`/`await`** syntax (PEP 492), **type hints** (PEP 484, `typing`), `@` matrix multiplication operator |
| **3.6** | **f-strings**, variable annotations, `secrets`, async generators; dicts keep insertion order (implementation detail) |
| **3.7** | Dict ordering **guaranteed**, **`dataclasses`**, **`contextvars`**, `asyncio.run`, `breakpoint()` |
| **3.8** | Walrus `:=`, positional-only parameters (`/`), `f"{x=}"` debugging, `Protocol`, `TypedDict`, `Final`, `Literal` |

### 3.9–3.10 (2020–2021)

| Version | Highlights |
|---|---|
| **3.9** | `dict` union (`|`), built-in generics (`list[int]`), `zoneinfo`, new **PEG parser**, annual cadence begins |
| **3.10** | **Structural pattern matching** (`match`/`case`), `X | Y` union types, `ParamSpec`, much better error messages |

### 3.11–3.12 (2022–2023) — Faster CPython

| Version | Highlights |
|---|---|
| **3.11** | **10–60% faster** (≈25% average): specialising adaptive interpreter, cheaper frames, **zero-cost exceptions** ([[01 - CPython Execution Model]]); **`ExceptionGroup` / `except*`**, **`asyncio.TaskGroup`** and `asyncio.timeout`, `tomllib`, `Self`, fine-grained error locations in tracebacks |
| **3.12** | **PEP 695 type parameter syntax** (`def first[T](xs: list[T]) -> T`, `type` aliases), **PEP 701 f-strings** (any expression, nested quotes), **per-interpreter GIL** in the C API (PEP 684), **immortal objects** (PEP 683), `sys.monitoring` (low-overhead profiling/debugging hooks), `distutils` removed, `itertools.batched` |

### 3.13 (Oct 2024) — experiments begin

- **Free-threaded build** (PEP 703) as an **experimental**, separately built interpreter (`python3.13t`) ([[03 - The GIL and Free-Threaded Python]]).
- **Experimental JIT** (copy-and-patch), off by default.
- New interactive REPL (multi-line editing, colour), `locals()` semantics defined (PEP 667), "dead batteries" removed (PEP 594: `cgi`, `telnetlib`, ...), iOS and Android as supported platforms.

### 3.14 (Oct 7, 2025)

- **Free-threaded build officially supported** (PEP 779), still opt-in and still a separate build; default builds keep the GIL.
- **Template strings (t-strings, PEP 750)**: `t"Hello {name}"` produces a `Template` object instead of a `str`, so libraries can safely process interpolations (SQL, HTML escaping).
- **Deferred evaluation of annotations** (PEP 649/749): annotations are evaluated lazily, ending most forward-reference quoting and `from __future__ import annotations`.
- **Multiple interpreters in the stdlib** (PEP 734, `concurrent.interpreters`) and `InterpreterPoolExecutor`.
- `compression.zstd` (PEP 784), `except A, B:` without parentheses (PEP 758), safe external debugger attach (PEP 768), asyncio introspection (`python -m asyncio ps <pid>` / `pstree`), experimental JIT in official Windows/macOS binaries, tail-calling interpreter (with recent Clang).
- `multiprocessing`'s default start method is **no longer `fork` on any platform** (Linux now uses `forkserver`) ([[04 - Python Threads Processes and Executors]]).
- The new **incremental cyclic GC** shipped in 3.14.0–3.14.4 was **reverted to the 3.13 generational GC in 3.14.5** (May 2026) after reports of memory pressure in production ([[02 - Python Memory Management and GC]]).

### 3.15 (Oct 9, 2026) — the current release

- **Explicit lazy imports** (PEP 810): `lazy import pandas` defers the import until first use; big startup and memory savings for CLIs and large apps.
- New built-ins **`frozendict`** (immutable, hashable mapping) and **`sentinel`** (unique marker values).
- **UTF-8 is the default text encoding** (PEP 686) for `open()` and friends, regardless of the platform locale.
- **Unpacking in comprehensions** (PEP 798): `[*xs for xs in lists]`.
- **Tachyon**, a high-frequency **statistical sampling profiler** in the new `profiling` package (PEP 799) ([[13 - Python Performance and Profiling]]).
- JIT improvements (single-digit-percent speedups on common platforms), **stable ABI for free-threaded builds** (`abi3t`, PEP 803), so one extension wheel can target several free-threaded versions.
- Generational GC retained (the incremental GC was reverted for 3.15 as well).

---

## Upgrade Notes That Come Up

| Upgrade | What usually bites |
|---|---|
| **→ 3.11** | Mostly free speed; C extensions need rebuilt wheels |
| **→ 3.12** | `distutils` gone (setuptools provides it), `imp` removed, warnings for invalid escape sequences become `SyntaxWarning` |
| **→ 3.13** | Removed dead-battery modules (`cgi`, `crypt`, `telnetlib`, ...) |
| **→ 3.14** | `fork` no longer the default start method on Linux (code relying on inherited globals breaks); annotation evaluation changes for libraries that inspect `__annotations__`; GC behaviour changed in 3.14.0–3.14.4 (fixed by 3.14.5) |
| **→ 3.15** | UTF-8 default encoding (code relying on a non-UTF-8 locale encoding changes behaviour); deprecations removed |

- Approach: test the new version in CI as soon as release candidates are out, wait for wheels of the heavy dependencies, upgrade base images and lockfiles together ([[11 - Python Packaging and Environments]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **Running an EOL Python** | No security fixes; libraries drop support | Upgrade within the 5-year window, ideally within 1–2 years |
| **Upgrading before wheels exist** | Source builds fail or are slow in CI | Check wheel availability for heavy deps first |
| **Assuming free-threading by default** | Code written for parallel threads runs under the GIL | Default builds have the GIL; free-threaded is `python3.14t` |
| **Relying on `fork` semantics** | Breaks on 3.14 (forkserver default) | Pass state explicitly; choose the start method deliberately |
| **Encoding assumptions** | Different behaviour on 3.15 (UTF-8 default) | Always pass `encoding=` explicitly |

---

## Interview Lines

> [!tip] Say it like this
> - "Python ships a feature release every October with about five years of support: two of bugfixes, three of security fixes."
> - "3.11 was the big speed release, with the specialising adaptive interpreter and zero-cost exceptions; it also added exception groups and `TaskGroup`."
> - "3.12 added the new generic syntax and f-string grammar; 3.13 introduced an experimental free-threaded build and JIT; 3.14 made the free-threaded build officially supported, but default builds still have the GIL."
> - "3.14 added t-strings, deferred annotations and subinterpreters in the stdlib; 3.15 added lazy imports, `frozendict` and UTF-8 as the default encoding."

## Related Notes

- [[01 - CPython Execution Model]] · [[02 - Python Memory Management and GC]] · [[03 - The GIL and Free-Threaded Python]] · [[04 - Python Threads Processes and Executors]]
- [[Languages/README]]
