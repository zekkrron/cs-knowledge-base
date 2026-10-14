---
tags: [languages/fastapi, status/draft]
created: 2026-10-14
---
# Pydantic v2 Deep Dive

> [!abstract] What this is
> Pydantic v2, the validation and serialisation layer under FastAPI (and LLM structured-output libraries): **models** and fields with constraints, **lax vs strict** parsing, **validators** (`field_validator` before/after/wrap, `model_validator`), **serialisers** and `computed_field`, `model_config` options (`extra`, `frozen`, `from_attributes`, aliases), **discriminated unions**, **`TypeAdapter`** for non-model types, **`pydantic-settings`** for configuration, the **Rust core** and how to use it for speed, and the v1 → v2 differences. The decision this helps with: modelling API contracts and external data precisely, and keeping validation fast.

---

## Why It Matters

- Every FastAPI request body, query model and response passes through Pydantic: its rules decide what gets a 422, what gets coerced, and what leaks in responses.
- Pydantic v1 is gone from current FastAPI ([[00 - FastAPI Version History and Ecosystem]]); v2 APIs and semantics differ enough that old snippets silently misbehave.

---

## Models and Fields

```python
from datetime import datetime, timezone
from decimal import Decimal
from typing import Annotated, Literal
from pydantic import BaseModel, ConfigDict, Field, EmailStr

Sku = Annotated[str, Field(pattern=r"^[A-Z0-9-]{3,32}$")]       # reusable constrained type

class OrderLine(BaseModel):
    sku: Sku
    quantity: Annotated[int, Field(gt=0, le=1000)]
    unit_price: Decimal = Field(max_digits=12, decimal_places=2)

class CreateOrder(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)   # reject unknown fields

    customer_email: EmailStr
    lines: list[OrderLine] = Field(min_length=1, max_length=100)
    currency: Literal["EUR", "USD", "GBP"] = "EUR"
    note: str | None = Field(default=None, max_length=500)
    requested_at: datetime | None = None
```

- Fields are declared with type hints; `Field(...)` adds constraints, defaults, aliases, descriptions (which flow into **JSON Schema / OpenAPI**).
- **Mutable defaults are safe** (`list[str] = []` is copied per instance), unlike plain classes ([[07 - Python Data Model]]).
- `extra`: `"ignore"` (default: drop unknown fields), `"forbid"` (422 on unknown: good for inputs, catches client typos), `"allow"` (keep them).

### Lax vs strict

| Mode | `"42"` into `int` | `1` into `bool` | `"2026-01-01"` into `datetime` |
|---|---|---|---|
| **Lax** (default) | `42` | `True` | Parsed |
| **Strict** (`strict=True` on a field/model, or `model_validate(..., strict=True)`) | Error | Error | JSON mode: parsed from string; Python mode: error |

- Lax coercion is convenient for query strings and forms; strict is safer for internal contracts and money/IDs. Choose per boundary.

---

## Validators

```python
from pydantic import field_validator, model_validator

class DateRange(BaseModel):
    start: datetime
    end: datetime

    @field_validator("start", "end", mode="after")          # after type parsing
    @classmethod
    def must_be_aware(cls, v: datetime) -> datetime:
        if v.tzinfo is None:
            raise ValueError("timezone required")
        return v

    @model_validator(mode="after")                           # whole-object rules
    def check_order(self) -> "DateRange":
        if self.end <= self.start:
            raise ValueError("end must be after start")
        return self
```

| Kind | Runs | Use |
|---|---|---|
| `mode="before"` | On raw input, before parsing | Normalising odd inputs (comma-separated strings → lists) |
| `mode="after"` (default) | On the parsed, typed value | Business-ish checks on one field |
| `mode="wrap"` | Around the core validation (`handler(value)`) | Fallbacks, custom error handling |
| `model_validator` | On the whole model (before or after) | Cross-field rules |
| `Annotated[..., AfterValidator(fn)]` | Reusable validators attached to types | Shared types across models |

