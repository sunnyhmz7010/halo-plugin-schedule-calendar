# AGENTS.md

This repository is a Halo plugin project named `halo-plugin-schedule-calendar`.

## Project Summary

- Purpose: provide a schedule calendar plugin for Halo.
- Public route: `/schedule-calendar`
- Admin capability: create, view, and delete schedule entries in a weekly calendar view.
- Editor capability: insert a schedule card for a single entry.
- Current prerelease version: `v1.1.0-beta.3`

## Tech Stack

- Backend: Java 21, Gradle, Halo plugin API
- Frontend: Vue 3, TypeScript, Vite
- UI location: `ui/`
- Backend source: `src/main/java/run/halo/schedule/calendar/`

## Important Files

- Version: `gradle.properties`
- Changelog: `CHANGELOG.md`
- Plugin metadata: `src/main/resources/plugin.yaml`
- Main admin page: `ui/src/views/HomeView.vue`
- Schedule model: `src/main/java/run/halo/schedule/calendar/ScheduleEntry.java`
- Week/query logic: `src/main/java/run/halo/schedule/calendar/ScheduleQueryService.java`
- Editor card extension: `ui/src/editor/schedule-card-extension.ts`

## Schedule Entry Rules

- Base data model stores a single source entry.
- Repeating entries are not duplicated in storage.
- Recurrence is expanded dynamically when rendering/querying a week view.
- Supported recurrence frequencies:
  - `DAILY`
  - `WEEKLY`
  - `MONTHLY`
  - `YEARLY`
- Recurrence fields currently live under `ScheduleEntry.Spec.recurrence`:
  - `frequency`
  - `interval`
  - `until`

## Development Notes

- Frontend validation command:
  - `npm run build` in `ui/`
- Backend build/test command:
  - `./gradlew.bat build`
  - `./gradlew.bat test`
- Important environment constraint:
  - this project requires Java 21 toolchain for Gradle tasks
  - if the machine only has Java 8 or Java 25, Gradle verification may fail before tests run
- In this repo, it is acceptable to skip Gradle verification if the environment does not have JDK 21 and the user does not want Gradle-based validation.

## Release Conventions

- Version tags follow this style:
  - stable: `v1.0.0`
  - prerelease: `v1.1.0-beta.3`
- When releasing:
  - update `gradle.properties`
  - prepend release notes to `CHANGELOG.md`
  - keep `README.md` current if it mentions the current version
- Keep `README.md` user-facing. AI/collaboration rules and release-process conventions belong in `AGENTS.md`, not `README.md`.
- Before publishing a release, inspect the latest GitHub release body format and align with the established style.
- For this repository, beta release notes should use the heading `## 更新内容`.
- GitHub Actions release build is triggered by the `Release published` event.
- For beta releases, prefer creating a GitHub prerelease and let CI build and upload the jar.
- A local full Gradle build is optional before release if the user does not need local verification.
- Current release line after the recurring-schedule feature is `v1.1.0-beta.3`.

## Recent Functional Change

- Recurring schedule entries were added in `v1.1.0-beta.1`.
- Admin entry editing and clearer recurring-entry visibility were added in `v1.1.0-beta.2`.
- Theme-facing Finder API support was added in `v1.1.0-beta.3`.
- Admin create dialog now supports recurrence frequency, interval, and until date.
- Admin week summary and calendar rendering now count expanded occurrences, not only raw stored entries.
- Public calendar and editor card also show recurrence information.

## Working Style For Future Sessions

- Prefer minimal, targeted changes over broad refactors.
- Preserve existing Chinese product copy unless the task requires rewriting it.
- For searches, prefer `rg`.
- Use `apply_patch` for manual edits.
- Do not run destructive git commands unless explicitly requested.
