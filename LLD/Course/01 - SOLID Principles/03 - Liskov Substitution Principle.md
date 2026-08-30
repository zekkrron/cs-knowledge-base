---
tags: [source/lld-course, status/draft]
created: 2026-08-03
---
# Liskov Substitution Principle

> [!abstract] Any subclass must be a perfect substitute for its base class — swap the child in for the parent and nothing should break.

## Definition

Any class must be a **perfect substitute** for the base class. You should be able to substitute the child class in place of the base class and expect nothing to break — everything works as-is.

In other words, a subclass should be a perfect substitute of the base class. And if a class has multiple child classes, then **any** of the child classes should be able to substitute the base class's behaviour as-is.

---

## Violations of LSP

### 1. Fat Interface

This is where the base class forces functions onto child classes that not all children can implement, without breaking the original expectation of the base class function. This leads to forced `IllegalArgumentException` or `UnsupportedOperationException`.

**Solution:** Use the Interface Segregation Principle to separate out functions or behaviours that are not universal.

> [!example]
> The `castle()` function in chess cannot be implemented by most of the pieces. Putting that function in the base class would be wrong. Instead, there should be a separate segregated interface imposed only upon the chess pieces that actually use the castle function.

### 2. Behaviour/State Violation

Unlike the fat interface case, the subclass contains all base class functions, but they mutate state in a way that breaks the mathematical or logical expectation of the base class.

> [!example]
> `Square` inherits `Rectangle`, but its `setWidth()` function also sets the height. If a function expects a 5×10 rectangle, the behaviour breaks.

**Solution 1:** Don't inherit — both `Square` and `Rectangle` should inherit a common `Quadrilateral` class.

**Solution 2:** Composition — make `Square` keep a private `Rectangle` object and delegate the methods that don't cause problems to that rectangle.

---

## Extracted To

*(will be updated as the design evolves)*

## Related Notes

- [[01 - SOLID Principles/01 - Single Responsibility Principle]]
- [[01 - SOLID Principles/02 - Open-Close Principle]]
