import { definePlugin } from '@halo-dev/console-shared'
import HomeView from './views/HomeView.vue'
import { IconCalendar } from '@halo-dev/components'
import { markRaw } from 'vue'
import { ScheduleCardExtension } from './editor/schedule-card-extension'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/schedule-calendar',
        name: 'ScheduleCalendar',
        component: HomeView,
        meta: {
          title: '日程日历',
          searchable: true,
          menu: {
            name: '日程日历',
            group: '内容工具',
            icon: markRaw(IconCalendar),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {
    'default:editor:extension:create': () => [ScheduleCardExtension],
  },
})
