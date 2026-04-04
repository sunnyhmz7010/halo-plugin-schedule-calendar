import { axiosInstance } from '@halo-dev/api-client'
import { Toast } from '@halo-dev/components'
import { ToolboxItem } from '@halo-dev/richtext-editor'
import { mergeAttributes, Node } from '@tiptap/core'
import type { Editor, Range } from '@tiptap/core'
import { markRaw } from 'vue'
import MdiCalendarClockOutline from '~icons/mdi/calendar-clock-outline'
import type { ScheduleCard } from '../types/schedule'
import { openScheduleCardPicker } from './schedule-card-picker'

const CARD_API = '/apis/api.schedule.calendar.sunny.dev/v1alpha1/calendar/entries'

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

const resolveSchedulePageUrl = () => `${window.location.origin}/console/tools/schedule-calendar`

const openSchedulePage = () => {
  window.open(resolveSchedulePageUrl(), '_blank', 'noopener,noreferrer')
}

const loadEntries = async () => {
  const { data } = await axiosInstance.get<ScheduleCard[]>(CARD_API)
  return data ?? []
}

const updateCardAttrs = (editor: Editor, position: number, attrs: Partial<ScheduleCard>) => {
  const currentNode = editor.state.doc.nodeAt(position)
  if (!currentNode) {
    return
  }

  editor.view.dispatch(
    editor.state.tr.setNodeMarkup(position, undefined, {
      ...currentNode.attrs,
      ...attrs,
    }),
  )
}

const openPickerForCard = async (editor: Editor, position: number) => {
  try {
    const items = await loadEntries()
    const card = await openScheduleCardPicker(items, {
      onCreate: openSchedulePage,
    })

    if (!card) {
      return
    }

    updateCardAttrs(editor, position, card)
  } catch (error) {
    console.error(error)
    Toast.error('读取事项列表失败，请稍后重试。')
  }
}

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

  addNodeView() {
    return ({ editor, node, getPos }) => {
      let currentNode = node
      const dom = document.createElement('div')

      const createButton = (label: string, primary = false) => {
        const button = document.createElement('button')
        button.type = 'button'
        button.textContent = label
        button.style.cssText = [
          'padding:8px 14px',
          'border-radius:999px',
          'border:1px solid rgba(15,23,42,0.12)',
          primary ? 'background:#111827' : 'background:#ffffff',
          primary ? 'color:#ffffff' : 'color:#111827',
          'font-size:13px',
          'font-weight:600',
          'cursor:pointer',
        ].join(';')
        return button
      }

      const render = () => {
        const attrs = currentNode.attrs as ScheduleCard
        const hasSelectedEntry = Boolean(attrs.name)

        dom.innerHTML = ''
        dom.setAttribute('data-type', 'schedule-card')
        dom.style.cssText = [
          'padding:18px',
          'border-radius:18px',
          `border-left:6px solid ${attrs.color || '#0f766e'}`,
          'background:linear-gradient(135deg, rgba(15,118,110,0.12), rgba(255,255,255,0.98))',
          'box-shadow:0 1px 2px rgba(15,23,42,0.05)',
        ].join(';')

        const badge = document.createElement('div')
        badge.textContent = '日程卡片'
        badge.style.cssText =
          'font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:0.08em;'

        const title = document.createElement('div')
        title.textContent = hasSelectedEntry ? attrs.title : '未选择日程'
        title.style.cssText = 'margin-top:6px;font-size:18px;font-weight:700;color:#111827;'

        const summary = document.createElement('div')
        summary.textContent = hasSelectedEntry
          ? `${attrs.startTime || ''} - ${attrs.endTime || ''}`.trim()
          : '先插入占位卡片，再从卡片内部选择已有事项，或前往添加新事项。'
        summary.style.cssText = 'margin-top:8px;font-size:13px;line-height:1.6;color:#374151;'

        const extras = document.createElement('div')
        extras.style.cssText =
          'display:flex;flex-direction:column;gap:4px;margin-top:8px;font-size:13px;line-height:1.6;color:#6b7280;'

        if (hasSelectedEntry && attrs.recurrenceDescription) {
          const recurrence = document.createElement('div')
          recurrence.textContent = attrs.recurrenceDescription
          recurrence.style.cssText = 'color:#0f766e;font-weight:600;'
          extras.appendChild(recurrence)
        }

        if (hasSelectedEntry && attrs.location) {
          const location = document.createElement('div')
          location.textContent = `地点：${attrs.location}`
          extras.appendChild(location)
        }

        if (hasSelectedEntry && attrs.description) {
          const description = document.createElement('div')
          description.textContent = `备注：${attrs.description}`
          extras.appendChild(description)
        }

        const actions = document.createElement('div')
        actions.style.cssText = 'display:flex;flex-wrap:wrap;gap:10px;margin-top:14px;'

        const selectButton = createButton(hasSelectedEntry ? '更换日程' : '选择日程', true)
        selectButton.addEventListener('click', () => {
          const position = typeof getPos === 'function' ? getPos() : null
          if (typeof position !== 'number') {
            return
          }

          void openPickerForCard(editor, position)
        })

        const createEntryButton = createButton('去添加事项')
        createEntryButton.addEventListener('click', () => {
          openSchedulePage()
        })

        actions.append(selectButton, createEntryButton)

        dom.append(badge, title, summary)
        if (extras.childNodes.length > 0) {
          dom.appendChild(extras)
        }
        dom.appendChild(actions)
      }

      render()

      return {
        dom,
        update(updatedNode: typeof node) {
          currentNode = updatedNode
          render()
          return true
        },
        stopEvent(event: Event) {
          return event.target instanceof HTMLElement && Boolean(event.target.closest('button'))
        },
        ignoreMutation() {
          return true
        },
      }
    }
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
          : '先插入占位卡片，再从卡片内部选择已有事项，或前往添加新事项。',
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
