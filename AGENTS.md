# AGENTS.md

This repository is a Halo plugin project named `halo-plugin-schedule-calendar`.

## Project Summary

- Purpose: provide a schedule calendar plugin for Halo.
- Public route: `/schedule-calendar`
- Admin capability: create, view, edit, and delete schedule entries in a weekly calendar view.
- Editor capability: insert a schedule card for a single entry.
- Current stable version: `v2.0.0`

## Tech Stack

- Backend: Java 21, Gradle, Halo plugin API
- Frontend: Vue 3, TypeScript, Vite
- UI location: `ui/`
- Backend source: `src/main/java/run/halo/schedule/calendar/`

## Important Files

- Version: `gradle.properties`
- Plugin metadata: `src/main/resources/plugin.yaml`
- Repo rules: `AGENTS.md`
- User docs: `README.md`
- Main admin page: `ui/src/views/HomeView.vue`
- Schedule model: `src/main/java/run/halo/schedule/calendar/ScheduleEntry.java`
- Week/query logic: `src/main/java/run/halo/schedule/calendar/ScheduleQueryService.java`
- Editor card extension: `ui/src/editor/schedule-card-extension.ts`
- README screenshot asset: `docs/images/schedule-calendar-home.png`

## Schedule Entry Rules

- Base data model stores a single source entry.
- Repeating entries are not duplicated in storage.
- Recurrence is expanded dynamically when rendering/querying a week view.
- Supported recurrence frequencies:
  - `DAILY`
  - `WEEKLY`
  - `MONTHLY`
  - `YEARLY`
- Recurrence fields live under `ScheduleEntry.Spec.recurrence`:
  - `frequency`
  - `interval`
  - `until`
- Cross-day recurring entries are not allowed from the admin form.

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
- README must stay user-facing. AI/collaboration rules and release-process conventions belong in `AGENTS.md`, not `README.md`.
- Do not keep local-only Gradle workarounds in `ui/build.gradle` after troubleshooting; restore the committed repo version unless the user explicitly wants the workaround merged.
- Keep the working tree clean before handoff: do not leave local build outputs, dependency caches, screenshots for debugging, or temporary troubleshooting files committed or untracked.
- `gradle/wrapper/` is normal committed project infrastructure for this repo; do not treat it as garbage when cleaning the repository.

## Release Conventions

- Version tags follow this style:
  - stable: `v1.2.0`
  - prerelease example: `v1.2.1-beta.1`
- When releasing:
  - update `gradle.properties`
  - keep `README.md` current if screenshots, URLs, or capability descriptions change
- This repo no longer keeps a committed `CHANGELOG.md`; release history is maintained in GitHub Releases instead.
- For this repository, release notes should use the heading `## 更新内容`.
- Release notes should describe concrete 新增功能、修复内容、优化点, and should avoid vague wording such as "收敛".
- Stable release notes must describe only the delta since the previous stable release; do not repeat capabilities already published in earlier stable versions unless there is a new material change to that capability in this release.
- Before editing or publishing release notes, compare the target version against the previous stable tag and review recent GitHub release bodies so repeated items are removed and missing new items, such as permission-control changes, are not omitted.
- GitHub Actions release build is triggered by the `Release published` event.
- For prereleases, prefer creating a GitHub prerelease and let CI build and upload the jar.
- A local full Gradle build is optional before release if the user does not need local verification.
- If the user asks to "覆盖" an existing release version, it is acceptable to delete and recreate the GitHub Release/tag at the latest commit, then rerun the release workflow if needed.

## Rendering Conventions

- When both location and note are shown, always render location first and note second.
- Visible calendar block meta uses labeled multiline text: `地点：...` then `备注：...`.
- Hover tooltip meta uses space-separated text instead of multiline separators.
- Keep admin list summaries consolidated under the title, but location and note can occupy separate full-width lines for readability.
- Public page and admin page should use the same meta ordering and labeling rules.
- Calendar blocks should prefer top-aligned content instead of vertical centering when text may overflow.
- For short calendar blocks, use conservative visible-line calculation: keep title, time, and duration readable first; hide extra meta lines rather than showing half-clipped text.

## Stable Feature Set

- Supports recurring schedule entries with dynamic weekly expansion.
- Supports admin-side entry editing, deletion, and recurrence configuration.
- Supports Finder API access for Halo themes.
- Blocks cross-day recurring entries in the admin form and stabilizes 24-hour public rendering.
- Uses unified admin/public meta ordering for location, note, and recurrence text.
- Uses adaptive calendar-block text rendering to reduce clipping in short blocks.

## Working Style For Future Sessions

- Prefer minimal, targeted changes over broad refactors.
- Preserve existing Chinese product copy unless the task requires rewriting it.
- For searches, prefer `rg`.
- Use `apply_patch` for manual edits when the environment is stable.
- Do not run destructive git commands unless explicitly requested.
- The current preferred README summary sentence is: `提供前台日历页面、控制台事项管理和编辑器日程卡片的 Halo 插件。`
- Keep the README product description concise and user-facing; do not add AI collaboration notes or extra marketing filler unless the user asks for it.
