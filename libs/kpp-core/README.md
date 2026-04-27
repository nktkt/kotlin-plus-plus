# kpp-core

Emulates the typed-error subset of Kotlin++ (`T ! E`) as a plain Kotlin/JVM library.

## Mapping

| Kotlin++                                  | This library                                       |
|-------------------------------------------|----------------------------------------------------|
| `T ! E`                                   | `Result<T, E>`                                     |
| `fail X`                                  | `return Err(X)` / `err(X)`                         |
| `error Foo { case A; case B(x: Int) }`    | `sealed interface Foo : KppError { ... }`          |
| `val x = foo()` (auto-propagates `! E`)   | `val x = foo().bind()` inside `result { }`        |
| `try foo() catch (e) ...`                 | `foo().recover { ... }` / `mapErr`                 |

## Example

Kotlin++:

```
error PaymentError { case CardRejected; case Network(reason: String) }

fun charge(amount: Int): Receipt ! PaymentError {
    val token = authorize(amount)
    val receipt = capture(token)
    receipt
}
```

This library:

```kotlin
sealed interface PaymentError : KppError {
    data object CardRejected : PaymentError
    data class Network(val reason: String) : PaymentError
}

fun charge(amount: Int): Result<Receipt, PaymentError> = result {
    val token = authorize(amount).bind()
    val receipt = capture(token).bind()
    receipt
}
```

`bind()` short-circuits the enclosing `result { }` to `Err`. Do not catch
`Throwable` inside a `result { }` block — the builder uses a private
exception for non-local exit.
