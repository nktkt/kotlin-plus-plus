# derive-ksp-demo

Consumer sample for the Phase-4 prototype `kpp-derive-ksp` processor.

## What this shows

`Models.kt` declares two `@DeriveJson` classes. The KSP processor generates
a `toJsonGenerated()` extension for each, with no runtime reflection. The
sample's `Main.kt` runs both encoders side by side; the test class
`DeriveJsonGeneratedTest` asserts byte-for-byte parity with `Json.encode`.

## Where the generated code lives

After a build, look for the generated source under:

```
samples/derive-ksp-demo/build/generated/ksp/main/kotlin/dev/kpp/samples/ksp/
    Greeting_DeriveJson.kt
    Request_DeriveJson.kt
```

A shared helper file is generated once per processing round:

```
samples/derive-ksp-demo/build/generated/ksp/main/kotlin/dev/kpp/derive/ksp/generated/
    DeriveJsonGeneratedHelpers.kt
```

It contains the `__kppEscapeJsonString` function used by every generated
per-class file.

## Running

```
gradle :samples:derive-ksp-demo:test
gradle :samples:derive-ksp-demo:run
```

The `run` task prints four lines: two from the runtime encoder, two from
the generated extensions, with each pair producing identical JSON.
