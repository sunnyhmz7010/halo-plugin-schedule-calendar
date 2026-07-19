<div align="center">
  <img src="https://raw.githubusercontent.com/sunnyhmz7010/halo-plugin-schedule-calendar/refs/heads/main/src/main/resources/logo.svg" alt="日程日历 Logo" width="120" />
  <h1>日程日历</h1>
  <p>为 Halo 博客系统补上一套完整的日程日历能力，包含前台页面、控制台管理和编辑器卡片</p>
</div>

<p align="center">
  <a href="https://github.com/sunnyhmz7010/halo-plugin-schedule-calendar/releases"><img src="https://img.shields.io/github/v/release/sunnyhmz7010/halo-plugin-schedule-calendar?label=Release&color=3b82f6" alt="Release" /></a>
  <a href="https://github.com/sunnyhmz7010/halo-plugin-schedule-calendar/blob/main/LICENSE"><img src="https://img.shields.io/github/license/sunnyhmz7010/halo-plugin-schedule-calendar?color=10b981" alt="License" /></a>
</p>

<p align="center">
  <a href="https://sunnyhmz.top/schedule-calendar">在线预览</a> ·
  <a href="https://github.com/sunnyhmz7010/halo-plugin-schedule-calendar/releases">下载发布包</a> ·
  <a href="https://github.com/sunnyhmz7010/halo-plugin-schedule-calendar/issues">反馈问题</a>
</p>

---

## ✨ 为什么做这个插件

Halo 默认并没有提供一套完整的日程日历能力。这个插件的目标，就是给 Halo 直接补上一套可用的日程系统。

- 给站点新增可直接访问的公开日历页面
- 给控制台新增本地事项管理、外部日历订阅、循环规则和备份恢复能力
- 给编辑器新增可插入文章的日程卡片
- 给主题和自定义前端新增可直接读取的 Finder、REST API 与公开 iCal 订阅

## 📸 截图预览

<p align="center">
  <img src="https://raw.githubusercontent.com/sunnyhmz7010/halo-plugin-schedule-calendar/refs/heads/main/schedule-calendar-home.png" alt="日程日历首页预览" />
</p>

## 🚀 核心能力

### 🌐 前台公开页面

- 提供公开页面路由 `GET /schedule-calendar`
- 提供公开 iCal 订阅路由 `GET /schedule-calendar.ics`
- 提供单事项卡片页 `GET /schedule-calendar/cards/{name}`
- 支持本地事项与外部日历订阅在周视图和事项视图中统一展示
- 支持当前状态、下一个事项倒计时和当前时间线提示
- 公开 iCal 订阅仅导出本地事项，不包含外部日历订阅数据

### 🛠️ 控制台事项管理

- 提供本地事项新增、编辑、删除
- 支持本地事项启用/停用
- 支持每日、每周、每月等循环规则
- 支持只读/管理权限区分
- 支持插件设置、本地事项与外部日历订阅的备份导出、导入恢复
- 支持批量导入本地事项，通过文本格式一次性添加多条日程

### 📥 外部日历订阅

- 支持添加 Google Calendar 等工具导出的公开 iCal 地址
- 支持外部日历订阅新增、编辑、删除、启用/停用
- 支持手动刷新外部日历订阅缓存
- 外部日历订阅会显示在后台周历和前台公开页面中
- 外部日历订阅不写入公开 iCal 订阅

### 🧩 编辑器日程卡片

- 在文章内容中插入已有本地事项或外部日历事项
- 自动展示事项时间、地点、说明和循环摘要
- 适合做课程表、活动安排、预约说明、固定栏目时间卡片

### 🔌 主题与自定义前端集成

- 内置 `scheduleCalendarFinder`
- 提供公开 REST API
- Finder 和 REST 返回的数据围绕同一套周视图、摘要和事项模型组织

## ⚡ 快速开始

### 📋 前置要求

- Halo `>= 2.25.0`
- JDK `21`

### 📦 安装与运行

