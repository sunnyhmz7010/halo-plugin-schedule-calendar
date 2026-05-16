# AGENTS.md

## Reusable Rules

These rules are intentionally written in a reusable way so they can be copied into other repositories as a starting point.

- This repository's `Reusable Rules` section is intended to be the shared baseline across projects. By default, other project `AGENTS.md` files should keep these reusable rules aligned in structure, intent, and policy unless the user explicitly asks for a deliberate deviation.

### General Working Style

- Prefer minimal, targeted changes over broad refactors.
- Preserve existing product copy unless the task requires rewriting it.
- Keep user-facing docs concise and practical; avoid adding AI collaboration notes or marketing filler unless explicitly requested.
- If a repository maintains a public-facing root `README.md`, keep it user-facing and promotional for external readers. Contributor rules, operational constraints, missing-work notes, AI guidance, release-process conventions, and collaboration guidance belong in `AGENTS.md`, not `README.md`.
- Do not create repository subdirectories such as `docs/`, `notes/`, `tmp/`, or similar just to store AI handoff notes, internal architecture summaries, release drafting scratch files, or collaboration-only guidance. Put that material in `AGENTS.md` unless the user explicitly asks for a separate file or directory.
- If a repository maintains a public-facing root `README.md`, follow the style of strong, high-star GitHub project READMEs: lead with clear value, polished feature framing, concise usage/integration guidance, and externally useful examples.
- If a repository maintains a public-facing root `README.md`, prefer the current polished README pattern used in this repository family: a centered hero block with logo, project name, one-sentence value summary, badges, and primary links first; then a short “why this exists” section, screenshot preview, core capability breakdown, quick start, usage/integration examples, feature details, local development, security reporting, license, and other public project metadata.
- If a repository maintains a public-facing root `README.md`, keep README section order user-journey oriented: what it is, why it matters, what it can do, how to start, how to use/integrate it, then contributor-facing local development notes. Do not lead with developer setup, internal architecture, or maintenance workflow.
- If a repository maintains a public-facing root `README.md`, group capability sections by user-facing surface or scenario, using short subsections plus concise bullet lists. Prefer concrete capability statements over abstract architecture descriptions.
- If a repository maintains a public-facing root `README.md`, include copyable real examples for routes, commands, Finder calls, APIs, or embed snippets when the product exposes them. Keep examples minimal but directly runnable or adaptable.
- If a repository maintains a public-facing root `README.md`, it is acceptable to use light decoration such as emoji section headings, centered screenshots, badges, and concise call-to-action links, as long as the page still reads cleanly and professionally.
- If a repository maintains a public-facing root `README.md`, keep paragraphs and bullet lists tight. Prefer a few high-signal bullets over long prose blocks, and avoid repeating the same capability in multiple sections unless each repetition adds new context.
- If a repository maintains a public-facing root `README.md`, prefer a direct product-description leading sentence instead of starting with the repository name or "This project is ...", unless the user explicitly asks for that phrasing.
- If a repository maintains a public-facing root `README.md`, do not add sections framed as internal progress tracking or roadmap bookkeeping, such as “当前已实现”, “当前缺失”, “后续里程碑”, “未来计划”, or similar wording.
- If a repository maintains a public-facing root `README.md`, do not use “当前…” style internal status phrasing unless the user explicitly requests it. README should read like a polished public-facing project page, not an internal handoff note.
- In public-facing docs such as a root `README.md`, write commands using standard upstream tooling, not local wrappers, aliases, shell functions, or private helper commands. Keep local convenience commands in contributor-only docs such as `AGENTS.md`.
- For searches, prefer `rg`.
- Use `apply_patch` for manual edits when the environment is stable.
- Do not run destructive git commands unless explicitly requested.

### Validation And Hygiene

