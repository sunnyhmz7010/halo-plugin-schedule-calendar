import { ToolboxItem, VueNodeViewRenderer } from '@halo-dev/richtext-editor'
import { mergeAttributes, Node } from '@tiptap/core'
import type { Editor, Range } from '@tiptap/core'
import { markRaw } from 'vue'
import MdiCalendarClockOutline from '~icons/mdi/calendar-clock-outline'
import type { ScheduleCard } from '../types/schedule'
import ScheduleCardView from './ScheduleCardView.vue'

const createEmptyCard = (): ScheduleCard => ({
  name: '',
  title: '',
  description: '',
  location: '',
  startTime: '',
  endTime: '',
  recurrenceDescription: '',
  color: '#0f766e',
})

const insertCard = (editor: Editor, range?: Range) => {
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
      attrs: createEmptyCard(),
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
            component: markRaw(ToolboxItem),
            props: {
              editor,
              icon: markRaw(MdiCalendarClockOutline),
              title: '日程卡片',
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
          title: '日程卡片',
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

  addNodeView() {
    return VueNodeViewRenderer(ScheduleCardView)
  },

  renderHTML({ HTMLAttributes }) {
    const description = HTMLAttributes.description as string | undefined
    const location = HTMLAttributes.location as string | undefined
    const recurrenceDescription = HTMLAttributes.recurrenceDescription as string | undefined
    const hasSelectedEntry = Boolean(HTMLAttributes.name)

    const children: unknown[] = [
      ['div', { style: 'font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:0.08em;' }, '日程卡片'],
      [
        'div',
        { style: 'margin-top:6px;font-size:18px;font-weight:700;color:#111827;' },
        String(HTMLAttributes.title ?? (hasSelectedEntry ? '未命名事项' : '未选择日程')),
      ],
      [
        'div',
        { style: 'margin-top:8px;font-size:13px;color:#374151;line-height:1.6;' },
        hasSelectedEntry
          ? `${String(HTMLAttributes.startTime ?? '')} - ${String(HTMLAttributes.endTime ?? '')}`
          : '选择已有事项，或先去添加一个新的事项。',
      ],
    ]

    if (hasSelectedEntry && recurrenceDescription) {
      children.push([
        'div',
        { style: 'margin-top:8px;font-size:13px;color:#0f766e;font-weight:600;line-height:1.6;' },
        recurrenceDescription,
      ])
    }

    if (hasSelectedEntry && location) {
      children.push([
        'div',
        { style: 'margin-top:8px;font-size:13px;color:#6b7280;line-height:1.6;' },
        `地点：${location}`,
      ])
    }

    if (hasSelectedEntry && description) {
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
