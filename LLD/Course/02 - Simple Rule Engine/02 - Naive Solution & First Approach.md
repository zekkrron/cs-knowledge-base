---
tags: [source/lld-course, status/draft]
created: 2026-07-26
---
# Naive Solution & First Approach

> [!abstract] Two early attempts at the rule engine — the naive filter function and the strategy-based refactor — and why both fell short before we landed on the real design.

## The Naive Solution

The first instinct: write a single function that loops through all expenses and decides which ones are violating.

```java
Map<Expense, String> filterExpenses(List<Expense> expenses) {
    // for each expense, check all rules, return verdict string
}
```

One function. One place. Feels clean at first.

> [!danger] Why this breaks down
> - **SRP violation** — the function is doing two things: iterating over expenses *and* encoding every rule's logic. Adding a new rule means touching this function.
> - **OCP violation** — the function is not closed for modification. Every new rule (or change to an existing one) requires you to go back in and edit the same block of code. You can never just *extend* it.

	The rules are baked into the loop. There's no seam to add new rules without reopening the function.

---

## First Student Suggestion — Strategy Pattern on Rules

Students recognised the OCP problem and suggested pulling rules out into their own classes behind an interface. This is the **Strategy pattern** applied to rules.

Simultaneously, to handle different expense shapes cleanly, they suggested a parallel hierarchy on the expense side — a common `Expense` interface, then concrete classes per expense type. Each concrete expense would *own* a set of rules (composition), and the engine would just call `evaluate()` down the chain.

```mermaid
classDiagram
    class Rule {
        <<interface>>
        +evaluate(expense) String
    }
    class Rule1
    class Rule2
    class Rule3
    Rule <|.. Rule1
    Rule <|.. Rule2
    Rule <|.. Rule3

    class Expense {
        <<interface>>
    }
    class AirfareExpense {
        -List~Rule~ rules
    }
    class RestaurantExpense {
        -List~Rule~ rules
    }
    class HotelExpense {
        -List~Rule~ rules
    }
    Expense <|.. AirfareExpense
    Expense <|.. RestaurantExpense
    Expense <|.. HotelExpense
```

> [!tip] What's right about this
> Rules are now open for extension — add a new rule by adding a new class, not by editing existing ones. That's OCP fixed.

> [!danger] The concrete class explosion problem
> The expense-type hierarchy creates a **testing nightmare**. For every new `Expense` subclass, you need a new test class (or a repeated test setup) that covers all rule combinations against that concrete type. The test matrix grows with every new `ExpenseType` class added. You end up writing the same test logic multiple times in slightly different wrappers — which is a signal that the abstraction is wrong.
>
> The problem isn't the `Rule` interface. The problem is treating `Expense` as a type hierarchy when it's really just **data** — a map of string keys and values. Making it a class hierarchy adds complexity with no real benefit.

> [!danger] Segregating on expense type doesn't map to reality
> Think from a **database perspective** — you're never going to have a separate table for `AirfareExpense`, another for `RestaurantExpense`, another for `HotelExpense`. They all live in one `expenses` table with an `expense_type` column. If the storage model doesn't split them, your domain model has no business splitting them either. The class hierarchy is modelling a distinction that doesn't exist at the persistence layer.

> [!danger] "Can segregate" ≠ "should segregate"
> Just because you *can* pull out a concrete class per expense type doesn't mean it's adding value. Ask: what behaviour does `AirfareExpense` have that `HotelExpense` doesn't? None — they're both a bag of key-value pairs. The rules are what differ, not the data shape. If the subclass adds zero unique behaviour, it's ceremony with no payoff. Segregation needs to earn its keep.

---

## Second Student Suggestion — Rules Categorized by Expense Type

Another instinct students had: okay, keep the `Rule` interface (or abstract class), but make each **child class** represent an expense type's ruleset — `AirfareRules`, `RestaurantRules`, `EntertainmentRules`. Each child would then have methods like `maxAmount()`, `isAllowed()`, etc.

> [!danger] Same mistake wearing different clothes
> This is the expense-type segregation problem again, just moved from the data side to the rule side. You're still splitting your hierarchy on *what type of expense this targets* rather than *what the rule actually does*.
>
> The teacher's killer counterexample: say two out of three expense types have a max-amount cap, but the third doesn't. Now the third rule class is **forced** to override `maxAmount()` with a no-op or a dummy value. You're writing empty implementations just to satisfy a contract that shouldn't apply to you. That's the textbook signal that the abstraction axis is wrong.

> [!tip] The correct axis — categorize by what the rule *does*
> The real insight: rules should be classified by their **behaviour**, not by the expense type they target.
>
> - `MaxAmountRule` — caps any expense (or trip total) at a configured threshold
> - `DisallowRule` — blocks a specific expense type entirely
>
> Each rule implementation is self-contained: it knows what condition it checks and what parameters it needs (the threshold, the blocked type, etc.). You configure *which* expense types or contexts it applies to via constructor arguments or a predicate — not by creating a new subclass per expense type.
>
> This way, adding a new expense type doesn't require a new rule class. And adding a new *kind* of rule doesn't force empty overrides anywhere. The two axes (rule behaviour × expense type) stay orthogonal.

---

## Extracted To

*(will be updated as the design evolves)*

## Related Notes

- [[03 - Simple Rule Engine/01 - Problem Statement]]
