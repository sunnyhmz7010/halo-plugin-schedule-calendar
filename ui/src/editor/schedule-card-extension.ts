import { axiosInstance } from '@halo-dev/api-client'
import { mergeAttributes, Node } from '@tiptap/core'
import type { Editor, Range } from '@tiptap/core'
import { markRaw } from 'vue'
import MdiCalendarClockOutline from '~icons/mdi/calendar-clock-outline'
import type { ScheduleCard } from '../types/schedule'

const CARD_API = '/apis/api.schedule.calendar.sunny.dev/v1alpha1/calendar/entries'

const escapeHtml = (value?: string) =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const buildCardSummary = (card: ScheduleCard) => `${card.startTime} - ${card.endTime}`

const buildCardSearchText = (card: ScheduleCard) =>
  [
    card.name,
    card.title,
    card.location,
    card.description,
    card.startTime,
    card.endTime,
    card.recurrenceDescription,
  ]
    .filter(Boolean)
    .join(' ')
    .toLocaleLowerCase()

const showEntryChooser = (items: ScheduleCard[]) =>
  new Promise<ScheduleCard | null>((resolve) => {
    const overlay = document.createElement('div')
    overlay.style.cssText = [
      'position:fixed',
      'inset:0',
      'z-index:9999',
      'display:flex',
      'align-items:center',
      'justify-content:center',
      'padding:24px',
      'background:rgba(15,23,42,0.42)',
      'backdrop-filter:blur(8px)',
    ].join(';')

    const dialog = document.createElement('div')
    dialog.style.cssText = [
      'width:min(720px,100%)',
      'max-height:min(80vh,720px)',
      'display:flex',
      'flex-direction:column',
      'overflow:hidden',
      'border-radius:24px',
      'background:#ffffff',
      'box-shadow:0 24px 60px rgba(15,23,42,0.2)',
    ].join(';')

    const header = document.createElement('div')
    header.style.cssText = 'padding:20px 20px 16px;border-bottom:1px solid rgba(15,23,42,0.08);'

    const title = document.createElement('div')
    title.textContent = '插入日程卡片'
    title.style.cssText = 'font-size:20px;font-weight:700;color:#111827;'

    const description = document.createElement('div')
    description.textContent = '搜索并选择一个事项，插入到当前文章内容中。'
    description.style.cssText = 'margin-top:6px;font-size:13px;line-height:1.5;color:#6b7280;'

    const input = document.createElement('input')
    input.type = 'search'
    input.placeholder = '搜索标题、名称、地点、备注或循环规则'
    input.style.cssText = [
      'width:100%',
      'margin-top:14px',
      'padding:12px 14px',
      'border:1px solid rgba(15,23,42,0.12)',
      'border-radius:14px',
      'outline:none',
      'font:inherit',
      'color:#111827',
      'background:#f9fafb',
    ].join(';')

    const body = document.createElement('div')
    body.style.cssText = 'padding:12px;overflow:auto;background:#f8fafc;'

    const footer = document.createElement('div')
    footer.style.cssText = [
      'display:flex',
      'align-items:center',
      'justify-content:space-between',
      'gap:12px',
      'padding:14px 20px 18px',
      'border-top:1px solid rgba(15,23,42,0.08)',
      'background:#ffffff',
    ].join(';')

    const count = document.createElement('div')
    count.style.cssText = 'font-size:13px;color:#6b7280;'

    const cancelButton = document.createElement('button')
    cancelButton.type = 'button'
    cancelButton.textContent = '取消'
    cancelButton.style.cssText = [
      'padding:10px 16px',
      'border:none',
      'border-radius:999px',
      'font:inherit',
      'font-weight:600',
      'color:#ffffff',
      'background:#111827',
      'cursor:pointer',
    ].join(';')

    header.append(title, description, input)
    footer.append(count, cancelButton)
    dialog.append(header, body, footer)
    overlay.appendChild(dialog)
    document.body.appendChild(overlay)

    let filtered = [...items]

    const cleanup = () => {
      overlay.remove()
      document.removeEventListener('keydown', onDocumentKeydown)
    }

    const close = (value: ScheduleCard | null) => {
      cleanup()
      resolve(value)
    }

    const renderList = () => {
      body.innerHTML = ''
      count.textContent = `共 ${filtered.length} 个可选事项`

      if (!filtered.length) {
        const empty = document.createElement('div')
        empty.textContent = '没有匹配的事项。'
        empty.style.cssText =
          'padding:20px 12px;text-align:center;font-size:14px;color:#6b7280;'
        body.appendChild(empty)
        return
      }

      filtered.forEach((card) => {
        const button = document.createElement('button')
        button.type = 'button'
        button.style.cssText = [
          'width:100%',
          'display:block',
          'padding:16px',
          'text-align:left',
          'border:1px solid rgba(15,23,42,0.08)',
          'border-radius:18px',
          'background:#ffffff',
          'cursor:pointer',
          'margin-bottom:12px',
        ].join(';')

        const metaLines = [
          card.location ? `地点：${card.location}` : '',
          card.description ? `备注：${card.description}` : '',
          card.recurrenceDescription ?? '',
        ].filter(Boolean)

        button.innerHTML = `
          <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:12px;">
            <div>
              <div style="font-size:16px;font-weight:700;color:#111827;">${escapeHtml(card.title)}</div>
              <div style="margin-top:6px;font-size:13px;line-height:1.5;color:#374151;">
                ${escapeHtml(buildCardSummary(card))}
              </div>
              ${
                metaLines.length
                  ? `<div style="margin-top:8px;font-size:13px;line-height:1.7;color:#6b7280;">${metaLines
                      .map((line) => `<div>${escapeHtml(line)}</div>`)
                      .join('')}</div>`
                  : ''
              }
            </div>
            <div style="flex:none;width:14px;height:14px;border-radius:999px;background:${escapeHtml(card.color)};"></div>
          </div>
        `

        button.addEventListener('click', () => close(card))
        body.appendChild(button)
      })
    }

    const applyFilter = () => {
      const keyword = input.value.trim().toLocaleLowerCase()
      filtered = keyword
        ? items.filter((card) => buildCardSearchText(card).includes(keyword))
        : [...items]
      renderList()
    }

    const onDocumentKeydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        close(null)
      }

      if (event.key === 'Enter' && document.activeElement === input && filtered.length === 1) {
        event.preventDefault()
        close(filtered[0])
      }
    }

    overlay.addEventListener('click', (event) => {
      if (event.target === overlay) {
        close(null)
      }
    })
    cancelButton.addEventListener('click', () => close(null))
    input.addEventListener('input', applyFilter)
    document.addEventListener('keydown', onDocumentKeydown)

    renderList()
    input.focus()
  })

const chooseEntry = async () => {
  const { data } = await axiosInstance.get<ScheduleCard[]>(CARD_API)
  const items = data ?? []

  if (!items.length) {
    window.alert('请先在日程日历插件页面添加事项。')
    return null
  }

  return showEntryChooser(items)
}

const insertCard = async (editor: Editor, range: Range) => {
  try {
    const card = await chooseEntry()
    if (!card) {
      return
    }

    editor
      .chain()
      .focus()
      .deleteRange(range)
      .insertContent({
        type: 'scheduleCard',
        attrs: card,
      })
      .run()
  } catch (error) {
    console.error(error)
    window.alert('读取事项列表失败，请稍后重试。')
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
