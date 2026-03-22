# halo-plugin-schedule-calendar

Halo 日程日历插件。

当前版本包含三部分能力：

1. 前台公开页面路由 `/schedule-calendar`，默认展示本周的已占用和空闲时间段。
2. Console 控制台页面 `/console/schedule-calendar`，用于录入和删除事项时间段。
3. 默认编辑器斜杠命令“插入日程卡片”，可插入单个事项的日期时间卡片。

## 开发环境

- JDK 21+
- Node 20+
- pnpm 9（模板默认）

## 本地运行

```bash
./gradlew.bat build
```

如果你使用 Halo 源码开发模式，在 Halo 配置里加入：

```yaml
halo:
  plugin:
    runtime-mode: development
    fixedPluginPath:
      - "C:/Users/Sunny/halo-plugin-schedule-calendar"
```

## GitHub 同步

当前仓库保留了模板来源远端 `upstream`。创建你自己的 GitHub 仓库后执行：

```bash
git remote add origin https://github.com/<your-account>/halo-plugin-schedule-calendar.git
git push -u origin main
```