- Keep the working tree clean before handoff: do not leave local build outputs, dependency caches, screenshots for debugging, or temporary troubleshooting files committed or untracked.
- When the environment lacks the required toolchain and the user does not need full local verification, it is acceptable to skip heavy verification, but say so explicitly.
- Release notes are user-facing change logs. Do not include internal verification/process statements such as having run tests, builds, audits, or CI checks unless explicitly requested.
- When repository structure, commands, external capabilities, release process, or recurring engineering pitfalls change, update `AGENTS.md` in the same task. Keeping this file current is required, not optional.
- If newly learned guidance appears to be reusable across repositories rather than specific to the current project, ask whether to automatically scan other project `AGENTS.md` files, apply the shared rule where appropriate, and push those updates to their remotes.
- For GitHub-hosted repositories, maintain the baseline repository-governance files consistently across projects unless the user explicitly asks for divergence. This baseline includes `LICENSE`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, issue templates, and similar repo-health/community files.
- "Consistently" does not mean every line must be identical. Keep the structure, tone, and policy baseline aligned, but make the necessary project-specific substitutions for repository name, product name, links, version fields, platform fields, security scope, issue-form fields, and other repo-specific facts.
- If one of those GitHub governance files is added, removed, or materially changed in a way that should become the new shared baseline, ask whether to propagate the same baseline change across other GitHub repositories and push the updates, while still preserving required project-specific substitutions.

### Security And Review

- Review code with a bug-risk mindset first. Prioritize functional regressions, security issues, breaking changes, and missing tests before style or cleanup suggestions.
- If code returns `text/html` built from server-side string templates, HTML-escape all text fields from settings, persisted data, and user-controlled input before interpolating them into tags such as `<title>`, headings, attributes, or inline scripts.
- Do not assume only frontend `innerHTML` paths are XSS-relevant. Also inspect backend-rendered HTML, email templates, CMS fragments, and any raw string formatting that bypasses auto-escaping.
- For admin permission checks, prefer no-side-effect probes against real resources.
- Do not use invalid create requests to probe permissions; validation failures can mask the real authorization result and create misleading server logs.

### Dependency And Upgrade Rules

- Do not merge dependency or toolchain bumps just to clear security alerts or Dependabot PRs. First confirm the repo's current config is compatible and all required CI/build/test steps stay green.
- Treat build-tool upgrades such as `vite`, bundlers, editors, framework compilers, and test runners as compatibility work, not routine version bumps. If the upgrade breaks the build, defer it or patch it properly instead of merging a red PR.
- When a security alert applies only to dev tooling or to a runtime mode the project does not use, verify the real exposure before escalating. Distinguish "reported in the dependency graph" from "actually exploitable in this repo."

### Release Rules

- Rewrite stable release notes from the commits actually included by the published tag. Do not mix in changes that landed only on `main` after that tag.
- When converting prereleases into a stable release, aggregate the effective user-visible changes across the prerelease cycle instead of copying beta notes verbatim.
- If replacing or deleting an older release in favor of a newer one, compare the old tag, the new tag, and the default branch separately so unreleased work is not accidentally documented.
- Do not promote a prerelease to a stable `vX.Y.Z` release unless the user explicitly asks for that exact stable release.
- GitHub release titles should default to the bare tag name such as `v0.1.0` or `v0.1.0-beta.1`, not `ProjectName v0.1.0`, unless the user explicitly asks for a product-prefixed title.

## Repository-Specific Rules

This repository is a Halo plugin project named `halo-plugin-schedule-calendar`.

### Project Summary

- This project is a Halo schedule calendar plugin, not a generic calendar library.
- Goal: provide a single plugin that covers public calendar viewing, console-side schedule management, editor schedule cards, and theme/API integration for Halo sites.
- Public route: `/schedule-calendar`
- Public iCal route: `/schedule-calendar.ics`
- Public card route: `/schedule-calendar/cards/{name}`
- Theme integration surface: Finder API via `scheduleCalendarFinder`
- Public JSON integration surface: REST API under `/apis/api.schedule.calendar.sunny.dev/v1alpha1`
- Public page title is configurable in plugin settings, but public page and iCal subscription routes are fixed.
- Public page and iCal subscription routes are fixed; do not reintroduce configurable public-path settings unless the user explicitly asks for that feature again.
- Admin capability: create, view, edit, and delete schedule entries in a weekly calendar view.
- Editor capability: insert a schedule card for a single entry.
- Current stable version: `v2.8.0`
- Current prerelease target in local development: `v2.9.0-alpha.7`