1. 从 [Releases](https://github.com/sunnyhmz7010/halo-plugin-schedule-calendar/releases) 下载插件 `jar`
2. 在 Halo 控制台安装插件
3. 启用后访问 `/schedule-calendar`
4. 打开控制台中的"日程日历"开始维护事项

## 📖 使用说明

### 公开页面

插件启用后即可访问：

```text
/schedule-calendar
```

如果要订阅本地事项的公开 iCal，可使用：

```text
/schedule-calendar.ics
```

该订阅地址仅包含本地事项，不包含外部日历订阅数据。

如果要嵌入单个事项卡片，可使用：

```text
/schedule-calendar/cards/{name}
```

### 批量导入

在控制台"本地事项"区域，点击"批量导入"按钮，通过文本格式一次性添加多条日程。

**格式说明**：

- 每条以「标题：」开头，字段按行填写，空行分隔不同日程
- 字段名/冒号均支持中英文
- 日期：`2026-07-02`、`2026/07/02`、`2026年7月2日`、`07-02`（补今年）
- 时间：`10:00` 或 `10：00`
- 颜色：`#3b82f6` 或 `3b82f6`，大小写均可
- 说明支持多行，缩进内容自动合并

**示例**：

```text
标题：团队周会
开始：2026-07-02 10:00
结束：2026-07-02 11:00
地点：会议室A
说明：讨论Q3计划
    参与人：张三、李四

标题：项目评审
开始：2026-07-03 14:00
结束：2026-07-03 16:00
颜色：#FF6B6B
```

### 主题 Finder

主题模板可直接使用以下 Finder：

- `scheduleCalendarFinder.week(start)`
- `scheduleCalendarFinder.summary()`
- `scheduleCalendarFinder.day(date)`
- `scheduleCalendarFinder.range(start, end)`
- `scheduleCalendarFinder.upcoming(limit)`
- `scheduleCalendarFinder.get(name)`
- `scheduleCalendarFinder.listAll()`

示例：

```html
<div th:with="week=${scheduleCalendarFinder.week('2026-04-01')}">
  <div th:text="${week.weekStart}"></div>
  <div th:text="${week.summary.current.text}"></div>
</div>

<div th:each="item : ${scheduleCalendarFinder.upcoming(5)}">
  <span th:text="${item.title}"></span>
  <time th:text="${item.startTime}"></time>
</div>

<div th:with="summary=${scheduleCalendarFinder.summary()}">
  <span th:text="${summary.next.text}"></span>
</div>
```

Finder 适合直接在 Halo 主题中读取：

- 当前周完整周视图
- 当前状态与下一个事项摘要
- 指定日期的时间块
- 指定区间内展开后的事项发生记录
- 单事项卡片或全量事项列表

### REST API

如果你更习惯在主题外部、独立前端或脚本里消费 JSON，可使用这些公开接口：

```text
GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/weeks?start=2026-04-01
GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/summary
GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/days?date=2026-04-01
GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/occurrences?start=2026-04-01&end=2026-04-07
GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/upcoming?limit=10
GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/entrycards
GET /apis/api.schedule.calendar.sunny.dev/v1alpha1/entrycards/{name}
```

其中：

- `weeks` 返回周视图，并附带 `serverTime`、`zoneId`、`summary`
- `summary` 返回当前状态和下一个事项摘要
- `days` 返回单日占用时间块与空闲时间块
- `occurrences` 返回区间内展开后的实际发生记录
- `upcoming` 返回未来事项
- `entrycards` 返回基础事项卡片数据

## 🧠 功能细节

### 🗓️ 周历体验

- 周视图和事项视图可切换
- 本地事项和外部日历订阅会在同一套周历中展示
- 会根据服务端时间计算当前状态
- 会显示下一个事项距离开始还有多久
- 会在当天列中标出当前时间线

### 📝 事项模型

- 支持标题、时间、地点、说明
- 支持启用/停用状态
- 支持循环频率、循环间隔、截止日期
- 循环事项会在周视图、区间查询和未来事项列表中按真实发生时间展开

### 📦 备份恢复

- 支持导出插件设置、本地事项和外部日历订阅
- 支持恢复时新增、更新、删除本地事项
- 支持恢复时新增、更新、删除外部日历订阅
- 恢复完成后会提示本地事项和外部日历订阅各自的新增、更新、删除数量

### 🔐 权限与维护

- 匿名访客可访问公开页面和公开 API
- 控制台支持查看权限与管理权限区分
- 管理权限可进行本地事项和外部日历订阅的新增、编辑、删除、刷新、备份和恢复

## 🧱 技术栈

- 运行平台：Halo `>= 2.25.0`
- 后端：Java 21、Gradle、Halo Plugin API
- 控制台前端：Vue 3、TypeScript、Vite、Halo UI Components
- 编辑器集成：Halo Rich Text Editor 扩展
- 对外集成：Finder API、REST API、iCal/ICS 订阅
- 构建方式：Gradle 多模块构建，`ui/` 前端产物打包进插件控制台资源

## 🗂️ 项目结构

```
halo-plugin-schedule-calendar/
├─ src/main/java/run/halo/schedule/calendar/  # 插件后端、公开页面、REST API、Finder、查询与备份逻辑
├─ src/main/resources/                        # plugin.yaml、插件资源和 Halo 扩展配置
│  └─ extensions/                             # 权限模板、插件设置 schema 等扩展定义
├─ ui/                                        # 控制台前端与编辑器扩展源码
│  └─ src/
│     ├─ views/                               # 控制台页面
│     ├─ editor/                              # 编辑器日程卡片扩展
│     ├─ utils/                               # 前端工具函数
│     └─ types/                               # TypeScript 类型声明
├─ gradle/                                    # Gradle Wrapper 支持文件
├─ build.gradle                               # 后端依赖、Halo 插件构建和前端资源打包配置
├─ gradle.properties                          # 插件版本等 Gradle 属性
└─ settings.gradle                            # Gradle 多模块设置
```

## 👨‍💻 本地开发

### 🧰 环境

- JDK `21+`
- Node `20+`
- pnpm `9`

### 🔨 构建插件

```bash
./gradlew.bat build
```

构建产物默认输出到：

```text
build/libs
```

### 🎨 前端开发

```bash
cd ui
pnpm install
pnpm build
pnpm test:unit
```

## 🔐 安全报告

如果发现安全问题，请不要公开披露细节。请优先参考仓库中的 [SECURITY.md](./SECURITY.md) 提交安全报告。

## 📄 许可证

本项目基于 [GPL-3.0](./LICENSE) 开源。

## ⭐ 星标历史

<a href="https://www.star-history.com/?repos=sunnyhmz7010%2Fhalo-plugin-schedule-calendar&type=date&legend=top-left">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=sunnyhmz7010/halo-plugin-schedule-calendar&type=date&theme=dark&legend=top-left&sealed_token=Zyhp4IHfNoi5QeK6P6pe5FeqRTdvxnAjzuEy1lsgFXCHdgKlE9RbBzlE6_YI58N9LOKy9XaCIkHq1qkusmlXEyCPrjdzxgYTKC7GLnr3SYcUjWN_CLL_2Kk4CruK0wHch1UTnSG2TRYqUkTP7upSkbURj8W5lg_WHcfiST78Iw0nlel4TO0FAjZOvcLR" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=sunnyhmz7010/halo-plugin-schedule-calendar&type=date&legend=top-left&sealed_token=Zyhp4IHfNoi5QeK6P6pe5FeqRTdvxnAjzuEy1lsgFXCHdgKlE9RbBzlE6_YI58N9LOKy9XaCIkHq1qkusmlXEyCPrjdzxgYTKC7GLnr3SYcUjWN_CLL_2Kk4CruK0wHch1UTnSG2TRYqUkTP7upSkbURj8W5lg_WHcfiST78Iw0nlel4TO0FAjZOvcLR" />
    <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=sunnyhmz7010/halo-plugin-schedule-calendar&type=date&legend=top-left&sealed_token=Zyhp4IHfNoi5QeK6P6pe5FeqRTdvxnAjzuEy1lsgFXCHdgKlE9RbBzlE6_YI58N9LOKy9XaCIkHq1qkusmlXEyCPrjdzxgYTKC7GLnr3SYcUjWN_CLL_2Kk4CruK0wHch1UTnSG2TRYqUkTP7upSkbURj8W5lg_WHcfiST78Iw0nlel4TO0FAjZOvcLR" />
  </picture>
</a>

<div align="center">
  <sub>Built with ❤️ by Sunny</sub>
</div>
