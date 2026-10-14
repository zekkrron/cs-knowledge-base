---
tags: [lld/patterns/non-gof, status/draft]
created: 2026-10-11
---
# Criteria (Filter) Pattern

> [!abstract] Filter a collection by independent rules that can be combined at runtime with AND, OR and NOT — so complex search logic is built from small reusable pieces instead of a combinatorial explosion of hard-coded methods.

> [!info] Not a GoF pattern
> Criteria isn't one of the 23 Gang of Four patterns, so Refactoring.guru doesn't cover it. It's a widely used structural idiom, essentially [[Composite]] applied to filter rules, with a close DDD cousin in the Specification pattern.

## Part 1 — Core Framework

### Main Purpose

To **filter a collection of objects based on different criteria**, and to **chain them together** in a decoupled, logical way (using **AND, OR, NOT** operations). It is a structural design pattern that allows you to build **complex, combinational data-filtering logic dynamically** without polluting your domain objects with search logic.

### Recognition Signal

> [!tip] The cue
> - You have a **large collection of objects in application memory** and need to extract specific subsets based on varying, complex rules.
> - You are building an **"Advanced Search"** feature (like e-commerce facets: size, color, brand, price range).
> - You want to **combine multiple filtering rules dynamically at runtime** without writing a combinatorial explosion of hardcoded methods (e.g. avoiding the need to write `getCheapAndInStockAndRedProducts()`).

### How to Implement