### Tech Stack

- Backend: Java 21, Gradle, Halo plugin API
- Frontend: Vue 3, TypeScript, Vite
- UI location: `ui/`
- Backend source: `src/main/java/run/halo/schedule/calendar/`

### Important Files

- Version: `gradle.properties`
- Plugin metadata: `src/main/resources/plugin.yaml`
- Role templates: `src/main/resources/extensions/roleTemplate.yaml`
- Native plugin settings schema: `src/main/resources/extensions/settings.yaml`
- Public-page meta API: `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/public-meta`
- Repo rules: `AGENTS.md`
- User docs: `README.md`
- Main admin page: `ui/src/views/HomeView.vue`
- Schedule model: `src/main/java/run/halo/schedule/calendar/ScheduleEntry.java`
- Week/query logic: `src/main/java/run/halo/schedule/calendar/ScheduleQueryService.java`
- Editor card extension: `ui/src/editor/schedule-card-extension.ts`
- README screenshot asset: `schedule-calendar-home.png`

### Schedule Entry Rules

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

### Repository Development Notes

- Frontend validation command:
  - `npm run build` in `ui/`
- Backend build/test command:
  - `./gradlew.bat build`
  - `./gradlew.bat test`
- External calendar MVP:
  - use Halo-native plugin settings to configure Google Calendar or other ICS/iCal subscription sources
  - external calendar events are read-only and merge into public page, Finder, and REST query results
  - do not mix external ICS events into local schedule-entry CRUD or editor-card selection unless the user explicitly asks for that broader scope
- Important environment constraint:
  - this project requires Java 21 toolchain for Gradle tasks
  - if the machine only has Java 8 or Java 25, Gradle verification may fail before tests run
- Do not keep local-only Gradle workarounds in `ui/build.gradle` after troubleshooting; restore the committed repo version unless the user explicitly wants the workaround merged.
- `gradle/wrapper/` is normal committed project infrastructure for this repo; do not treat it as garbage when cleaning the repository.
- For this project, Halo-native plugin settings must be preferred over custom settings pages. Do not reintroduce a custom settings tab unless the user explicitly asks for it.
- The plugin backup feature belongs in the plugin settings area, but the page itself should stay visually close to Halo native patterns and avoid extra decorative copy.
- The plugin backup/settings area may surface public export endpoints such as the iCal subscription URL when they are real user-facing capabilities; present them as simple utility actions, not marketing content.
- Do not add `@halo-dev/ui-shared` just to read UI permissions in this repo. It pulls extra frontend dependencies such as `pinia`/`vue-router` and can break the current plugin build.
- For public calendar rendering, do not interpolate schedule data into `innerHTML`. Build DOM nodes with `textContent`/`createElement` so persisted entry data cannot become stored XSS.
- `unplugin-icons` is not required in this repo. Prefer Halo-provided icons from `@halo-dev/components`; keeping `unplugin-icons` pulls `vue-template-compiler` into the dependency tree and creates avoidable audit noise.
- `ui/env.d.ts` should not keep `unplugin-icons` type references after icon cleanup. Keep only the Vite/client reference plus repo-specific global component declarations unless the dependency is intentionally reintroduced.
- When dependency audit is part of the task, `pnpm.overrides` in `ui/package.json` is an acceptable way to pin vulnerable transitive packages to safe versions, as long as `pnpm build`, `pnpm audit`, and Gradle tests still pass afterward.
- When this repository's structure, commands, Finder/REST capabilities, release conventions, or known pitfalls change, update this file before handoff so a new session can recover project context quickly.

