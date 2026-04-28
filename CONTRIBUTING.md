# Contributing to Kotlin++

Thanks for taking a look. Kotlin++ is an MVP; expect rough edges.

## Quickstart

```sh
gradle test
gradle :libs:kpp-analyzer:kppCheck
```

The first runs all 149 tests. The second runs the analyzer dogfood pass over
the repo and must stay at **0 violations**.

## Module map

See the root [README](README.md) for the full module map. In short:

- `libs/kpp-core`, `kpp-capability`, `kpp-analyzer`, `kpp-immutable`,
  `kpp-concurrent`, `kpp-derive`, `kpp-test`, `kpp-gradle-plugin`
- `samples/` for runnable examples

## Conventions

- No emojis in code or docs.
- Comments only when the **why** is non-obvious. The **what** should be the code.
- Every new public function gets a test.
- The analyzer dogfood must remain at 0 violations. If you have to suppress a
  rule, justify it in the PR.
- Two-space YAML indents, no tabs, no trailing whitespace.

## Adding a new analyzer rule

1. Open an issue using the **New analyzer rule** template.
2. Reserve the next free ID at the bottom of [`docs/RULES.md`](docs/RULES.md).
3. Implement the rule, add positive and negative tests, run:

   ```sh
   gradle :libs:kpp-analyzer:test
   gradle :libs:kpp-analyzer:kppCheck
   ```

4. Update `docs/RULES.md` with the final entry.

## Adding a new module

1. Add the module to `settings.gradle.kts`.
2. Drop a placeholder `build.gradle.kts` in the new module directory.
3. Add a `README.md` describing the module's scope.
4. Run `gradle test` to confirm the build still passes.

## License

The license is **not yet decided**, matching the note in the README. If you
want to consume Kotlin++ code in your own project, please open an issue so we
can sort it out together.
