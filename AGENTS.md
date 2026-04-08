# AGENTS.md

This repository is a Halo plugin project named `halo-plugin-schedule-calendar`.

## Project Summary

- Purpose: provide a schedule calendar plugin for Halo.
- Public route: `/schedule-calendar`
- Admin capability: create, view, edit, and delete schedule entries in a weekly calendar view.
- Editor capability: insert a schedule card for a single entry.
- Current stable version: `v2.2.0`

## Tech Stack

- Backend: Java 21, Gradle, Halo plugin API
- Frontend: Vue 3, TypeScript, Vite
- UI location: `ui/`
- Backend source: `src/main/java/run/halo/schedule/calendar/`

## Important Files

- Version: `gradle.properties`
- Plugin metadata: `src/main/resources/plugin.yaml`
- Role templates: `src/main/resources/extensions/roleTemplate.yaml`
- Native plugin settings schema: `src/main/resources/extensions/settings.yaml`
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
- For this project, Halo-native plugin settings must be preferred over custom settings pages. Do not reintroduce a custom settings tab unless the user explicitly asks for it.
- The plugin backup feature belongs in the plugin settings area, but the page itself should stay visually close to Halo native patterns and avoid extra decorative copy.
- Do not use invalid create requests to probe permissions. For admin permission checks, prefer no-side-effect probes against real resources.
- Do not add `@halo-dev/ui-shared` just to read UI permissions in this repo. It pulls extra frontend dependencies such as `pinia`/`vue-router` and can break the current plugin build.
- For public calendar rendering, do not interpolate schedule data into `innerHTML`. Build DOM nodes with `textContent`/`createElement` so persisted entry data cannot become stored XSS.
- `unplugin-icons` is not required in this repo. Prefer Halo-provided icons from `@halo-dev/components`; keeping `unplugin-icons` pulls `vue-template-compiler` into the dependency tree and creates avoidable audit noise.
- When dependency audit is part of the task, `pnpm.overrides` in `ui/package.json` is an acceptable way to pin vulnerable transitive packages to safe versions, as long as `pnpm build`, `pnpm audit`, and Gradle tests still pass afterward.

## Release Conventions

- Version tags follow this style:
  - stable: `v1.2.0`
  - prerelease example: `v1.2.1-beta.1`
- When releasing:
  - update `gradle.properties`
  - keep `README.md` current if screenshots, URLs, or capability descriptions change
- If `WeekViewResponse`, Finder 返回结构, or public `weeks` API fields change, update both the Finder section and the REST API section in `README.md`; do not document only one side.
- When public-page UX adds user-visible capabilities such as 当前状态、下一个事项倒计时、当前时间线, reflect them in `README.md` 功能概览 and 对外能力说明.
- This repo no longer keeps a committed `CHANGELOG.md`; release history is maintained in GitHub Releases instead.
- For this repository, release notes should use the heading `## 更新内容`.
- Release notes should describe concrete 新增功能、修复内容、优化点, and should avoid vague wording such as "收敛".
- Release notes are user-facing change logs. Do not include internal verification/process statements such as having run `pnpm audit`, `pnpm build`, or `./gradlew.bat test`.
- Do not include plugin metadata-only edits in release notes, such as author name, author website, or similar manifest/profile adjustments, unless the user explicitly wants those noted.
- Stable release notes must aggregate the effective changes across the whole prerelease cycle since the previous stable release.
- When converting a beta series into a stable release, do not keep the beta notes verbatim and do not omit beta-only fixes. Rewrite the stable notes in the repo's formal structure.
- Before editing or publishing release notes, compare the target version against the previous stable tag and review recent GitHub release bodies so repeated items are removed and missing new items, especially permission-control and backup-recovery changes, are not omitted.
- GitHub Actions release build is triggered by the `Release published` event.
- For prereleases, prefer creating a GitHub prerelease and let CI build and upload the jar.
- A local full Gradle build is optional before release if the user does not need local verification.
- If the user asks to "覆盖" an existing release version, it is acceptable to delete and recreate the GitHub Release/tag at the latest commit, then rerun the release workflow if needed.
- If the user asks to delete a whole beta series before a stable release, remove both the GitHub prereleases and their tags before creating the stable tag.

## Permission Rules

- `roleTemplate.yaml` is the source of truth for Halo role aggregation in this repo.
- The `manage` role must include the real `scheduleentries` read/write verbs it depends on; do not rely only on transitive role assumptions when the UI needs direct resource access.
- If frontend behavior depends on whether a user can manage schedule entries, prefer probing real `scheduleentries` capability with a no-side-effect request rather than relying only on custom permission endpoints.
- Do not gate the discoverability of the plugin's main console route, quick action, or plugin self tabs purely on Halo UI permission metadata. If Halo-side UI permission aggregation drifts or fails after upgrade, the whole入口 can disappear even while the plugin is still `STARTED`.
- For this plugin, prefer keeping console entry points visible and enforce real permissions inside the page with runtime capability probes plus readonly/disabled states.
- If a custom permission endpoint is kept, its authorization rules and the role template entries for that endpoint must be updated together.
- Never use an invalid create payload to probe permissions. That can hit Halo validation first and generate misleading 500 logs instead of a clean permission result.

## Console Entry Troubleshooting

- If the console menu item and plugin self tab disappear together after a release, do not assume the release jar is broken first.
- Check the live plugin state before changing code:
  - `halo plugin get schedule-calendar --json`
  - if `status.phase` is `STARTED` and `status.entry` exists, the plugin has started and the console asset is registered
- If the plugin is `STARTED` but the入口 is still missing, suspect Halo UI permission aggregation or frontend route visibility rules before suspecting missing bundle files.
- When comparing release artifacts, verify at least:
  - `console/main.js`
  - `console/style.css`
  - `META-INF/plugin-components.idx`
  - `extensions/roleTemplate.yaml`
- For authenticated console bundle checks, remember `/apis/api.console.halo.run/v1alpha1/plugins/-/bundle.js` returns the login page when requested without console authentication, so unauthenticated fetch results are not valid evidence.

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
