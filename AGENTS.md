# AGENTS.md

## 项目概况

| 维度 | 内容 |
|------|------|
| 产品定位 | Halo 日程日历插件，非通用日历库 |
| 核心功能 | 公开日历查看、控制台日程管理、编辑器日程卡片、主题/API 集成 |
| 当前稳定版本 | `v3.1.0` |

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21、Gradle、Halo Plugin API |
| 前端 | Vue 3、TypeScript、Vite |

### 关键路径

| 用途 | 路径 |
|------|------|
| 前端源码 | `ui/` |
| 后端源码 | `src/main/java/run/halo/schedule/calendar/` |
| 版本号 | `gradle.properties` |
| 插件元数据 | `src/main/resources/plugin.yaml` |
| 角色模板 | `src/main/resources/extensions/roleTemplate.yaml` |
| 设置 Schema | `src/main/resources/extensions/settings.yaml` |
| 主管理页 | `ui/src/views/HomeView.vue` |
| 日程模型 | `src/main/java/run/halo/schedule/calendar/ScheduleEntry.java` |
| 周查询逻辑 | `src/main/java/run/halo/schedule/calendar/ScheduleQueryService.java` |
| 编辑器卡片 | `ui/src/editor/schedule-card-extension.ts` |

### 路由

| 路由 | 用途 |
|------|------|
| `/schedule-calendar` | 公开日历页 |
| `/schedule-calendar.ics` | iCal 订阅 |
| `/schedule-calendar/cards/{name}` | 公开卡片 |
| `/apis/api.schedule.calendar.sunny.dev/v1alpha1` | REST API |

## 架构约束

### 日程条目规则

- 基础数据模型存储单条源条目，重复条目不在存储中复制
- 重复规则在渲染/查询周视图时动态展开
- 支持频率：`DAILY`、`WEEKLY`、`MONTHLY`、`YEARLY`
- 重复字段位于 `ScheduleEntry.Spec.recurrence`：`frequency`、`interval`、`until`
- 管理表单禁止跨天重复条目

### 产品约束

- 公开页面标题可在插件设置中配置，但公开页面和 iCal 订阅路由固定
- Halo 原生插件设置优先于自定义设置页
- 外部日历 MVP 使用 Halo 原生插件设置配置 ICS 订阅源
- 外部日历事件为只读，合并到公开页面、Finder 和 REST 查询结果
- 不要将外部 ICS 事件混入本地日程 CRUD 或编辑器卡片选择

### 权限规则

- `roleTemplate.yaml` 是 Halo 角色聚合的唯一来源
- `manage` 角色必须包含实际的 `scheduleentries` 读写权限
- 前端权限探测优先使用无副作用的真实资源请求
- 控制台入口保持可见，页面内通过运行时能力探测执行权限

### 渲染规范

- 地点和备注同时显示时，地点在前、备注在后
- 可见日历块元信息使用标记多行文本：`地点：...` 然后 `备注：...`
- 悬停提示元信息使用空格分隔文本
- 公开页面和管理页面使用相同的元信息排序和标记规则
- 日历块内容优先顶部对齐，短块使用保守的可见行计算

## 开发规范

### 命令速查

| 场景 | 命令 |
|------|------|
| 前端构建验证 | `cd ui && npm run build` |
| 后端构建 | `./gradlew.bat build` |
| 后端测试 | `./gradlew.bat test` |

### 环境约束

- 项目需要 Java 21 工具链
- 如果机器只有 Java 8 或 Java 25，Gradle 验证可能在测试前失败

### 依赖管理

- 不要添加 `@halo-dev/ui-shared` 读取 UI 权限，会引入额外依赖
- `unplugin-icons` 非必需，优先使用 `@halo-dev/components` 提供的图标
- `ui/env.d.ts` 不应保留 `unplugin-icons` 类型引用
- `pnpm.overrides` 可用于固定有漏洞的传递依赖

### XSS 防护

- 公开日历渲染不要将日程数据插入 `innerHTML`
- 使用 `textContent`/`createElement` 构建 DOM 节点

## 移植性/踩坑经验

### 控制台入口排查

- 如果控制台菜单项和插件自标签同时消失，先检查插件状态：
  - `halo plugin get schedule-calendar --json`
  - 如果 `status.phase` 是 `STARTED` 且 `status.entry` 存在，插件已启动
- 如果插件 `STARTED` 但入口仍缺失，优先怀疑 Halo UI 权限聚合或前端路由可见性规则
- 比较发布产物时至少验证：
  - `console/main.js`
  - `console/style.css`
  - `META-INF/plugin-components.idx`
  - `extensions/roleTemplate.yaml`
- 认证控制台包检查时，`/apis/api.console.halo.run/v1alpha1/plugins/-/bundle.js` 无认证会返回登录页

### 构建注意事项

- 不要在 `ui/build.gradle` 中保留本地 Gradle 变通方案
- `gradle/wrapper/` 是正常的项目基础设施

## 版本历史

### 版本标签格式

| 类型 | 格式 |
|------|------|
| 稳定版 | `v1.2.0` |
| 预发布 | `v1.2.1-beta.1` |

### 发布检查清单

发布时需同步更新：
- `gradle.properties` 版本号
- `README.md`（如截图、URL 或功能描述有变）

### README 同步规则

- `WeekViewResponse`、Finder 返回结构或公开 `weeks` API 字段变更时，同时更新 Finder 和 REST API 文档
- `SummaryResponse` 或周页面摘要字段变更时，更新服务端渲染公开页面脚本
- 公开页面 UX 添加用户可见功能时，反映到 `README.md` 功能概览和对外能力说明

### 发布流程

- GitHub Actions 发布构建由 `Release published` 事件触发
- 预发布优先创建 GitHub prerelease，让 CI 构建并上传 jar
- 如需"覆盖"现有版本，可删除并重新创建 GitHub Release/tag
- 如需删除整个 beta 系列，在创建稳定标签前删除 GitHub prereleases 及其标签

### 稳定功能集

- 支持重复日程条目及动态周展开
- 支持控制台条目编辑、删除和重复配置
- 支持 Finder API 供 Halo 主题使用
- 阻止跨天重复条目，稳定 24 小时公开渲染
- 统一管理/公开元信息排序
- 自适应日历块文本渲染减少短块裁剪
- 支持批量导入本地事项，兼容中英文字段名/冒号、多种日期格式、颜色大小写
