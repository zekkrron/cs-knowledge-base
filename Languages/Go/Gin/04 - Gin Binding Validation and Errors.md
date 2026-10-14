---
tags: [languages/gin, status/draft]
created: 2026-10-14
---
# Gin Binding Validation and Errors

> [!abstract] What this is
> How Gin turns requests into typed structs and reports problems: the **binding** family (`ShouldBindJSON`, `ShouldBindQuery`, `ShouldBindUri`, `ShouldBindHeader`, form/multipart) vs the **`Bind*`/`MustBind*`** variants that abort with 400, struct tags (`json`, `form`, `uri`, `header`, `binding`), **validation** with `go-playground/validator` (tags, custom validators, translating errors), JSON decoding pitfalls (unknown fields, numbers, body reuse, size limits), and **centralised error handling** with `c.Error` and an error middleware producing consistent problem-details responses. The decision this helps with: strict, predictable input handling and one error format across the API.

---

## Why It Matters

- Binding mistakes become security and correctness bugs: unknown fields silently ignored, missing required fields defaulting to zero values, huge bodies accepted, errors leaking internals.
- Without a central error path, every handler formats errors differently.

---

## Binding

```go
type CreateOrderRequest struct {
    CustomerID string      `json:"customer_id" binding:"required,uuid"`
    Lines      []OrderLine `json:"lines"       binding:"required,min=1,max=100,dive"`  // dive: validate each element
    Currency   string      `json:"currency"    binding:"required,oneof=EUR USD GBP"`
    Note       *string     `json:"note"        binding:"omitempty,max=500"`            // pointer: absent vs empty
}

type OrderLine struct {
    SKU      string `json:"sku"      binding:"required,alphanum,max=32"`
    Quantity int    `json:"quantity" binding:"required,gt=0,lte=1000"`
}

type ListOrdersQuery struct {
    Status string `form:"status" binding:"omitempty,oneof=pending paid refunded"`
    Limit  int    `form:"limit"  binding:"omitempty,min=1,max=100"`
    Cursor string `form:"cursor"`
}

type OrderURI struct {
    ID string `uri:"id" binding:"required,uuid"`
}

func createOrder(c *gin.Context) {
    var req CreateOrderRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        _ = c.Error(NewValidationError(err))      // let the error middleware render it
        c.Abort()
        return
    }
    ...
}
```

| Method | On error | Use |
|---|---|---|
| **`ShouldBind*`** (`ShouldBindJSON`, `ShouldBindQuery`, `ShouldBindUri`, `ShouldBindHeader`, `ShouldBindWith`) | Returns the error; you decide the response | **Default choice** |
| `Bind*` / `MustBindWith` | Aborts with **400** and a plain-text error, then returns the error | Quick prototypes; inconsistent responses |
| `ShouldBind` (no suffix) | Chooses the binder from method + `Content-Type` | Endpoints accepting several formats |
| `ShouldBindBodyWith` / `ShouldBindBodyWithJSON` | Caches the body so it can be bound twice | Middleware that needs to read the body too |
| `BindPlain` (1.11) | Raw text body | Plain-text endpoints |

- Tags: `json` (body), `form` (query and form), `uri` (path params), `header`; since 1.12 URI/query binding supports types implementing **`encoding.TextUnmarshaler`** (custom IDs, dates). 1.11 added array **collection formats** (`collection_format:"csv"`) and defaults for collections.
- The request body is a stream: binding it twice with `ShouldBindJSON` fails the second time (empty body). Use `ShouldBindBodyWith` when needed.

### JSON decoding details

- Gin uses `encoding/json` by default (other codecs with build tags: `go_json`, `sonic`, `jsoniter`). On Go 1.27, `encoding/json` runs on the v2 engine with v1 behaviour preserved; error message texts may differ ([[00 - Go Version History]]).
- **Unknown fields are ignored** by default; enable strictness globally with `binding.EnableDecoderDisallowUnknownFields = true` (catches client typos) and `binding.EnableDecoderUseNumber = true` to avoid float64 for numbers in `any` fields.
- **Zero values vs missing**: `required` on an `int` fails for `0` (validator treats zero as missing); use pointers (`*int` with `required`) when zero is valid, or `min=0`.
- Limit body size before binding: `http.MaxBytesReader` middleware ([[03 - Gin Middleware]]).

---

## Validation

- Gin's default validator is **`go-playground/validator` v10** using the `binding` tag: `required`, `omitempty`, `min`/`max`/`len`, `gt`/`gte`/`lt`/`lte`, `oneof`, `email`, `url`, `uuid`, `alphanum`, `dive` (elements), `required_if`, `eqfield`/`gtfield` (cross-field), `datetime=2006-01-02`.
- **Custom validators**:

```go
if v, ok := binding.Validator.Engine().(*validator.Validate); ok {
    _ = v.RegisterValidation("sku", func(fl validator.FieldLevel) bool {
        return skuPattern.MatchString(fl.Field().String())
    })
    v.RegisterTagNameFunc(func(f reflect.StructField) string {   // report JSON names, not Go field names
        name, _, _ := strings.Cut(f.Tag.Get("json"), ",")
        if name == "-" {
            return ""
        }
        return name
    })
}
```

