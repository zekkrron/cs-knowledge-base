---
tags: [source/lld-course, status/draft]
created: 2026-08-03
---
# Single Responsibility Principle

> [!abstract] A class or piece of code should have one and only one reason to change.

## Definition

There should be **one and only one reason** to change a class or a piece of code. If a class is doing multiple things, any change to one of those things forces you to reopen and modify the same class — risking breakage in the other responsibilities it handles.

---

## What Happens if SRP is Violated

It causes **fragility through tight coupling**. In a module doing too many things (responsibilities), those responsibilities get entangled. A change to one requirement forces you to touch code that handles an entirely different requirement — creating a massive blast radius for potential bugs.

---

## Core Benefits of SRP

- **Maintainability** — the measure of how easily a codebase can be understood, repaired, and updated after its initial release. When each class owns a single responsibility, finding and fixing bugs stays local.
- **Extendability** — the system's capacity to accommodate new features or changing requirements with minimal modification to existing, tested code. SRP keeps classes small and focused, so extending behaviour means adding new classes rather than bloating old ones.

---

## How to Check if Code Follows SRP

Ask: is this class or function handling too many responsibilities? If a single class is doing multiple unrelated things — e.g., validating data *and* persisting it *and* formatting output — it has more than one reason to change, and it violates SRP.

> [!info] Monster Class
> A class that handles too many responsibilities. It grows endlessly, becomes hard to test, and every change risks breaking something unrelated.

---

## How to Make Code SRP Compliant

1. **Ask yourself the "roles" of a class.** List what it does — if you need more than one bullet, it might be doing too much.
2. **If multiple roles, ask for each: are they independent enough to be separated?** (i.e., not highly cohesive)
3. **Separate the separable classes.**
4. **Reconnect via dependency injection** (if the class was supposed to be an orchestrator).

---

## Example — UserService Refactor

**Before (violates SRP):** `UserService` does four things:
1. Handles user creation
2. Validates payload
3. Connects and writes to DB
4. Publishes event to message broker

**After (SRP compliant):** Separate into focused classes:
- `UserValidator` — owns validation logic
- `UserRepository` — owns DB writes
- `UserEventPublisher` — owns event publishing

DB connections are separated to the **infra/configuration layer** (typically using connection pooling).

The repository gets the DB context object directly into its constructor and calls `execute()` on it.

`UserService` now acts as an **orchestrator, not a doer**. It gets the above classes via dependency injection (ideally as interfaces).

---

## Example — UserManager

**Bad Code:**

```java
public class UserManager {
    public void addUser(User user) { /* ... */ }
    public void deleteUser(int userId) { /* ... */ }
    public void updateUser(User user) { /* ... */ }
    public User getUser(int userId) { /* ... */ }
    public void logUserActivity(int userId, String activity) { /* ... */ }
}
```

`add`, `delete`, `update`, `get` all have one reason to change — CRUD operations on user. But `logUserActivity` has a completely different reason to change (logging format, log destination, etc.). Two reasons to change = SRP violated.

**Better Code:**

```java
public class UserManager {
    public void addUser(User user) { /* ... */ }
    public void deleteUser(int userId) { /* ... */ }
    public void updateUser(User user) { /* ... */ }
    public User getUser(int userId) { /* ... */ }
}
```

```java
public class UserActivityLogger {
    public void logUserActivity(int userId, String activity) { /* ... */ }
}
```

Logging is now its own class with its own reason to change.

---

## Example — HTMLConverter

**Bad Code:**

```java
public class HTMLConverter {
    public static void main(String[] args) {
        // text processing logic inline
        String content = readAllText("input.html");
        // ... transforms content ...
        writeToFile("output.txt", content);
    }

    private static String readAllText(String path) { /* ... */ }
    private static void writeToFile(String path, String content) { /* ... */ }
}
```

One class doing three things: text processing, reading files, writing files.

**Better Code:**

```java
public class TextProcessor {
    public String process(String rawHtml) { /* ... */ }
}
```

```java
public class FileProcessor {
    public String readAllText(String path) { /* ... */ }
    public void writeToFile(String path, String content) { /* ... */ }
}
```

Text processing gets its own class. Read and write are grouped into `FileProcessor` — they *could* be split further, but that's where over-engineering begins.

> [!warning] Over-engineering
> SRP doesn't mean "one function per class." Read and write are both file I/O operations — they share the same reason to change (file system interaction). Splitting them further adds ceremony with no real benefit. Know when to stop.

---

## Example — Employee Class

**Bad Code:**

`Employee` class has its standard properties, a constructor, and the following functions:
- `printPerformanceReport()`
- `computeSalary()`
- `updateEmployeeData()`
- `fetchEmployeeData()`

This class is doing too much — SRP is violated. It causes **fragility**: any change to one responsibility risks breaking others.

- Performance report format changes? → modify `Employee`.
- Taxing scheme changes? → modify `Employee`.
- Data storage logic changes? → modify `Employee`.

Every unrelated change forces you back into the same class.

**Better Code:**

`Employee` becomes a pure data class — only holds properties (name, address, etc.) with getters and setters. Nothing else.

Each function gets its own class:
- `PerformanceReportGenerator` — owns report formatting
- `SalaryCalculator` — owns salary computation
- `EmployeeRepository` — owns data persistence (update + fetch)

Now each class has exactly one reason to change. Report format changes? Only `PerformanceReportGenerator` is touched. Tax logic changes? Only `SalaryCalculator`. The `Employee` class itself stays untouched unless the data model changes.

---

## Extracted To

*(will be updated as the design evolves)*

## Related Notes