### Repository Release Conventions

- Version tags follow this style:
  - stable: `v1.2.0`
  - prerelease example: `v1.2.1-beta.1`
- When releasing:
  - update `gradle.properties`
  - keep `README.md` current if screenshots, URLs, or capability descriptions change
- If `WeekViewResponse`, Finder 返回结构, or public `weeks` API fields change, update both the Finder section and the REST API section in `README.md`; do not document only one side.
- If `SummaryResponse` or week-page summary fields change, update the server-rendered public page script to consume the new structure and keep README examples aligned with both Finder and REST usage.
- When public-page UX adds user-visible capabilities such as 当前状态、下一个事项倒计时、当前时间线, reflect them in `README.md` 功能概览 and 对外能力说明.
- This repo no longer keeps a committed `CHANGELOG.md`; release history is maintained in GitHub Releases instead.
- For this repository, release notes should use the heading `## 更新内容`.
- Release notes should describe concrete 新增功能、修复内容、优化点, and should avoid vague wording such as "收敛".
- Do not include plugin metadata-only edits in release notes, such as author name, author website, or similar manifest/profile adjustments, unless the user explicitly wants those noted.
- Stable release notes must aggregate the effective changes across the whole prerelease cycle since the previous stable release.
- Before editing or publishing release notes, compare the target version against the previous stable tag and review recent GitHub release bodies so repeated items are removed and missing new items, especially permission-control and backup-recovery changes, are not omitted.
- GitHub Actions release build is triggered by the `Release published` event.
- For prereleases, prefer creating a GitHub prerelease and let CI build and upload the jar.
- A local full Gradle build is optional before release if the user does not need local verification.
- If the user asks to "覆盖" an existing release version, it is acceptable to delete and recreate the GitHub Release/tag at the latest commit, then rerun the release workflow if needed.
- If the user asks to delete a whole beta series before a stable release, remove both the GitHub prereleases and their tags before creating the stable tag.

### Permission Rules

- `roleTemplate.yaml` is the source of truth for Halo role aggregation in this repo.
- The `manage` role must include the real `scheduleentries` read/write verbs it depends on; do not rely only on transitive role assumptions when the UI needs direct resource access.
- If frontend behavior depends on whether a user can manage schedule entries, prefer probing real `scheduleentries` capability with a no-side-effect request rather than relying only on custom permission endpoints.
- Do not gate the discoverability of the plugin's main console route, quick action, or plugin self tabs purely on Halo UI permission metadata. If Halo-side UI permission aggregation drifts or fails after upgrade, the whole入口 can disappear even while the plugin is still `STARTED`.
- For this plugin, prefer keeping console entry points visible and enforce real permissions inside the page with runtime capability probes plus readonly/disabled states.
- If a custom permission endpoint is kept, its authorization rules and the role template entries for that endpoint must be updated together.

### Console Entry Troubleshooting

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

### Rendering Conventions

- When both location and note are shown, always render location first and note second.
- Visible calendar block meta uses labeled multiline text: `地点：...` then `备注：...`.
- Hover tooltip meta uses space-separated text instead of multiline separators.
- Keep admin list summaries consolidated under the title, but location and note can occupy separate full-width lines for readability.
- Public page and admin page should use the same meta ordering and labeling rules.
- Calendar blocks should prefer top-aligned content instead of vertical centering when text may overflow.
- For short calendar blocks, use conservative visible-line calculation: keep title, time, and duration readable first; hide extra meta lines rather than showing half-clipped text.

### Stable Feature Set

- Supports recurring schedule entries with dynamic weekly expansion.
- Supports admin-side entry editing, deletion, and recurrence configuration.
- Supports Finder API access for Halo themes.
- Blocks cross-day recurring entries in the admin form and stabilizes 24-hour public rendering.
- Uses unified admin/public meta ordering for location, note, and recurrence text.
- Uses adaptive calendar-block text rendering to reduce clipping in short blocks.
