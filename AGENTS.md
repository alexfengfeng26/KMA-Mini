# Repository agent rules

- On Windows, search a directory with `rg` and filter files using `-g`. Do not pass wildcard file paths such as `src/**/*.java` directly to `rg`.
- For optional audits where no match is a valid result, handle `rg` exit code 1 explicitly instead of treating it as a command failure.
