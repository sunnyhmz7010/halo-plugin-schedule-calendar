# halo-plugin-schedule-calendar

提供前台日历页面、控制台事项管理和编辑器日程卡片的 Halo 插件。

## 功能概览

1. 提供前台公开页面路由 `/schedule-calendar`，以清晰的周视图方式展示日程安排。
2. 提供控制台管理页面，支持新增、查看、编辑、删除事项以及配置循环规则。
3. 提供编辑器日程卡片，可在文章内容中插入单个事项的时间信息。
4. 提供插件设置页，可自定义前台页面标题，方便匹配站点风格。
5. 提供 Halo 主题模板可直接调用的 Finder API，方便主题侧读取周视图和事项数据。

## 在线预览

- 演示地址：<https://sunnyhmz.top/schedule-calendar>

## 效果截图

![日程日历前台页面截图](https://raw.githubusercontent.com/sunnyhmz7010/halo-plugin-schedule-calendar/refs/heads/main/docs/images/schedule-calendar-home.png)

## 对外能力说明

插件当前提供四类能力：

### 1. 前台公开页面

- 页面周历：`GET /schedule-calendar`
- 单事项卡片页：`GET /schedule-calendar/cards/{name}`

说明：

- `/schedule-calendar` 面向站点访客，直接输出可访问的前台日历页面。
- `/schedule-calendar/cards/{name}` 适合做单事项嵌入页、卡片预览或独立详情块。

### 2. 主题侧集成（Finder API）

主题模板可直接使用以下 Finder：

- `scheduleCalendarFinder.week(start)`
- `scheduleCalendarFinder.day(date)`
- `scheduleCalendarFinder.range(start, end)`
- `scheduleCalendarFinder.upcoming(limit)`
- `scheduleCalendarFinder.get(name)`
- `scheduleCalendarFinder.listAll()`

说明：

- `week(start)`：
  - 参数 `start` 使用 `yyyy-MM-dd` 格式。
  - 返回该日期所在周的完整周视图数据。
  - 传空值时返回当前周。
- `day(date)`：
  - 参数 `date` 使用 `yyyy-MM-dd` 格式。
  - 返回单日的占用时间块和空闲时间块。
  - 传空值时返回当天。
- `range(start, end)`：
  - 参数均使用 `yyyy-MM-dd` 格式。
  - 返回指定日期区间内展开后的实际事项发生记录。
  - 适合首页、归档、时间线或主题侧自定义分组展示。
- `upcoming(limit)`：
  - 返回未来事项发生记录。
  - `limit` 为空时默认返回 `10` 条，最大 `100` 条。
  - 会自动展开循环事项并按最近时间排序。
- `get(name)`：
  - 按事项名称返回单个事项卡片数据。
  - 适合文章页、详情区块或编辑器卡片二次展示。
- `listAll()`：
  - 返回全部事项卡片列表。
  - 不展开循环发生记录，适合主题侧自行筛选或建立索引。

主题模板示例：

```html
<div th:with="week=${scheduleCalendarFinder.week('2026-04-01')}">
  <div th:text="${week.weekStart}"></div>
</div>

<div th:each="item : ${scheduleCalendarFinder.upcoming(5)}">
  <span th:text="${item.title}"></span>
  <time th:text="${item.startTime}"></time>
</div>

<div th:each="entry : ${scheduleCalendarFinder.listAll()}">
  <span th:text="${entry.title}"></span>
</div>
```

### 3. 公开 REST API

如果主题、自定义前端或外部脚本更适合消费 JSON，可使用以下公开接口：

- `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/weeks?start=2026-04-01`
- `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/days?date=2026-04-01`
- `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/occurrences?start=2026-04-01&end=2026-04-07`
- `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/upcoming?limit=10`
- `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/entrycards`
- `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/entrycards/{name}`

说明：

- `weeks`：
  - 返回指定周的周视图结构，适合自定义前端日历视图。
- `days`：
  - 返回单日时间块，适合移动端或详情页单日展示。
- `occurrences`：
  - 返回指定日期区间内展开后的事项发生记录。
  - 循环事项会按实际发生时间展开。
- `upcoming`：
  - 返回未来事项发生记录。
  - 默认 `10` 条，最大 `100` 条。
- `entrycards`：
  - 返回基础事项卡片列表，不展开循环发生记录。
- `entrycards/{name}`：
  - 返回单个事项卡片详情。

Finder 和公开 REST API 的查询语义保持一致：

- 想在主题模板里直接取值，优先用 Finder。
- 想在前端应用、脚本或外部系统里取 JSON，优先用 REST API。

### 4. 控制台内部 API 与权限边界

以下能力属于插件管理面，不作为公开集成接口：

- 自定义模型管理：`/apis/schedule.calendar.sunny.dev/v1alpha1/scheduleentries`
- 备份导出：`GET /apis/console.api.schedule.calendar.sunny.dev/v1alpha1/backupexports`
- 备份恢复：`POST /apis/console.api.schedule.calendar.sunny.dev/v1alpha1/backupimports`

权限约定：

- 匿名访问默认只开放前台页面和公开 REST API。
- 事项的新增、编辑、删除，以及备份导入导出，需要 `plugin:schedule-calendar:manage`。
- `scheduleentries` 属于插件内部管理数据面，控制台和编辑器能力基于这组接口工作，但不建议将其当作对外集成契约。

参考 Halo 文档：

- Finder API：<https://docs.halo.run/developer-guide/theme/finder-apis/>
- 为主题提供数据（自定义 Finder）：<https://docs.halo.run/developer-guide/plugin/api-reference/server/finder-for-theme>
- RESTful API 介绍：<https://docs.halo.run/developer-guide/restful-api/introduction>

## 开发环境

- JDK 21+
- Node 20+
- pnpm 9

## 本地构建

```bash
./gradlew.bat build
```

构建产物默认输出到：

```text
build/libs
```
