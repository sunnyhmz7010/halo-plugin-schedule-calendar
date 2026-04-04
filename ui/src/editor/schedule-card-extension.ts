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
    const hasSelectedEntry = Boolean(HTMLAttributes.name)
    const name = String(HTMLAttributes.name ?? '')
    const iframeSrc = `/schedule-calendar/cards/${encodeURIComponent(name)}`

    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-type': 'schedule-card',
        'data-name': HTMLAttributes.name,
        style: hasSelectedEntry
          ? 'border-radius:12px;overflow:hidden;'
          : 'padding:18px;border-radius:12px;border:1px solid #e5e7eb;background:#ffffff;',
      }),
      ...(hasSelectedEntry
        ? [[
            'iframe',
            {
              src: iframeSrc,
              loading: 'lazy',
              style: 'display:block;width:100%;min-height:160px;border:0;background:transparent;',
              onload:
                "try{this.style.height=Math.max(this.contentWindow.document.documentElement.scrollHeight,160)+'px';}catch(e){}",
            },
          ]]
        : [
            [
              'div',
              { style: 'font-size:16px;font-weight:700;color:#111827;' },
              '日程卡片',
            ],
            [
              'div',
              { style: 'margin-top:8px;font-size:13px;color:#6b7280;line-height:1.6;' },
              '选择已有事项，或直接在这里添加一个新的事项。',
            ],
          ]),
    ]
  },
})