1. Define a **criteria interface** with one method that filters a list (`meetCriteria(List<T>)`).
2. Write **concrete criteria**, one per single rule (in stock, price under 50, category).
3. Write **combinator criteria** (`AndCriteria`, `OrCriteria`, `NotCriteria`) that hold other criteria and combine their results.
4. The **client** assembles a tree of criteria at runtime (often from the user's selected filters) and applies it once.

### Key Code

You are building the product catalog for an e-commerce site. Users can filter a list of `Product` objects by price, stock status, and category. We create **individual criteria** and then **composite them**.

```java
// 1. The Target Object
class Product {
    String name;
    double price;
    boolean inStock;
    // constructors, getters...
}

// 2. The Criteria Interface
public interface Criteria {
    List<Product> meetCriteria(List<Product> products);
}

// 3. Concrete Criteria (Single Rules)
public class CriteriaInStock implements Criteria {
    public List<Product> meetCriteria(List<Product> products) {
        List<Product> inStockProducts = new ArrayList<>();
        for (Product product : products) {
            if (product.isInStock()) inStockProducts.add(product);
        }
        return inStockProducts;
    }
}

public class CriteriaPriceUnder50 implements Criteria {
    public List<Product> meetCriteria(List<Product> products) {
        List<Product> cheapProducts = new ArrayList<>();
        for (Product product : products) {
            if (product.getPrice() < 50.0) cheapProducts.add(product);
        }
        return cheapProducts;
    }
}

// 4. Structural Criteria (The Combinators)
public class AndCriteria implements Criteria {
    private Criteria criteria;
    private Criteria otherCriteria;

    public AndCriteria(Criteria criteria, Criteria otherCriteria) {
        this.criteria = criteria;
        this.otherCriteria = otherCriteria;
    }

    public List<Product> meetCriteria(List<Product> products) {
        // Filter the list with the first criteria...
        List<Product> firstPassCriteria = criteria.meetCriteria(products);
        // ...then pass that reduced list to the second criteria
        return otherCriteria.meetCriteria(firstPassCriteria);
    }
}

// 5. The Client Assembly
public class CatalogFilter {
    public static void main(String[] args) {
        List<Product> allProducts = fetchAllProducts(); // Assume this gets 100 products

        Criteria inStock = new CriteriaInStock();
        Criteria cheap = new CriteriaPriceUnder50();

        // Dynamically composite the filters!
        Criteria cheapAndInStock = new AndCriteria(inStock, cheap);

        List<Product> results = cheapAndInStock.meetCriteria(allProducts);
    }
}
```

The other two combinators, for completeness:

```java
public class OrCriteria implements Criteria {
    private final Criteria a, b;
    public OrCriteria(Criteria a, Criteria b) { this.a = a; this.b = b; }

    public List<Product> meetCriteria(List<Product> products) {
        Set<Product> union = new LinkedHashSet<>(a.meetCriteria(products));   // keep order, no duplicates
        union.addAll(b.meetCriteria(products));
        return new ArrayList<>(union);
    }
}

public class NotCriteria implements Criteria {
    private final Criteria inner;
    public NotCriteria(Criteria inner) { this.inner = inner; }

    public List<Product> meetCriteria(List<Product> products) {
        Set<Product> excluded = new HashSet<>(inner.meetCriteria(products));
        return products.stream().filter(p -> !excluded.contains(p)).toList();
    }
}

// (in stock AND under 50) OR NOT electronics
Criteria query = new OrCriteria(new AndCriteria(inStock, cheap), new NotCriteria(electronics));
```

### Beyond the Basics

The true power of this pattern lies in **Combinatorics**. By implementing `AndCriteria`, `OrCriteria`, and `NotCriteria`, you treat the filters themselves as **composable building blocks**. You can stack them infinitely deep to create highly complex **Venn diagrams of data logic** without writing messy nested if-statements.

### Anti-Patterns & When NOT to Use

- **The Database Memory Trap.** Using this pattern to filter **millions of rows in application memory** is catastrophic. The Criteria pattern is **strictly for in-memory collections**. If you need to filter a massive dataset, you must **push that logic down to the database** using SQL `WHERE` clauses or an ORM framework. Do not pull 10 million rows into Java just to run a Criteria filter.

## Part 2 — Architecture Deep Dive

### Pattern Synergy

#### Criteria + Composite

The `AndCriteria` and `OrCriteria` classes are **textbook implementations of the [[Composite]] Pattern**. They allow you to treat a massive, complex tree of filters **exactly the same way** you treat a single, simple filter.

#### Criteria vs Chain of Responsibility

The Pipeline ([[Chain of Responsibility]]) manages **execution flow** and **halts upon failure**. Criteria manages **data reduction** and **never halts**: it just returns a smaller subset.

#### Criteria vs Specification

The **Specification** pattern is the **Domain-Driven Design (DDD) cousin** to Criteria. While Criteria is focused on **filtering lists**, Specification is often used to **validate if a single object satisfies a business rule**, or to **dynamically generate SQL queries**.

```java
// Specification: a rule about ONE object, combinable the same way
public interface Specification<T> {
    boolean isSatisfiedBy(T candidate);
    default Specification<T> and(Specification<T> o) { return c -> isSatisfiedBy(c) && o.isSatisfiedBy(c); }
    default Specification<T> or(Specification<T> o)  { return c -> isSatisfiedBy(c) || o.isSatisfiedBy(c); }
    default Specification<T> not()                   { return c -> !isSatisfiedBy(c); }
}

Specification<Order> eligibleForFreeShipping = isPaid.and(totalAbove(500)).and(isInternational.not());
if (!eligibleForFreeShipping.isSatisfiedBy(order)) throw new IllegalStateException("not eligible");
```

- Because a Specification judges **one** item, the same rule object can validate a single order, filter a list (`list.stream().filter(spec::isSatisfiedBy)`), or be translated into a query (Spring Data JPA's `Specification` builds JPA `WHERE` clauses this way, which also avoids the memory trap above).

#### Criteria vs Strategy

Each concrete criterion is like a [[Strategy]] for "which items to keep"; the pattern's distinctive part is the **combinators** that compose strategies into a tree.

### Real-World Context

You see this concept constantly in modern frontend and backend data grids:

- **UI Data Tables:** when you use Ag-Grid or React Table and apply a filter to the "Status" column and the "Date" column simultaneously, the component is using an **in-memory Criteria pattern** to reduce the dataset displayed on screen.
- **Hibernate Criteria API:** Hibernate ORM uses a conceptual variation of this pattern to allow Java developers to **build SQL queries programmatically** using strongly-typed Java objects rather than raw strings.

### Advanced Nuances — The Functional Evolution (Lambdas & Streams)

In a Senior system design or machine coding interview, you absolutely must mention that the **traditional Object-Oriented Criteria pattern has largely been rendered obsolete by Functional Programming**.

In modern languages like Java 8+, C#, and TypeScript, developers rarely write 20 separate classes for this anymore. Instead, they use **Lambdas and Streams**. The entire code block written in Part 1 can be replaced with one line of modern Java:

```java
List<Product> results = products.stream()
    .filter(Product::isInStock)
    .filter(p -> p.getPrice() < 50.0)
    .collect(Collectors.toList());
```

The Stream API's **`.filter(Predicate)`** method is the exact modern equivalent of the Criteria pattern. Demonstrating that you understand the heavy OOP roots of the pattern, but **choose to implement it using modern functional paradigms**, is the hallmark of an elite engineer.

- `Predicate` even ships the combinators: `isInStock.and(cheap)`, `a.or(b)`, `p.negate()`, `Predicate.not(p)`. Keeping **named predicates** (`static final Predicate<Product> IN_STOCK = Product::isInStock;`) preserves the pattern's reusability without the class explosion.

```java
Predicate<Product> inStock = Product::isInStock;
Predicate<Product> cheap   = p -> p.getPrice() < 50.0;
Predicate<Product> query   = inStock.and(cheap).or(Predicate.not(isElectronics));
List<Product> results = products.stream().filter(query).toList();
```

## Principles Served

- [[02 - Open-Close Principle]] — a new filter is a new criterion; existing criteria and combinators don't change.
- [[01 - Single Responsibility Principle]] — each criterion owns exactly one rule; domain objects carry no search logic.

## Sources

- No Refactoring.guru entry (non-GoF). Specification pattern background: Eric Evans & Martin Fowler, *Specifications* (DDD).