- Struct-level validation (`RegisterStructValidation`) for rules across fields.
- Validation checks **shape**; business rules (stock, permissions, state transitions) belong in the service layer ([[LLD/Foundations/04 - Enums and State Machines]]).

### Turning validator errors into responses

```go
type FieldError struct {
    Field   string `json:"field"`
    Message string `json:"message"`
}

func validationDetails(err error) []FieldError {
    var ves validator.ValidationErrors
    if !errors.As(err, &ves) {
        return []FieldError{{Field: "body", Message: "malformed request body"}}   // JSON syntax/type errors
    }
    out := make([]FieldError, 0, len(ves))
    for _, fe := range ves {
        out = append(out, FieldError{Field: fe.Field(), Message: describe(fe.Tag(), fe.Param())})
    }
    return out
}
```

- Don't return `err.Error()` from the validator or JSON decoder directly: messages expose Go type and field names and aren't stable.

---

## Centralised Error Handling

```go
// Domain errors (services know nothing about HTTP)
var ErrNotFound = errors.New("not found")
type ConflictError struct{ Reason string }
func (e *ConflictError) Error() string { return e.Reason }

// One middleware maps errors to responses
func ErrorHandler() gin.HandlerFunc {
    return func(c *gin.Context) {
        c.Next()
        err := c.Errors.Last()                    // 1.12 also offers c.GetError / GetErrorSlice helpers
        if err == nil || c.Writer.Written() {
            return
        }
        status, body := toProblem(c, err.Err)
        c.JSON(status, body)
    }
}

func toProblem(c *gin.Context, err error) (int, gin.H) {
    var ve *ValidationError
    var ce *ConflictError
    switch {
    case errors.As(err, &ve):
        return http.StatusBadRequest, problem(c, "validation failed", gin.H{"errors": ve.Fields})
    case errors.Is(err, ErrNotFound):
        return http.StatusNotFound, problem(c, "not found", nil)
    case errors.As(err, &ce):
        return http.StatusConflict, problem(c, ce.Reason, nil)
    case errors.Is(err, context.DeadlineExceeded):
        return http.StatusGatewayTimeout, problem(c, "upstream timeout", nil)
    default:
        slog.ErrorContext(c.Request.Context(), "unhandled error", "err", err, "route", c.FullPath())
        return http.StatusInternalServerError, problem(c, "internal error", nil)   // details only in logs
    }
}
```

- Handlers call `c.Error(err)` + `c.Abort()` (or return errors through a small adapter type `func(*gin.Context) error`) and stay free of status-code logic.
- Response shape: **Problem Details** (`application/problem+json`: `type`, `title`, `status`, `detail`, `instance`) plus a request ID, consistent with the rest of the platform ([[10 - Spring Validation Errors and API Design]]).
- Use `errors.Is`/`errors.As` against domain errors, never string matching ([[06 - Go Errors and Panics]]).

---

## Failure Modes

| Failure | What happens | Mitigation |
|---|---|---|
| **`Bind*` instead of `ShouldBind*`** | Plain-text 400s, inconsistent format, header already written | `ShouldBind*` + central errors |
| **Unknown fields accepted** | Typos silently ignored | `EnableDecoderDisallowUnknownFields` |
| **`required` on zero-valid ints** | Valid `0` rejected | Pointers or `min=0` |
| **Binding the body twice** | Second bind sees an empty body | `ShouldBindBodyWith` |
| **No body size limit** | Memory exhaustion | `MaxBytesReader` |
| **Raw validator/decoder messages returned** | Internals leaked, unstable messages | Map to field errors with JSON names |
| **Business rules in tags** | Logic scattered, hard to test | Service-layer validation |
| **Status codes decided in every handler** | Inconsistent API | Error middleware mapping domain errors |
| **Missing `dive` on slices** | Elements unvalidated | `dive` (and `keys`/`endkeys` for maps) |

---

## Interview Lines

> [!tip] Say it like this
> - "I use `ShouldBindJSON`, `ShouldBindQuery` and `ShouldBindUri` with `binding` tags validated by go-playground/validator, and never the `Bind*` variants that write their own 400."
> - "I turn on disallow-unknown-fields, cap body sizes, use pointers when zero is a valid value, and `dive` into slices."
> - "Validator errors are translated into field errors using JSON names; raw messages never reach clients."
> - "Handlers record errors with `c.Error`, and one middleware maps domain errors to problem-details responses with `errors.Is` and `errors.As`."

## Related Notes

- [[02 - Gin Architecture Routing and Context]] · [[03 - Gin Middleware]] · [[05 - Go Data Access]] · [[07 - Gin Project Structure and Testing]]
- [[06 - Go Errors and Panics]] · [[04 - Pydantic v2 Deep Dive]] · [[10 - Spring Validation Errors and API Design]] · [[Languages/README]]
