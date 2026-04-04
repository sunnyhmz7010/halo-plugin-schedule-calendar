import { axiosInstance } from '@halo-dev/api-client'
import { Toast } from '@halo-dev/components'
import { mergeAttributes, Node } from '@tiptap/core'
import type { Editor, Range } from '@tiptap/core'
import { markRaw } from 'vue'
import MdiCalendarClockOutline from '~icons/mdi/calendar-clock-outline'
import type { ScheduleCard } from '../types/schedule'
import { openScheduleCardPicker } from './schedule-card-picker'
import ScheduleCardToolboxItem from './ScheduleCardToolboxItem.vue'

const CARD_API = '/apis/api.schedule.calendar.sunny.dev/v1alpha1/calendar/entries'

const chooseEntry = async () => {
  const { data } = await axiosInstance.get<ScheduleCard[]>(CARD_API)
  const items = data ?? []

  if (!items.length) {
    Toast.warning('请先在日程日历插件页面添加事项。')
    return null
  }

  return openScheduleCardPicker(items)
}

const insertCard = async (editor: Editor, range?: Range) => {
  try {
    const card = await chooseEntry()
    if (!card) {
      return
    }

    const insertRange = range ?? {
      from: editor.state.selection.from,
      to: editor.state.selection.to,
    }

    editor
      .chain()
      .focus()
      .deleteRange(insertRange)
      .insertContent({
        type: 'scheduleCard',
        attrs: card,
      })
      .run()
  } catch (error) {
    console.error(error)
    Toast.error('读取事项列表失败，请稍后重试。')
  }
}

export const ScheduleCardExtension = Node.create({
  name: 'scheduleCard',
  group: 'block',
  atom: true,
  draggable: true,
  selectable: true,

  addAttributes() {
    return {
      name: { default: '' },
      title: { default: '' },
      description: { default: '' },
      location: { default: '' },
      startTime: { default: '' },
      endTime: { default: '' },
      recurrenceDescription: { default: '' },
      color: { default: '#0f766e' },
    }
  },

  addOptions() {
    return {
      ...this.parent?.(),
      getToolboxItems({ editor }: { editor: Editor }) {
        return [
          {
            priority: 35,
            component: markRaw(ScheduleCardToolboxItem),
            props: {
              editor,
              icon: markRaw(MdiCalendarClockOutline),
              title: '插入日程卡片',
              description: '选择一个事项并插入为卡片',
              action: () => {
                void insertCard(editor)
              },
            },
          },
        ]
      },
      getCommandMenuItems() {
        return {
          priority: 140,
          icon: markRaw(MdiCalendarClockOutline),
          title: '插入日程卡片',
          keywords: ['schedule', 'calendar', 'rili', '日程', '日历'],
          command: ({ editor, range }: { editor: Editor; range: Range }) => {
            void insertCard(editor, range)
          },
        }
      },
    }
  },

  parseHTML() {
    return [{ tag: 'div[data-type="schedule-card"]' }]
  },

  renderHTML({ HTMLAttributes }) {
    const description = HTMLAttributes.description as string | undefined
    const location = HTMLAttributes.location as string | undefined
    const recurrenceDescription = HTMLAttributes.recurrenceDescription as string | undefined

    const children: unknown[] = [
      ['div', { style: 'font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:0.08em;' }, '日程卡片'],
      ['div', { style: 'margin-top:6px;font-size:18px;font-weight:700;color:#111827;' }, String(HTMLAttributes.title ?? '未命名事项')],
      [
        'div',
        { style: 'margin-top:8px;font-size:13px;color:#374151;line-height:1.6;' },
        `${String(HTMLAttributes.startTime ?? '')} - ${String(HTMLAttributes.endTime ?? '')}`,
      ],
    ]

    if (recurrenceDescription) {
      children.push([
        'div',
        { style: 'margin-top:8px;font-size:13px;color:#0f766e;font-weight:600;line-height:1.6;' },
        recurrenceDescription,
      ])
    }

    if (location) {
      children.push([
        'div',
        { style: 'margin-top:8px;font-size:13px;color:#6b7280;line-height:1.6;' },
        `地点：${location}`,
      ])
    }

    if (description) {
      children.push([
        'div',
        { style: 'margin-top:4px;font-size:13px;color:#6b7280;line-height:1.6;' },
        `备注：${description}`,
      ])
    }

    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-type': 'schedule-card',
        'data-name': HTMLAttributes.name,
        style: `padding:16px 18px;border-radius:18px;border-left:6px solid ${String(
          HTMLAttributes.color ?? '#0f766e',
        )};background:linear-gradient(135deg, rgba(15,118,110,0.12), rgba(255,255,255,0.95));`,
      }),
      ...children,
    ]
  },
})
