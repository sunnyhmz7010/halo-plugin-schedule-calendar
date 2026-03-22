import { axiosInstance } from '@halo-dev/api-client'
import { mergeAttributes, Node } from '@tiptap/core'
import type { Editor, Range } from '@tiptap/core'
import { markRaw } from 'vue'
import MdiCalendarClockOutline from '~icons/mdi/calendar-clock-outline'
import type { ExtensionListResult, ScheduleEntry } from '../types/schedule'

const ENTRY_API = '/apis/schedule.calendar.sunny.dev/v1alpha1/scheduleentries'
const CARD_API = '/apis/api.schedule.calendar.sunny.dev/v1alpha1/calendar/entries'

interface ScheduleCardPayload {
  name: string
  title: string
  description?: string
  location?: string
  startTime: string
  endTime: string
  color: string
}

const chooseEntry = async () => {
  const { data } = await axiosInstance.get<ExtensionListResult<ScheduleEntry>>(ENTRY_API, {
    params: {
      page: 1,
      size: 100,
    },
  })

  const items = data.items ?? []

  if (!items.length) {
    window.alert('请先在日程日历插件页面添加事项。')
    return null
  }

  const options = items
    .map((item, index) => {
      const start = new Date(item.spec.startTime).toLocaleString('zh-CN', {
        hour12: false,
      })
      return `${index + 1}. ${item.spec.title} (${start})`
    })
    .join('\n')

  const answer = window.prompt(
    `输入序号或事项名称以插入日程卡片：\n${options}`,
    '1',
  )

  if (!answer) {
    return null
  }

  const selectedByIndex = Number(answer)
  if (!Number.isNaN(selectedByIndex) && selectedByIndex > 0 && selectedByIndex <= items.length) {
    return items[selectedByIndex - 1]
  }

  return (
    items.find(
      (item) =>
        item.metadata.name === answer ||
        item.spec.title === answer,
    ) ?? null
  )
}

const insertCard = async (editor: Editor, range: Range) => {
  const entry = await chooseEntry()
  if (!entry) {
    return
  }

  const { data } = await axiosInstance.get<ScheduleCardPayload>(
    `${CARD_API}/${encodeURIComponent(entry.metadata.name)}`,
  )

  editor
    .chain()
    .focus()
    .deleteRange(range)
    .insertContent({
      type: 'scheduleCard',
      attrs: data,
    })
    .run()
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
      color: { default: '#0f766e' },
    }
  },

  addOptions() {
    return {
      ...this.parent?.(),
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
    const meta = [description, location ? `地点：${location}` : '']
      .filter(Boolean)
      .join(' / ')

    const children: unknown[] = [
      ['div', { style: 'font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:0.08em;' }, '日程卡片'],
      ['div', { style: 'margin-top:6px;font-size:18px;font-weight:700;color:#111827;' }, String(HTMLAttributes.title ?? '未命名事项')],
      [
        'div',
        { style: 'margin-top:8px;font-size:13px;color:#374151;' },
        `${String(HTMLAttributes.startTime ?? '')} - ${String(HTMLAttributes.endTime ?? '')}`,
      ],
    ]

    if (meta) {
      children.push(['div', { style: 'margin-top:8px;font-size:13px;color:#6b7280;line-height:1.6;' }, meta])
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
