<div align="center">
  <img src="./src/main/resources/logo.svg" alt="日程日历 Logo" width="120" />
  <h1>日程日历</h1>
  <p>提供前台日历页面、控制台事项管理和编辑器日程卡片的 Halo 插件</p>
</div>

<p align="center">
  <a href="https://github.com/sunnyhmz7010/halo-plugin-schedule-calendar/releases"><img src="https://img.shields.io/github/v/release/sunnyhmz7010/halo-plugin-schedule-calendar?label=Release&color=3b82f6" alt="Release" /></a>
  <a href="https://github.com/sunnyhmz7010/halo-plugin-schedule-calendar/blob/main/LICENSE"><img src="https://img.shields.io/github/license/sunnyhmz7010/halo-plugin-schedule-calendar?color=10b981" alt="License" /></a>
  <a href="https://github.com/sunnyhmz7010/halo-plugin-schedule-calendar/actions/workflows/ci.yaml"><img src="https://img.shields.io/github/actions/workflow/status/sunnyhmz7010/halo-plugin-schedule-calendar/ci.yaml?branch=main&label=CI" alt="CI" /></a>
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
- 给控制台新增事项管理、循环规则和备份恢复能力
- 给编辑器新增可插入文章的日程卡片
- 给主题和自定义前端新增可直接读取的 Finder 与 REST API

## 📸 截图预览

<p align="center">
  <img src="./schedule-calendar-home.png" alt="日程日历首页预览" />
</p>

## 🚀 核心能力

### 🌐 前台公开页面

- 提供公开页面路由 `GET /schedule-calendar`
- 提供单事项卡片页 `GET /schedule-calendar/cards/{name}`
- 支持周视图、事项视图、当前状态、下一个事项倒计时和当前时间线提示

### 🛠️ 控制台事项管理

- 提供事项新增、编辑、删除
- 支持每日、每周、每月等循环规则
- 支持只读/管理权限区分
- 支持插件设置与事项数据的备份导出、导入恢复

### 🧩 编辑器日程卡片

- 在文章内容中插入已有事项
- 自动展示事项时间、地点、说明和循环摘要
- 适合做课程表、活动安排、预约说明、固定栏目时间卡片

### 🔌 主题与自定义前端集成

- 内置 `scheduleCalendarFinder`
- 提供公开 REST API
- Finder 和 REST 返回的数据围绕同一套周视图、摘要和事项模型组织

## ⚡ 快速开始

### 📋 运行要求

- Halo `>= 2.23.0`
- JDK `21`

### 📦 安装

1. 从 [Releases](https://github.com/sunnyhmz7010/halo-plugin-schedule-calendar/releases) 下载插件 `jar`
2. 在 Halo 控制台安装插件
3. 启用后访问 `/schedule-calendar`
4. 打开控制台中的“日程日历”开始维护事项

## 📖 使用方式

### 1. 🌍 公开页面

插件启用后即可访问：

```text
/schedule-calendar
```

如果要嵌入单个事项卡片，可使用：

```text
/schedule-calendar/cards/{name}
```

### 2. 🔎 主题 Finder

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

### 3. 📡 REST API

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
- 会根据服务端时间计算当前状态
- 会显示下一个事项距离开始还有多久
- 会在当天列中标出当前时间线

### 📝 事项模型

- 支持标题、时间、地点、说明
- 支持循环频率、循环间隔、截止日期
- 循环事项会在周视图、区间查询和未来事项列表中按真实发生时间展开

### 🔐 权限与维护

- 匿名访客可访问公开页面和公开 API
- 控制台支持查看权限与管理权限区分
- 管理权限可进行新增、编辑、删除、备份和恢复

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

如果你发现了安全问题，请不要在公开 Issue 中直接披露细节。请优先参考仓库中的 [SECURITY.md](./SECURITY.md) 提交安全报告。

## 📄 License

本项目基于 [GPL-3.0](./LICENSE) 开源。

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=sunnyhmz7010/halo-plugin-schedule-calendar&type=Date)](https://star-history.com/#sunnyhmz7010/halo-plugin-schedule-calendar&Date)

<div align="center">
  <sub>Built with ❤️ by Sunny</sub>
</div>
