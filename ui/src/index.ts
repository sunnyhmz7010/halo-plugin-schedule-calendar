import { definePlugin } from '@halo-dev/console-shared'
import { IconCalendar } from '@halo-dev/components'
import { defineAsyncComponent, markRaw } from 'vue'

const HomeView = defineAsyncComponent(() => import('./views/HomeView.vue'))
const BackupTab = defineAsyncComponent(() => import('./views/BackupTab.vue'))

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
    'plugin:self:tabs:create': () => [
      {
        id: 'schedule-calendar-backup',
        label: '数据备份',
        component: markRaw(BackupTab),
      },
    ],
    'console:dashboard:widgets:internal:quick-action:item:create': () => [
      {
        id: 'schedule-calendar-quick-action',
        icon: markRaw(IconCalendar),
        title: '日程日历',
        action: openScheduleCalendarConsole,
      },
    ],
  },
})
