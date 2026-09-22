---
tags: [source/lld-course, status/draft]
created: 2026-07-26
---
# Problem Statement

> [!abstract] A real interview problem asked at Rippling — design a rules engine that evaluates employee business expenses against manager-defined policies, at both the individual expense level and the aggregated trip level.

### Context

This was an actual machine coding round problem asked at **Rippling**. The goal is to build an **Expense Policy Rules Engine** — a system that lets managers define rules to govern how employees spend on corporate cards, and then automatically evaluates submitted expenses against those rules.

### What You're Building

A rules engine that:
1. Evaluates **individual expenses** against a set of rules.
2. Evaluates **aggregated trip-level expenses** against a set of rules.
3. **Flags violations** clearly with reasons.

---

### Input Format

**Expenses** — a list of expenses, where each expense is a dictionary/map of string keys to string values.

Example keys on an expense object:

| Key            | Example Value                                                           |
| -------------- | ----------------------------------------------------------------------- |
| `expense_id`   | `"001"`                                                                 |
| `trip_id`      | `"trip1"`                                                               |
| `amount_usd`   | `"80"`                                                                  |
| `expense_type` | `"restaurant"`, `"airfare"`, `"entertainment"`, `"hotel"`, `"supplies"` |
| `vendor_name`  | `"Outback Roadhouse"`                                                   |

**Rules** — a list of rules to evaluate, applied at one of two levels:
- **Expense level** — evaluated per individual expense.
- **Trip level** — evaluated across all expenses belonging to the same `trip_id`.

---

### Basic Rules (start here)

1. No restaurant expense can exceed **$75**.
2. No **airfare** expenses are allowed.
3. No **entertainment** expenses are allowed.
4. No single expense can exceed **$250**.

### Extended Rules (add later)

5. A trip cannot exceed **$2000** in total expenses.
6. Total **meal (restaurant)** expenses per trip cannot exceed **$1000**.

---

### Output Format

- For **each expense**: return `APPROVED` or `REJECTED`, with reasons for rejection.
- For **each trip**: return `OK` or `VIOLATIONS`, with reasons.

---

### Example Input

```
expenses = [
  { "expense_id": "001", "trip_id": "trip1", "amount_usd": "80",  "expense_type": "restaurant", "vendor_name": "Outback Roadhouse" },
  { "expense_id": "002", "trip_id": "trip1", "amount_usd": "120", "expense_type": "supplies",   "vendor_name": "Staples"           },
  { "expense_id": "003", "trip_id": "trip1", "amount_usd": "199", "expense_type": "airfare",    "vendor_name": "Delta Airlines"    },
  { "expense_id": "004", "trip_id": "trip1", "amount_usd": "260", "expense_type": "hotel",      "vendor_name": "Marriott"          }
]
```

> [!example] Walking through the basic rules on this input:
> - `001` — restaurant, $80 → **REJECTED** (exceeds $75 restaurant cap)
> - `002` — supplies, $120 → **APPROVED** (no rule applies)
> - `003` — airfare, $199 → **REJECTED** (airfare not allowed)
> - `004` — hotel, $260 → **REJECTED** (exceeds $250 single-expense cap)

---

## Extracted To

*(nothing yet — notes will be added as the module progresses)*

## Related Notes
