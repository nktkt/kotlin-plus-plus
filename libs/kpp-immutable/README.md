# kpp-immutable

Phase-3 deep immutability primitives for Kotlin++.

## What this module provides

- `@Immutable` — class- and property-level marker. Future Kotlin++ compilers
  will enforce deep immutability at the type level. Today the marker is
  consumed by the analyzer rule **KPP005** (paired with `:libs:kpp-analyzer`).
- `ImmutableList`, `ImmutableMap`, `ImmutableSet` — sealed read-only
  collection interfaces. Backing implementations take a defensive copy of any
  input and reject runtime mutation through iterators and view collections.
- `freeze(value)` — identity helper that documents intent. Pair with the
  `@Immutable` analyzer rule for compile-time guarantees.
- `Borrow` and `Move` — placeholder annotations for the future Kotlin++
  ownership-lite keywords (Phase 3). They have no runtime effect today.

## Persistent-style API

`add`, `remove`, `set` and `put` all return a NEW collection. The original is
never mutated:

```kotlin
val a = immutableListOf("x", "y")
val b = a.add("z")     // a == [x, y], b == [x, y, z]
```

Today the implementation copies the backing array on every write. Structural
sharing (HAMT / RRB trees) is a planned optimization that does not change the
public API.

## Example

```kotlin
import dev.kpp.immutable.*

@Immutable
data class UserProfile(
    val id: String,
    @Immutable val tags: ImmutableList<String>,
)

val u1 = UserProfile("u-1", immutableListOf("admin", "beta"))
val u2 = u1.copy(tags = u1.tags.add("vip"))
// u1 is untouched; u2 has the extra tag.
```

## Borrow and Move (preview)

```kotlin
fun audit(@Borrow profile: UserProfile) { /* read-only view */ }
fun consume(@Move profile: UserProfile) { /* takes ownership */ }
```

These annotations are SOURCE-retained markers today and are reserved for the
future `borrow` / `move` keywords.
