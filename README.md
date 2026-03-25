# halo-plugin-schedule-calendar

Halo 日程日历插件。

## 功能概览

1. 提供前台公开页面路由 `/schedule-calendar`，用于展示周视图日历。
2. 提供控制台页面，用于新增、查看和管理事项时间段。
3. 提供编辑器日程卡片，可插入单个事项的日期时间信息。
4. 提供插件设置页，可配置前台路由页面标题。

## 当前版本

当前预发布版本：`v1.0.0-beta.2`

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

## 发布说明

- GitHub Release 标签与插件构建版本保持一致。
- 正式版本和预发布版本都会以中文更新日志记录在 `CHANGELOG.md` 中。