- Raise `ValueError` (or `PydanticCustomError`) inside validators; FastAPI turns them into 422 responses with the field path.
- Keep validators **pure and fast**: no DB or network calls (that's service-layer logic, and it would block the event loop).

---

## Serialisation

```python
from pydantic import computed_field, field_serializer

class OrderOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)          # build from ORM objects / attributes

    id: str
    lines: list[OrderLine]
    created_at: datetime

    @computed_field
    @property
    def total(self) -> Decimal:
        return sum((l.unit_price * l.quantity for l in self.lines), Decimal("0"))

    @field_serializer("created_at")
    def iso_utc(self, v: datetime) -> str:
        return v.astimezone(timezone.utc).isoformat()

out = OrderOut.model_validate(orm_order)                     # from_attributes
out.model_dump()                                             # Python dict
out.model_dump_json(exclude_none=True)                       # JSON bytes/str directly from Rust
OrderOut.model_validate_json(raw_bytes)                      # parse JSON in Rust: faster than json.loads + validate
```

- `model_dump(mode="json")` gives JSON-compatible Python types; `by_alias=True` uses aliases; `include`/`exclude` sets.
- **Aliases**: `Field(alias="customerId")` or `model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)` for camelCase APIs with snake_case Python.
- In FastAPI, the response model's serialisation (with aliases, exclusions) happens in Rust since 0.130 ([[01 - FastAPI Architecture]]).

---

## Unions and Discriminators

```python
from typing import Annotated, Literal, Union
from pydantic import Field

class CardPayment(BaseModel):
    kind: Literal["card"]
    token: str

class BankPayment(BaseModel):
    kind: Literal["bank"]
    iban: str

Payment = Annotated[Union[CardPayment, BankPayment], Field(discriminator="kind")]

class Checkout(BaseModel):
    payment: Payment
```

- **Discriminated (tagged) unions** pick the model from a literal field: fast, precise errors ("payment.card.token missing"), and clear OpenAPI `oneOf`.
- Plain unions use **smart mode**: they try to find the best match, but ambiguous unions can choose surprisingly. Discriminate when variants overlap.

---

## TypeAdapter

```python
from pydantic import TypeAdapter

Lines = TypeAdapter(list[OrderLine])            # build ONCE (module level): construction compiles a validator
lines = Lines.validate_json(raw)                # validate any type, not just BaseModel
schema = Lines.json_schema()
```

- Validates and serialises arbitrary types (lists, dicts, `TypedDict`s, dataclasses, unions). Creating adapters is relatively expensive; **reuse** them.

---

## Settings with pydantic-settings

```python
from functools import lru_cache
from pydantic import SecretStr, PostgresDsn
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ORDERS_", env_file=".env", env_nested_delimiter="__")

    database_url: PostgresDsn
    pricing_base_url: str
    pricing_timeout_s: float = 2.0
    api_key: SecretStr                     # repr shows '**********'; use .get_secret_value()

@lru_cache
def get_settings() -> Settings:
    return Settings()                      # reads env vars once; fails fast at startup if invalid
```

- Sources in priority order: init arguments, **environment variables**, `.env` file, secrets directory (`secrets_dir`, e.g. Kubernetes mounted secrets), defaults; custom sources (Vault, cloud secret managers) can be added.
- Validate settings **at startup** (in the lifespan) so a bad deploy fails immediately ([[02 - Spring Boot Auto-Configuration and Config]] is the Spring equivalent).

---

## Performance

- The core (**pydantic-core**) is written in Rust: v2 validation is typically several times to tens of times faster than v1.
- Prefer **`model_validate_json`** over `json.loads` + `model_validate` (one pass in Rust); return models from endpoints so FastAPI serialises in Rust.
- Reuse `TypeAdapter`s; avoid defining models dynamically per request.
- `model_construct()` skips validation: only for trusted, already-validated data (it can create invalid objects).
- Very large payloads still cost CPU on the event loop thread: cap request sizes, paginate, or validate in a worker for bulk imports ([[02 - FastAPI Request Lifecycle and Sync vs Async]]).

---

## v1 → v2 Cheat Sheet

| v1 | v2 |
|---|---|
| `.dict()`, `.json()` | `.model_dump()`, `.model_dump_json()` |
| `parse_obj`, `parse_raw` | `model_validate`, `model_validate_json` |
| `class Config:` | `model_config = ConfigDict(...)` |
| `orm_mode = True` | `from_attributes=True` |
| `@validator`, `@root_validator` | `@field_validator`, `@model_validator` |
| `Optional[X]` meant "not required" | `X | None` is **required** unless it has a default |
| `BaseSettings` in `pydantic` | `pydantic_settings.BaseSettings` |
| `__root__` models | `RootModel` |

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **`extra="ignore"` on inputs** | Client typos silently dropped | `extra="forbid"` for request models |
| **Lax coercion on sensitive fields** | `"1"` becomes `True`, strings become numbers | `strict=True` where it matters |
| **`X | None` assumed optional** | Field required (422) unless defaulted | `X | None = None` |
| **I/O in validators** | Blocks the loop; slow, untestable | Validate shape in Pydantic, rules in services |
| **Returning ORM objects without a response model** | Internal fields leak; lazy loads during serialisation | Response models with `from_attributes` |
| **`model_construct` on untrusted data** | Invalid objects | Only for trusted data |
| **New `TypeAdapter` per call** | CPU overhead | Module-level adapters |
| **Secrets as plain `str`** | Printed in logs/reprs | `SecretStr` |

---

## Interview Lines

> [!tip] Say it like this
> - "Pydantic v2 parses and validates with a Rust core; by default it's lax and coerces compatible types, and I switch to strict or `extra="forbid"` for contracts where surprises are dangerous."
> - "Field validators run before or after type parsing, model validators handle cross-field rules, and validators stay pure: no database calls."
> - "Discriminated unions with a literal tag give fast, precise validation and clean OpenAPI `oneOf`s."
> - "`model_validate_json` parses and validates in one Rust pass, and returning models lets FastAPI serialise in Rust too."
> - "Config is a `pydantic-settings` class read from env vars and mounted secrets, validated at startup, with `SecretStr` for credentials."

## Related Notes

- [[01 - FastAPI Architecture]] · [[03 - FastAPI Dependency Injection]] · [[05 - FastAPI Data Access with SQLAlchemy]]
- [[09 - Python Typing]] · [[07 - Python Data Model]] · [[01 - Async LLM Client Patterns]] · [[Languages/README]]
