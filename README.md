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

当前插件已经提供以下能力：

- 前台公开页面：`/schedule-calendar`
- 插件 REST API：
  - `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/calendar/week`
  - `GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/calendar/entries/{name}`
- 自定义模型 CRUD：
  - `ScheduleEntry` 作为 Halo 自定义模型，可通过 Halo Extension API 访问
- Finder API：
  - `scheduleCalendarFinder.week(start)`
  - `scheduleCalendarFinder.get(name)`
  - `scheduleCalendarFinder.listAll()`

其中：

- `week(start)`：
  - 参数 `start` 使用 `yyyy-MM-dd` 格式
  - 返回该日期所在周的完整周视图数据
  - 传空值时返回当前周
- `get(name)`：
  - 按事项名称返回单个事项卡片数据，适合文章页或详情区块单独调用
- `listAll()`：
  - 返回全部事项卡片数据列表，适合主题中做列表展示或二次筛选

主题模板示例：

```html
<div th:with="week=${scheduleCalendarFinder.week('2026-04-01')}">
  <div th:text="${week.weekStart}"></div>
</div>

<div th:each="entry : ${scheduleCalendarFinder.listAll()}">
  <span th:text="${entry.title}"></span>
</div>
```

如果主题侧更习惯消费 JSON，也可以继续直接使用现有 REST API。

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

## Halo 开发模式加载

如果你使用 Halo 源码开发模式，可在 Halo 配置中加入：

```yaml
halo:
  plugin:
    runtime-mode: development
    fixedPluginPath:
      - "C:/Users/Sunny/halo-plugin-schedule-calendar"
```
