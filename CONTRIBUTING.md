# Contributing

Issues and pull requests are welcome.

- Code, comments, commit messages and UI strings are in English.
- Keep the toolchain as pinned in `gradle/libs.versions.toml` (see `docs/BUILDING.md`).
- No deprecated Android APIs; no analytics, ads or third-party services; never request the notification permission.
- Canonical market keys are `exchange:BASE/QUOTE`; exchange-native symbols never leave the adapter.
- Run `./gradlew test :app:lintFossDebug` before opening a pull request (`:app` is flavoured, so
  there is no plain `lintDebug` task).

By contributing you agree that your contribution is licensed under the GNU General Public License v3.0 or later, like the rest of the project, and you certify the [Developer Certificate of Origin](https://developercertificate.org/).
