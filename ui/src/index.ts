import { definePlugin } from '@halo-dev/console-shared'
import HomeView from './views/HomeView.vue'
import { IconToolsFill } from '@halo-dev/components'
import { markRaw } from 'vue'
import { ScheduleCardExtension } from './editor/schedule-card-extension'

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
            icon: markRaw(IconToolsFill),
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
