import { definePlugin } from '@halo-dev/console-shared'
import HomeView from './views/HomeView.vue'
import BackupTab from './views/BackupTab.vue'
import SettingsTab from './views/SettingsTab.vue'
import { IconCalendar } from '@halo-dev/components'
import { markRaw } from 'vue'
import { ScheduleCardExtension } from './editor/schedule-card-extension'

const viewPermissions = ['plugin:schedule-calendar:view']
const managePermissions = ['plugin:schedule-calendar:manage']

const openScheduleCalendarConsole = () => {
  window.location.assign(new URL('/console/schedule-calendar', window.location.origin).toString())
}

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'ToolsRoot',
      route: {
        path: '/schedule-calendar',
        name: 'ScheduleCalendar',
        component: HomeView,
        meta: {
          title: '日程日历',
          searchable: true,
          permissions: viewPermissions,
          menu: {
            name: '日程日历',
            icon: markRaw(IconCalendar),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {
    'default:editor:extension:create': () => [ScheduleCardExtension],
    'plugin:self:tabs:create': () => [
      {
        id: 'schedule-calendar-settings',
        label: '前台页面',
        component: markRaw(SettingsTab),
        permissions: managePermissions,
      },
      {
        id: 'schedule-calendar-backup',
        label: '数据备份',
        component: markRaw(BackupTab),
        permissions: managePermissions,
      },
    ],
    'console:dashboard:widgets:internal:quick-action:item:create': () => [
      {
        id: 'schedule-calendar-quick-action',
        icon: markRaw(IconCalendar),
        title: '日程日历',
        action: openScheduleCalendarConsole,
        permissions: viewPermissions,
      },
    ],
  },
})
