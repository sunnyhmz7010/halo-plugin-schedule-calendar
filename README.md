# halo-plugin-schedule-calendar

Halo 日程日历插件。

## 功能概览

1. 提供前台公开页面路由 `/schedule-calendar`，用于展示周视图日历。
2. 提供控制台页面，用于新增、查看和管理事项时间段。
3. 提供编辑器日程卡片，可插入单个事项的日期时间信息。
4. 提供插件设置页，可配置前台路由页面标题。

## 对外能力说明

当前插件已经提供以下能力：

- 前台公开页面：`/schedule-calendar`
- 插件 REST API：
  - `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/calendar/week`
  - `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/calendar/entries/{name}`
- 自定义模型 CRUD：
  - `ScheduleEntry` 作为 Halo 自定义模型，可通过 Halo Extension API 访问

当前插件已经提供 Halo 主题模板可直接调用的 Finder API：

- Finder 名称：`scheduleCalendarFinder`
- 可用方法：
  - `scheduleCalendarFinder.week(start)`
  - `scheduleCalendarFinder.get(name)`
  - `scheduleCalendarFinder.listAll()`

其中：

- `week(start)`：
  - 参数 `start` 使用 `yyyy-MM-dd` 格式
  - 返回该日期所在周的周视图数据
  - 传空值时返回当前周
- `get(name)`：
  - 按事项名称返回单个事项卡片数据
- `listAll()`：
  - 返回全部事项卡片数据列表

主题模板示例：

```html
<div th:with="week=${scheduleCalendarFinder.week('2026-04-01')}">
  <div th:text="${week.weekStart}"></div>
</div>

<div th:each="entry : ${scheduleCalendarFinder.listAll()}">
  <span th:text="${entry.title}"></span>
</div>
```

如果主题侧只需要消费 JSON，也可以继续使用现有 REST API。

参考 Halo 文档：

- Finder API：<https://docs.halo.run/developer-guide/theme/finder-apis/>
- 为主题提供数据（自定义 Finder）：<https://docs.halo.run/developer-guide/plugin/api-reference/server/finder-for-theme>
- RESTful API 介绍：<https://docs.halo.run/developer-guide/restful-api/introduction>

## 当前版本

当前预发布版本：`v1.1.0-beta.4`

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

## Halo 开发模式加载

如果你使用 Halo 源码开发模式，可在 Halo 配置中加入：

```yaml
halo:
  plugin:
    runtime-mode: development
    fixedPluginPath:
      - "C:/Users/Sunny/halo-plugin-schedule-calendar"
```
