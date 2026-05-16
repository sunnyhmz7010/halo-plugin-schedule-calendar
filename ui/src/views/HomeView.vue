<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  IconAddCircle,
  IconArrowLeft,
  IconArrowRight,
  IconCalendar,
  IconDeleteBin,
  IconExternalLinkLine,
  IconRiPencilFill,
  IconSearch,
  Dialog,
  VAlert,
  VButton,
  VCard,
  VDescription,
  VDescriptionItem,
  VEmpty,
  VEntity,
  VEntityContainer,
  VLoading,
  VModal,
  VPageHeader,
  VStatusDot,
  VTabbar,
  VTag,
  Toast,
} from '@halo-dev/components'
import type {
  ScheduleEntry,
  ScheduleEntryRecurrenceFrequency,
  ScheduleEntrySpec,
} from '../types/schedule'
import {
  expandEntryOccurrences,
  formatDisplayDate,
  formatDisplayDateWithWeekday,
  formatEntryScheduleSummary,
  formatRecurrenceDescription,
  isRecurringEntry,
  spansMultipleLocalDates,
} from '../utils/recurrence'
import { fetchAllScheduleEntries } from '../editor/schedule-card-data'

const apiBase = '/apis/schedule.calendar.sunny.dev/v1alpha1/scheduleentries'
const pluginConfigApi = '/apis/api.console.halo.run/v1alpha1/plugins/schedule-calendar/json-config'
const publicMetaApi = '/apis/api.schedule.calendar.sunny.dev/v1alpha1/public-meta'
const hourHeight = 56
const dayColumnHeight = hourHeight * 24
const headerHeight = 64

const loading = ref(false)
const externalCalendarsLoading = ref(false)
const externalCalendarSaving = ref(false)
const saving = ref(false)
const deleting = ref(false)
const dialogVisible = ref(false)
const externalCalendarDialogVisible = ref(false)
const entries = ref<ScheduleEntry[]>([])
const entryKeyword = ref('')
const pageError = ref('')
const dialogError = ref('')
const externalCalendarDialogError = ref('')
const colorInputRef = ref<HTMLInputElement | null>(null)
const editingEntryName = ref<string | null>(null)
const editingExternalCalendarId = ref<string | null>(null)
const viewportWidth = ref(typeof window === 'undefined' ? 1280 : window.innerWidth)
const permissionLevel = ref<'unknown' | 'view' | 'manage'>('unknown')
const nowRef = ref(new Date())
const pluginTitle = ref('日程日历')
const externalCalendars = ref<ExternalCalendarFormItem[]>([])
const publicPageUrl = ref('/schedule-calendar')

const form = reactive({
  title: '',
  description: '',
  location: '',
  startTimeLocal: '',
  endTimeLocal: '',
  color: '#3b82f6',
  recurrenceFrequency: 'NONE' as ScheduleEntryRecurrenceFrequency,
  recurrenceInterval: 1,
  recurrenceUntil: '',
})

const externalCalendarForm = reactive({
  name: '',
  icsUrl: '',
  enabled: true,
  color: '#4285f4',
})

interface CalendarBlock {
  id: string
  title: string
  metaLines?: string[]
  visibleMetaLines?: string[]
  tooltipMeta?: string
  isRecurring: boolean
  isSplit: boolean
  startLabel: string
  endLabel: string
  duration: string
  color: string
  startMinutes: number
  endMinutes: number
  top: number
  height: number
  left: string
  width: string
  density: 'full' | 'compact' | 'minimal'
}

interface CalendarOccurrence {
  id: string
  entry: ScheduleEntry
  start: Date
  end: Date
}

interface EntryOccurrenceSummary {
  currentWeekCount: number
  currentWeekPreview: string
  nextOccurrenceLabel: string
}

interface EntryMetaItem {
  text: string
  wide?: boolean
  block?: boolean
}

interface PluginConfigResponse {
  title?: string
  externalCalendars?: ExternalCalendarConfigItem[]
}

interface PublicMetaResponse {
  publicPagePath: string
  publicIcalPath: string
}

interface ExternalCalendarConfigItem {
  name?: string
  icsUrl?: string
  enabled?: boolean
  color?: string
}

interface ExternalCalendarFormItem {
  id: string
  name: string
  icsUrl: string
  enabled: boolean
  color: string
}

type WeekViewMode = 'calendar' | 'agenda'

const hourLabels = Array.from({ length: 24 }, (_, hour) => `${String(hour).padStart(2, '0')}:00`)

const dateTimeInputValue = (value?: string) => {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

const startOfWeek = (source: Date) => {
  const date = new Date(source)
  const day = date.getDay()
  const diff = day === 0 ? -6 : 1 - day
  date.setHours(0, 0, 0, 0)
  date.setDate(date.getDate() + diff)
  return date
}

const formatDateInput = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const currentWeekStart = ref(startOfWeek(new Date()))
const weekInput = ref(formatDateInput(currentWeekStart.value))

const syncWeekInput = () => {
  weekInput.value = formatDateInput(currentWeekStart.value)
}

const moveWeek = (offset: number) => {
  const next = new Date(currentWeekStart.value)
  next.setDate(next.getDate() + offset * 7)
  currentWeekStart.value = startOfWeek(next)
  syncWeekInput()
}

const applyWeekInput = () => {
  if (!weekInput.value) {
    syncWeekInput()
    return
  }

  const next = new Date(`${weekInput.value}T00:00:00`)
  if (Number.isNaN(next.getTime())) {
    syncWeekInput()
    return
  }

  currentWeekStart.value = startOfWeek(next)
  syncWeekInput()
}

const resetForm = () => {
  form.title = ''
  form.description = ''
  form.location = ''
  form.startTimeLocal = ''
  form.endTimeLocal = ''
  form.color = '#3b82f6'
  form.recurrenceFrequency = 'NONE'
  form.recurrenceInterval = 1
  form.recurrenceUntil = ''
}

const resetExternalCalendarForm = () => {
  externalCalendarForm.name = ''
  externalCalendarForm.icsUrl = ''
  externalCalendarForm.enabled = true
  externalCalendarForm.color = '#4285f4'
}

const fillForm = (entry: ScheduleEntry) => {
  form.title = entry.spec.title
  form.description = entry.spec.description ?? ''
  form.location = entry.spec.location ?? ''
  form.startTimeLocal = dateTimeInputValue(entry.spec.startTime)
  form.endTimeLocal = dateTimeInputValue(entry.spec.endTime)
  form.color = entry.spec.color || '#3b82f6'
  form.recurrenceFrequency = entry.spec.recurrence?.frequency ?? 'NONE'
  form.recurrenceInterval = entry.spec.recurrence?.interval ?? 1
  form.recurrenceUntil = entry.spec.recurrence?.until ?? ''
}

const fillExternalCalendarForm = (calendar: ExternalCalendarFormItem) => {
  externalCalendarForm.name = calendar.name
  externalCalendarForm.icsUrl = calendar.icsUrl
  externalCalendarForm.enabled = calendar.enabled
  externalCalendarForm.color = calendar.color || '#4285f4'
}

const formatDuration = (start: Date, end: Date) => {
  const minutes = Math.max(Math.round((end.getTime() - start.getTime()) / 60000), 0)
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60

  if (hours && remainMinutes) {
    return `${hours} 小时 ${remainMinutes} 分钟`
  }

  if (hours) {
    return `${hours} 小时`
  }

  return `${minutes} 分钟`
}

const formatClock = (date: Date) =>
  date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })

const formatOccurrenceLabel = (occurrence: CalendarOccurrence) => {
  return spansMultipleLocalDates(occurrence.start, occurrence.end)
    ? `${formatDisplayDateWithWeekday(occurrence.start)} ${formatClock(occurrence.start)} - ${formatDisplayDateWithWeekday(occurrence.end)} ${formatClock(occurrence.end)}`
    : `${formatDisplayDateWithWeekday(occurrence.start)} ${formatClock(occurrence.start)}-${formatClock(occurrence.end)}`
}

const weekRangeLabel = computed(() => {
  const end = new Date(currentWeekStart.value)
  end.setDate(end.getDate() + 6)

  return `${formatDisplayDate(currentWeekStart.value)} 至 ${formatDisplayDate(end)}`
})

const dialogWidth = computed(() => Math.min(720, Math.max(280, viewportWidth.value - 24)))
const resolveResponsiveWeekViewMode = (width: number): WeekViewMode => (width <= 768 ? 'agenda' : 'calendar')
const hasManualWeekViewMode = ref(false)
const weekViewMode = ref<WeekViewMode>(resolveResponsiveWeekViewMode(viewportWidth.value))
const weekViewModeItems: Array<{ id: WeekViewMode; label: string }> = [
  { id: 'calendar', label: '日历布局' },
  { id: 'agenda', label: '事项布局' },
]

const setWeekViewMode = (mode: WeekViewMode) => {
  hasManualWeekViewMode.value = true
  weekViewMode.value = mode
}

const handleWeekViewModeChange = (value: string | number) => {
  if (value === 'calendar' || value === 'agenda') {
    setWeekViewMode(value)
  }
}

const goToCurrentWeek = () => {
  currentWeekStart.value = startOfWeek(new Date())
  syncWeekInput()
}

const normalizeExternalCalendar = (
  item?: ExternalCalendarConfigItem,
  fallbackName = 'Google Calendar',
): ExternalCalendarFormItem => ({
  id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  name: item?.name?.trim() || fallbackName,
  icsUrl: item?.icsUrl?.trim() || '',
  enabled: item?.enabled ?? true,
  color: item?.color?.trim() || '#4285f4',
})

const sanitizeExternalCalendarsForSave = (items: ExternalCalendarFormItem[]) =>
  items
    .map((item) => ({
      name: item.name.trim(),
      icsUrl: item.icsUrl.trim(),
      enabled: item.enabled,
      color: item.color.trim() || '#4285f4',
    }))
    .filter((item) => item.icsUrl)

const normalizeExternalCalendarForCompare = (item: ExternalCalendarFormItem) => ({
  name: item.name.trim(),
  icsUrl: item.icsUrl.trim(),
  enabled: item.enabled,
  color: item.color.trim() || '#4285f4',
})

const hasExternalCalendarChanged = (
  currentItem: ExternalCalendarFormItem,
  nextItem: ExternalCalendarFormItem,
) =>
  JSON.stringify(normalizeExternalCalendarForCompare(currentItem)) !==
  JSON.stringify(normalizeExternalCalendarForCompare(nextItem))

const openPublicPage = () => {
  window.open(publicPageUrl.value, '_blank', 'noopener')
}

const buildBlockMetaLines = (entry: ScheduleEntry) => {
  const lines: string[] = []

  if (entry.spec.location) {
    lines.push(`地点：${entry.spec.location}`)
  }

  if (entry.spec.description) {
    lines.push(`备注：${entry.spec.description}`)
  }

  const recurrence = formatRecurrenceDescription(entry.spec.recurrence)
  if (recurrence) {
    lines.push(recurrence)
  }

  return lines
}

const buildTooltipMeta = (entry: ScheduleEntry) => buildBlockMetaLines(entry).join(' ')

const buildVisibleMetaLines = (block: {
  density: CalendarBlock['density']
  height: number
  isRecurring: boolean
  title: string
  metaLines?: string[]
}) => {
  if (block.density !== 'full' || !block.metaLines?.length) {
    return []
  }

  const titleLines = block.title.length > 10 ? 2 : 1
  const contentBudget = Math.max(0, block.height - 12)
  const reservedHeight =
    titleLines * 18 +
    18 +
    18 +
    (block.isRecurring ? 22 : 0)
  const maxMetaLines = Math.max(0, Math.floor((contentBudget - reservedHeight) / 18))
  return block.metaLines.slice(0, maxMetaLines)
}

const buildDayBlocks = (
  occurrences: CalendarOccurrence[],
  startOfDay: Date,
  endOfDay: Date,
  dayIndex: number,
) => {
  const rawBlocks = occurrences
    .map((occurrence) => {
      const start = occurrence.start
      const end = occurrence.end
      const entry = occurrence.entry

      if (end <= startOfDay || start >= endOfDay) {
        return null
      }

      const clippedStart = start < startOfDay ? startOfDay : start
      const clippedEnd = end > endOfDay ? endOfDay : end
      const startMinutes = clippedStart.getHours() * 60 + clippedStart.getMinutes()
      const rawEndMinutes = clippedEnd.getHours() * 60 + clippedEnd.getMinutes()
      const crossesDayBoundary = clippedEnd.getTime() > clippedStart.getTime() && rawEndMinutes <= startMinutes
      const endMinutes = crossesDayBoundary ? 24 * 60 : rawEndMinutes
      const durationMinutes = Math.max(Math.round((clippedEnd.getTime() - clippedStart.getTime()) / 60000), 30)
      const height = Math.max((durationMinutes / 60) * hourHeight - 6, 26)

      return {
        id: `${occurrence.id}-${dayIndex}`,
        title: entry.spec.title,
        metaLines: buildBlockMetaLines(entry),
        tooltipMeta: buildTooltipMeta(entry),
        isRecurring: isRecurringEntry(entry),
        startLabel: formatClock(clippedStart),
        endLabel: crossesDayBoundary ? '24:00' : formatClock(clippedEnd),
        duration: formatDuration(clippedStart, clippedEnd),
        color: entry.spec.color || '#3b82f6',
        startMinutes,
        endMinutes,
        top: (startMinutes / 60) * hourHeight,
        height,
      }
    })
    .filter(Boolean)
    .sort((left, right) => left!.startMinutes - right!.startMinutes) as Array<
    Omit<CalendarBlock, 'left' | 'width' | 'density' | 'isSplit'>
  >

  const groups: typeof rawBlocks[] = []
  let currentGroup: typeof rawBlocks = []
  let currentGroupEnd = -1

  rawBlocks.forEach((block) => {
    if (!currentGroup.length || block.startMinutes < currentGroupEnd) {
      currentGroup.push(block)
      currentGroupEnd = Math.max(currentGroupEnd, block.endMinutes)
      return
    }

    groups.push(currentGroup)
    currentGroup = [block]
    currentGroupEnd = block.endMinutes
  })

  if (currentGroup.length) {
    groups.push(currentGroup)
  }

  return groups.flatMap((group) => {
    const columns: number[] = []
    const placements = group.map((block) => {
      let columnIndex = 0
      while (columnIndex < columns.length && columns[columnIndex] > block.startMinutes) {
        columnIndex += 1
      }

      if (columnIndex === columns.length) {
        columns.push(block.endMinutes)
      } else {
        columns[columnIndex] = block.endMinutes
      }

      return {
        block,
        columnIndex,
      }
    })

    const columnCount = Math.max(columns.length, 1)
    const gap = 6
    const width = `calc((100% - ${(columnCount + 1) * gap}px) / ${columnCount})`

    return placements.map(({ block, columnIndex }) => {
      const left = `calc(${gap}px + (${width} + ${gap}px) * ${columnIndex})`
      const density = block.height < 42 ? 'minimal' : block.height < 76 ? 'compact' : 'full'
      const visibleMetaLines = buildVisibleMetaLines({
        density,
        height: block.height,
        isRecurring: block.isRecurring,
        title: block.title,
        metaLines: block.metaLines,
      })

      return {
        ...block,
        left,
        width,
        density,
        isSplit: columnCount > 1,
        visibleMetaLines,
      } satisfies CalendarBlock
    })
  })
}

const weekDays = computed(() => {
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(currentWeekStart.value)
    day.setDate(day.getDate() + index)

    const startOfDay = new Date(day)
    startOfDay.setHours(0, 0, 0, 0)

    const endOfDay = new Date(day)
    endOfDay.setDate(endOfDay.getDate() + 1)
    endOfDay.setHours(0, 0, 0, 0)

    const blocks = buildDayBlocks(currentWeekOccurrences.value, startOfDay, endOfDay, index)

    return {
      id: day.toISOString(),
      weekday: day.toLocaleDateString('zh-CN', { weekday: 'short' }),
      date: formatDisplayDate(day),
      blocks,
    }
  })
})

const currentWeekOccurrences = computed(() => {
  const rangeStart = new Date(currentWeekStart.value)
  const rangeEnd = new Date(currentWeekStart.value)
  rangeEnd.setDate(rangeEnd.getDate() + 7)

  return entries.value
    .flatMap((entry) => expandEntryOccurrences(entry, rangeStart, rangeEnd))
    .sort((left, right) => left.start.getTime() - right.start.getTime())
})

const currentActiveOccurrences = computed(() => {
  const now = nowRef.value
  const rangeStart = new Date(now)
  rangeStart.setHours(0, 0, 0, 0)
  const rangeEnd = new Date(rangeStart)
  rangeEnd.setDate(rangeEnd.getDate() + 1)

  return entries.value
    .flatMap((entry) => expandEntryOccurrences(entry, rangeStart, rangeEnd))
    .filter((occurrence) => occurrence.start <= now && occurrence.end > now)
    .sort((left, right) => left.start.getTime() - right.start.getTime())
})

const formatCurrentStatusText = (titles: string[]) => {
  const normalizedTitles = [...new Set(titles.map((title) => title.trim()).filter(Boolean))]

  if (!normalizedTitles.length) {
    return '当前空闲'
  }

  if (normalizedTitles.length <= 2) {
    return `进行中：${normalizedTitles.join('、')}`
  }

  return `进行中：${normalizedTitles.slice(0, 2).join('、')} 等 ${normalizedTitles.length} 项`
}

const formatCountdownDuration = (target: Date, reference = nowRef.value) => {
  const totalMinutes = Math.max(Math.ceil((target.getTime() - reference.getTime()) / 60000), 0)
  const days = Math.floor(totalMinutes / (24 * 60))
  const hours = Math.floor((totalMinutes % (24 * 60)) / 60)
  const minutes = totalMinutes % 60
  const parts: string[] = []

  if (days) {
    parts.push(`${days}天`)
  }

  if (hours) {
    parts.push(`${hours}小时`)
  }

  if (minutes || !parts.length) {
    parts.push(`${minutes}分钟`)
  }

  return parts.join('')
}

const currentStatus = computed<{ state: 'warning' | 'success'; text: string }>(() => ({
  state: currentActiveOccurrences.value.length ? 'warning' : 'success',
  text: formatCurrentStatusText(currentActiveOccurrences.value.map((occurrence) => occurrence.entry.spec.title)),
}))

const nextUpcomingOccurrence = computed(() => {
  const now = nowRef.value
  const rangeEnd = new Date(now)
  rangeEnd.setDate(rangeEnd.getDate() + 90)

  return entries.value
    .flatMap((entry) => expandEntryOccurrences(entry, now, rangeEnd))
    .filter((occurrence) => occurrence.start > now)
    .sort((left, right) => left.start.getTime() - right.start.getTime())[0]
})

const nextOccurrenceCountdown = computed(() => {
  const nextOccurrence = nextUpcomingOccurrence.value
  if (!nextOccurrence) {
    return ''
  }

  return `${formatCountdownDuration(nextOccurrence.start)}后开始：${nextOccurrence.entry.spec.title}`
})

const currentTimeDateKey = computed(() => formatDisplayDate(nowRef.value))
const currentTimeTop = computed(() => {
  const now = nowRef.value
  return ((now.getHours() * 60 + now.getMinutes()) / 60) * hourHeight
})

const weekOccupiedSummary = computed(() => {
  const totalMinutes = currentWeekOccurrences.value.reduce((sum, occurrence) => {
    const start = occurrence.start
    const end = occurrence.end
    return sum + Math.max(Math.round((end.getTime() - start.getTime()) / 60000), 0)
  }, 0)

  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  if (hours && minutes) {
    return `${hours} 小时 ${minutes} 分钟`
  }

  if (hours) {
    return `${hours} 小时`
  }

  return `${minutes} 分钟`
})

const sortedEntries = computed(() => {
  return [...entries.value].sort((left, right) =>
    new Date(left.spec.startTime).getTime() - new Date(right.spec.startTime).getTime(),
  )
})

const buildEntrySearchText = (entry: ScheduleEntry) => {
  const occurrenceSummary = entryOccurrenceSummaryMap.value.get(entry.metadata.name)

  return [
    entry.metadata.name,
    entry.spec.title,
    entry.spec.location,
    entry.spec.description,
    formatEntryScheduleSummary(entry),
    occurrenceSummary?.currentWeekPreview,
    occurrenceSummary?.nextOccurrenceLabel,
  ]
    .filter(Boolean)
    .join(' ')
    .toLocaleLowerCase()
}

const filteredSortedEntries = computed(() => {
  const keyword = entryKeyword.value.trim().toLocaleLowerCase()

  if (!keyword) {
    return sortedEntries.value
  }

  return sortedEntries.value.filter((entry) => buildEntrySearchText(entry).includes(keyword))
})

const entryOccurrenceSummaryMap = computed(() => {
  const weekRangeStart = new Date(currentWeekStart.value)
  const weekRangeEnd = new Date(currentWeekStart.value)
  weekRangeEnd.setDate(weekRangeEnd.getDate() + 7)

  const upcomingStart = new Date()
  const upcomingEnd = new Date(upcomingStart)
  upcomingEnd.setDate(upcomingEnd.getDate() + 90)

  return new Map<string, EntryOccurrenceSummary>(
    entries.value.map((entry) => {
      const currentWeekOccurrences = expandEntryOccurrences(entry, weekRangeStart, weekRangeEnd)
      const upcomingOccurrences = expandEntryOccurrences(entry, upcomingStart, upcomingEnd).filter(
        (occurrence) => occurrence.end > upcomingStart,
      )
      const entryStart = new Date(entry.spec.startTime)
      const upcomingOccurrence = isRecurringEntry(entry)
        ? entryStart > upcomingStart
          ? upcomingOccurrences.find((occurrence) => occurrence.start > entryStart)
          : upcomingOccurrences[0]
        : upcomingOccurrences[0]

      return [
        entry.metadata.name,
        {
          currentWeekCount: currentWeekOccurrences.length,
          currentWeekPreview: currentWeekOccurrences
            .slice(0, 3)
            .map((occurrence) => formatOccurrenceLabel(occurrence))
            .join(' · '),
          nextOccurrenceLabel: upcomingOccurrence ? formatOccurrenceLabel(upcomingOccurrence) : '',
        },
      ]
    }),
  )
})

const isEditing = computed(() => editingEntryName.value !== null)
const dialogTitle = computed(() => (isEditing.value ? '编辑事项' : '新增事项'))
const dialogSubmitLabel = computed(() => (isEditing.value ? '更新事项' : '保存事项'))
const isExternalCalendarEditing = computed(() => editingExternalCalendarId.value !== null)
const externalCalendarDialogTitle = computed(() =>
  isExternalCalendarEditing.value ? '编辑外部日历订阅' : '新增外部日历订阅',
)
const externalCalendarDialogSubmitLabel = computed(() =>
  isExternalCalendarEditing.value ? '更新订阅' : '保存订阅',
)
const canManageEntries = computed(() => permissionLevel.value === 'manage')
const showReadonlyNotice = computed(() => permissionLevel.value === 'view')

const buildEntryMetaItems = (entry: ScheduleEntry): EntryMetaItem[] => {
  const items: EntryMetaItem[] = [{ text: formatEntryScheduleSummary(entry) }]
  const occurrenceSummary = entryOccurrenceSummaryMap.value.get(entry.metadata.name)

  if (occurrenceSummary?.currentWeekCount) {
    items.push({
      text: occurrenceSummary.currentWeekPreview
        ? `本周展开 ${occurrenceSummary.currentWeekCount} 次：${occurrenceSummary.currentWeekPreview}`
        : `本周展开 ${occurrenceSummary.currentWeekCount} 次`,
      wide: true,
      block: true,
    })
  } else if (occurrenceSummary?.nextOccurrenceLabel) {
    items.push({
      text: `下一次出现：${occurrenceSummary.nextOccurrenceLabel}`,
      wide: true,
      block: true,
    })
  }

  if (entry.spec.location) {
    items.push({ text: `地点：${entry.spec.location}`, wide: true, block: true })
  }

  if (entry.spec.description) {
    items.push({ text: `备注：${entry.spec.description}`, wide: true, block: true })
  }

  return items
}

const buildExternalCalendarMetaItems = (calendar: ExternalCalendarFormItem): EntryMetaItem[] => [
  { text: calendar.enabled ? '状态：已启用' : '状态：已停用' },
  { text: `地址：${calendar.icsUrl}`, wide: true, block: true },
]

const openCreateDialog = () => {
  if (!canManageEntries.value) {
    return
  }

  editingEntryName.value = null
  resetForm()
  dialogError.value = ''
  dialogVisible.value = true
}

const openCreateExternalCalendarDialog = () => {
  if (!canManageEntries.value) {
    return
  }

  editingExternalCalendarId.value = null
  externalCalendarDialogError.value = ''
  resetExternalCalendarForm()
  externalCalendarDialogVisible.value = true
}

const openEditDialog = (entry: ScheduleEntry) => {
  if (!canManageEntries.value) {
    return
  }

  editingEntryName.value = entry.metadata.name
  fillForm(entry)
  dialogError.value = ''
  dialogVisible.value = true
}

const openEditExternalCalendarDialog = (calendar: ExternalCalendarFormItem) => {
  if (!canManageEntries.value) {
    return
  }

  editingExternalCalendarId.value = calendar.id
  fillExternalCalendarForm(calendar)
  externalCalendarDialogError.value = ''
  externalCalendarDialogVisible.value = true
}

const closeDialog = () => {
  dialogVisible.value = false
  editingEntryName.value = null
  dialogError.value = ''
  resetForm()
}

const closeExternalCalendarDialog = () => {
  externalCalendarDialogVisible.value = false
  editingExternalCalendarId.value = null
  externalCalendarDialogError.value = ''
  resetExternalCalendarForm()
}

const handleDialogVisibleUpdate = (visible: boolean) => {
  if (!visible) {
    closeDialog()
    return
  }

  dialogVisible.value = true
}

const handleExternalCalendarDialogVisibleUpdate = (visible: boolean) => {
  if (!visible) {
    closeExternalCalendarDialog()
    return
  }

  externalCalendarDialogVisible.value = true
}

const openColorPicker = () => {
  colorInputRef.value?.click()
}

const updateViewportWidth = () => {
  viewportWidth.value = window.innerWidth
}

watch(viewportWidth, (width) => {
  if (hasManualWeekViewMode.value) {
    return
  }

  weekViewMode.value = resolveResponsiveWeekViewMode(width)
})

const fetchEntries = async () => {
  loading.value = true
  pageError.value = ''

  try {
    entries.value = await fetchAllScheduleEntries()
    if (permissionLevel.value === 'unknown') {
      permissionLevel.value = 'view'
    }
  } catch (err) {
    pageError.value = '事项加载失败，请检查插件权限或 Halo 运行状态。'
    console.error(err)
  } finally {
    loading.value = false
  }
}

const loadPluginConfig = async () => {
  externalCalendarsLoading.value = true

  try {
    const { data } = await axiosInstance.get<PluginConfigResponse>(pluginConfigApi)

    pluginTitle.value = data.title?.trim() || '日程日历'

    externalCalendars.value = Array.isArray(data.externalCalendars)
      ? data.externalCalendars.map((item, index) =>
          normalizeExternalCalendar(item, item?.name?.trim() || `外部日历 ${index + 1}`),
        )
      : []
  } catch (error) {
    console.error(error)
    Toast.error('外部日历订阅加载失败')
  } finally {
    externalCalendarsLoading.value = false
  }
}

const loadPublicMeta = async () => {
  try {
    const { data } = await axiosInstance.get<PublicMetaResponse>(publicMetaApi)
    publicPageUrl.value = data.publicPagePath || '/schedule-calendar'
  } catch (error) {
    console.error(error)
    publicPageUrl.value = '/schedule-calendar'
  }
}

const persistExternalCalendars = async (items: ExternalCalendarFormItem[]) => {
  await axiosInstance.put(pluginConfigApi, {
    title: pluginTitle.value,
    externalCalendars: sanitizeExternalCalendarsForSave(items),
  })
  externalCalendars.value = items
}

const submitExternalCalendar = async () => {
  if (!canManageEntries.value) {
    return
  }

  const name = externalCalendarForm.name.trim()
  const icsUrl = externalCalendarForm.icsUrl.trim()

  externalCalendarDialogError.value = ''

  if (!name) {
    externalCalendarDialogError.value = '日历名称不能为空。'
    return
  }

  if (!icsUrl) {
    externalCalendarDialogError.value = 'ICS 订阅地址不能为空。'
    return
  }

  try {
    new URL(icsUrl)
  } catch {
    externalCalendarDialogError.value = 'ICS 订阅地址格式无效。'
    return
  }

  externalCalendarSaving.value = true

  try {
    const nextCalendar: ExternalCalendarFormItem = {
      id: editingExternalCalendarId.value ?? `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      name,
      icsUrl,
      enabled: externalCalendarForm.enabled,
      color: externalCalendarForm.color.trim() || '#4285f4',
    }

    if (isExternalCalendarEditing.value) {
      const currentCalendar = externalCalendars.value.find((item) => item.id === editingExternalCalendarId.value)
      if (!currentCalendar) {
        externalCalendarDialogError.value = '未找到要编辑的订阅，请刷新后重试。'
        return
      }

      if (!hasExternalCalendarChanged(currentCalendar, nextCalendar)) {
        Toast.success('没有变化')
        return
      }
    }

    const nextCalendars = isExternalCalendarEditing.value
      ? externalCalendars.value.map((item) =>
          item.id === editingExternalCalendarId.value ? nextCalendar : item,
        )
      : [...externalCalendars.value, nextCalendar]

    await persistExternalCalendars(nextCalendars)
    closeExternalCalendarDialog()
    Toast.success(isExternalCalendarEditing.value ? '外部日历订阅已更新' : '外部日历订阅已添加')
  } catch (error) {
    console.error(error)
    externalCalendarDialogError.value = '外部日历订阅保存失败。'
  } finally {
    externalCalendarSaving.value = false
  }
}

const removeExternalCalendar = async (id: string) => {
  await persistExternalCalendars(externalCalendars.value.filter((item) => item.id !== id))
}

const openRemoveExternalCalendarDialog = (calendar: ExternalCalendarFormItem) => {
  if (!canManageEntries.value || deleting.value) {
    return
  }

  Dialog.warning({
    title: '确认删除外部日历订阅',
    description: `删除后将停止同步“${calendar.name}”的外部日历数据。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    showCancel: true,
    onConfirm: () => {
      if (deleting.value) {
        return
      }

      deleting.value = true
      void removeExternalCalendar(calendar.id)
        .then(() => {
          Toast.success('外部日历订阅已删除')
        })
        .catch((error) => {
          console.error(error)
          Toast.error('外部日历订阅删除失败')
        })
        .finally(() => {
          deleting.value = false
        })
    },
  })
}

const loadPermissionLevel = async () => {
  permissionLevel.value = 'unknown'

  try {
    const response = await axiosInstance.delete(
      `${apiBase}/${encodeURIComponent('__permission_probe__')}`,
      {
        validateStatus: (status) => status >= 200 && status < 500,
      },
    )

    if (response.status !== 401 && response.status !== 403) {
      permissionLevel.value = 'manage'
      return
    }
  } catch (error) {
    console.error(error)
  }
}

const restoreScrollPosition = async (top: number) => {
  await nextTick()
  window.scrollTo({
    top,
    behavior: 'auto',
  })
}

const buildEntrySpec = (startDate: Date, endDate: Date): ScheduleEntrySpec => ({
  title: form.title,
  description: form.description || undefined,
  location: form.location || undefined,
  startTime: startDate.toISOString(),
  endTime: endDate.toISOString(),
  color: form.color,
  recurrence:
    form.recurrenceFrequency === 'NONE'
      ? undefined
      : {
          frequency: form.recurrenceFrequency,
          interval: form.recurrenceInterval,
          until: form.recurrenceUntil || undefined,
        },
})

const normalizeEntrySpec = (spec: ScheduleEntrySpec): ScheduleEntrySpec => ({
  title: spec.title,
  description: spec.description || undefined,
  location: spec.location || undefined,
  startTime: new Date(spec.startTime).toISOString(),
  endTime: new Date(spec.endTime).toISOString(),
  color: spec.color || '#3b82f6',
  recurrence:
    spec.recurrence?.frequency && spec.recurrence.frequency !== 'NONE'
      ? {
          frequency: spec.recurrence.frequency,
          interval: spec.recurrence.interval ?? 1,
          until: spec.recurrence.until || undefined,
        }
      : undefined,
})

const hasEntryChanged = (currentSpec: ScheduleEntrySpec, nextSpec: ScheduleEntrySpec) =>
  JSON.stringify(normalizeEntrySpec(currentSpec)) !== JSON.stringify(normalizeEntrySpec(nextSpec))

const validateForm = () => {
  dialogError.value = ''

  if (!form.title || !form.startTimeLocal || !form.endTimeLocal) {
    dialogError.value = '标题、开始时间、结束时间是必填项。'
    return null
  }

  const startDate = new Date(form.startTimeLocal)
  const endDate = new Date(form.endTimeLocal)
  if (endDate <= startDate) {
    dialogError.value = '结束时间必须晚于开始时间。'
    return null
  }

  if (form.recurrenceFrequency !== 'NONE') {
    if (spansMultipleLocalDates(startDate, endDate)) {
      dialogError.value = '跨天事项暂不支持循环，请拆分为单次事项或取消循环。'
      return null
    }

    if (!Number.isInteger(form.recurrenceInterval) || form.recurrenceInterval < 1) {
      dialogError.value = '循环间隔必须是大于 0 的整数。'
      return null
    }

    const startDateKey = form.startTimeLocal.slice(0, 10)
    if (form.recurrenceUntil && form.recurrenceUntil < startDateKey) {
      dialogError.value = '循环截止日期不能早于开始日期。'
      return null
    }
  }

  return { startDate, endDate }
}

const createEntry = async () => {
  if (!canManageEntries.value) {
    return
  }

  const validated = validateForm()
  if (!validated) {
    return
  }

  const { startDate, endDate } = validated
  const scrollTop = window.scrollY

  saving.value = true

  try {
    await axiosInstance.post(apiBase, {
      apiVersion: 'schedule.calendar.sunny.dev/v1alpha1',
      kind: 'ScheduleEntry',
      metadata: {
        name: `schedule-entry-${Date.now()}`,
      },
      spec: buildEntrySpec(startDate, endDate),
    })

    closeDialog()
    await fetchEntries()
    await restoreScrollPosition(scrollTop)
    Toast.success('事项已添加')
  } catch (err) {
    dialogError.value = '事项创建失败。'
    console.error(err)
  } finally {
    saving.value = false
  }
}

const updateEntry = async () => {
  if (!canManageEntries.value) {
    return
  }

  const validated = validateForm()
  if (!validated || !editingEntryName.value) {
    return
  }

  const currentEntry = entries.value.find((entry) => entry.metadata.name === editingEntryName.value)
  if (!currentEntry) {
    dialogError.value = '未找到要编辑的事项，请刷新后重试。'
    return
  }

  const { startDate, endDate } = validated
  const nextSpec = buildEntrySpec(startDate, endDate)

  if (!hasEntryChanged(currentEntry.spec, nextSpec)) {
    Toast.success('没有变化')
    return
  }

  const scrollTop = window.scrollY
  saving.value = true

  try {
    await axiosInstance.put(`${apiBase}/${encodeURIComponent(currentEntry.metadata.name)}`, {
      apiVersion: currentEntry.apiVersion ?? 'schedule.calendar.sunny.dev/v1alpha1',
      kind: currentEntry.kind ?? 'ScheduleEntry',
      metadata: {
        ...currentEntry.metadata,
        name: currentEntry.metadata.name,
      },
      spec: nextSpec,
    })

    closeDialog()
    await fetchEntries()
    await restoreScrollPosition(scrollTop)
    Toast.success('事项已更新')
  } catch (err) {
    dialogError.value = '事项更新失败。'
    console.error(err)
  } finally {
    saving.value = false
  }
}

const submitEntry = async () => {
  if (!canManageEntries.value) {
    return
  }

  if (isEditing.value) {
    await updateEntry()
    return
  }

  await createEntry()
}

const removeEntry = async (name: string) => {
  if (!canManageEntries.value) {
    return false
  }

  pageError.value = ''
  const previousEntries = [...entries.value]

  try {
    entries.value = entries.value.filter((entry) => entry.metadata.name !== name)
    await axiosInstance.delete(`${apiBase}/${encodeURIComponent(name)}`)
    Toast.success('事项已删除')
    return true
  } catch (err) {
    entries.value = previousEntries
    pageError.value = '事项删除失败。'
    Toast.error('事项删除失败')
    console.error(err)
    return false
  }
}

const openRemoveDialog = (entry: ScheduleEntry) => {
  if (!canManageEntries.value || deleting.value) {
    return
  }

  Dialog.warning({
    title: '确认删除事项',
    description: `删除后将无法恢复，“${entry.spec.title}”会从周历和事项列表中移除。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    showCancel: true,
    onConfirm: () => {
      if (deleting.value) {
        return
      }

      deleting.value = true
      void removeEntry(entry.metadata.name).finally(() => {
        deleting.value = false
      })
    }
  })
}

const updateNow = () => {
  nowRef.value = new Date()
}

let nowTimer: number | undefined

onMounted(() => {
  updateNow()
  nowTimer = window.setInterval(updateNow, 60_000)
  updateViewportWidth()
  window.addEventListener('resize', updateViewportWidth)
  syncWeekInput()
  void loadPublicMeta()
  void loadPluginConfig()
  void loadPermissionLevel()
  void fetchEntries()
})

onBeforeUnmount(() => {
  if (nowTimer) {
    window.clearInterval(nowTimer)
  }
  window.removeEventListener('resize', updateViewportWidth)
})
</script>

<template>
  <section class="schedule-view">
    <VPageHeader title="日程日历">
      <template #icon>
        <IconCalendar class="mr-2 h-5 w-5" />
      </template>
      <template #actions>
        <VButton type="secondary" @click="openPublicPage">
          <template #icon>
            <IconExternalLinkLine />
          </template>
          打开前台页面
        </VButton>
      </template>
    </VPageHeader>

    <div class="page-body">
      <VAlert
        v-if="pageError"
        class="page-alert"
        type="error"
        title="操作失败"
        :description="pageError"
        :closable="false"
      />

      <VAlert
        v-if="showReadonlyNotice"
        class="page-alert"
        type="info"
        title="当前为只读权限"
        description="你可以查看周历和事项列表；新增、编辑、删除和备份恢复需要“日程日历管理”权限。"
        :closable="false"
      />

      <VCard class="overview-card">
        <VDescription>
          <VDescriptionItem label="周范围" :content="weekRangeLabel" />
          <VDescriptionItem label="事项数">
            <VTag theme="default">{{ currentWeekOccurrences.length }} 个事项</VTag>
          </VDescriptionItem>
          <VDescriptionItem label="总占用">
            <VStatusDot state="success" :text="weekOccupiedSummary" />
          </VDescriptionItem>
          <VDescriptionItem label="现在">
            <VStatusDot :state="currentStatus.state" :text="currentStatus.text" />
          </VDescriptionItem>
          <VDescriptionItem v-if="nextOccurrenceCountdown" label="下一个" :content="nextOccurrenceCountdown" />
        </VDescription>
      </VCard>

      <VCard class="section-card">
        <div class="week-toolbar">
          <div class="week-toolbar__nav">
            <VButton @click="moveWeek(-1)">
              <template #icon>
                <IconArrowLeft />
              </template>
              上一周
            </VButton>
            <VButton @click="moveWeek(1)">
              <template #icon>
                <IconArrowRight />
              </template>
              下一周
            </VButton>
          </div>

          <div class="week-toolbar__center">
            <input
              v-model="weekInput"
              class="week-picker"
              type="date"
              @change="applyWeekInput"
              @keyup.enter="applyWeekInput"
            />
          </div>

          <div class="week-toolbar__current">
            <VButton @click="goToCurrentWeek">回到本周</VButton>
          </div>

          <div class="week-toolbar__mode">
            <div class="week-view-mode">
              <VTabbar
                :items="weekViewModeItems"
                :active-id="weekViewMode"
                type="outline"
                @update:active-id="handleWeekViewModeChange"
              />
            </div>
          </div>
        </div>

        <div v-if="loading" class="calendar-loading">
          <VLoading />
        </div>

        <div v-else class="calendar-shell" :class="{ 'calendar-shell--agenda': weekViewMode === 'agenda' }">
          <div v-if="weekViewMode === 'agenda'" class="calendar-mobile">
            <section
              v-for="day in weekDays"
              :key="`${day.id}-mobile`"
              class="calendar-mobile-day"
            >
              <header class="calendar-mobile-day__header">
                <div class="calendar-mobile-day__heading">
                  <strong>{{ day.weekday }}</strong>
                  <span>{{ day.date }}</span>
                </div>

                <VTag theme="default">{{ day.blocks.length }} 项</VTag>
              </header>

              <div v-if="day.blocks.length" class="calendar-mobile-day__list">
                <article
                  v-for="block in day.blocks"
                  :key="`${block.id}-mobile`"
                  class="calendar-mobile-block"
                >
                  <div class="calendar-mobile-block__accent" :style="{ background: block.color }"></div>

                  <div class="calendar-mobile-block__content">
                    <div class="calendar-mobile-block__top">
                      <div class="calendar-mobile-block__title">{{ block.title }}</div>
                    </div>

                    <div class="calendar-mobile-block__time">
                      {{ block.startLabel }} - {{ block.endLabel }}
                    </div>
                    <div class="calendar-mobile-block__meta">{{ block.duration }}</div>
                    <div
                      v-for="(metaLine, metaIndex) in block.metaLines ?? []"
                      :key="`${block.id}-mobile-meta-${metaIndex}`"
                      class="calendar-mobile-block__meta"
                    >
                      {{ metaLine }}
                    </div>
                  </div>
                </article>
              </div>

              <div v-else class="calendar-mobile-day__empty">当天暂无事项</div>
            </section>
          </div>

          <div v-else class="calendar-desktop">
            <div class="calendar-grid-scroll">
              <div class="calendar-grid">
                <div class="time-column">
                  <div class="time-column__header" :style="{ height: `${headerHeight}px` }">时间</div>
                  <div class="time-column__body" :style="{ height: `${dayColumnHeight}px` }">
                    <div
                      v-for="hour in hourLabels"
                      :key="hour"
                      class="time-column__slot"
                      :style="{ height: `${hourHeight}px` }"
                    >
                      {{ hour }}
                    </div>
                  </div>
                </div>

                <div class="day-columns">
                  <div
                    v-for="day in weekDays"
                    :key="day.id"
                    class="day-column"
                  >
                    <header class="day-column__header" :style="{ height: `${headerHeight}px` }">
                      <strong>{{ day.weekday }}</strong>
                      <span>{{ day.date }}</span>
                    </header>

                    <div
                      class="day-column__body"
                      :style="{ height: `${dayColumnHeight}px` }"
                    >
                      <div class="day-column__lines"></div>
                      <div
                        v-if="day.date === currentTimeDateKey"
                        class="current-time-line"
                        :style="{ top: `${currentTimeTop}px` }"
                      >
                        <span class="current-time-line__dot"></span>
                      </div>

                      <article
                        v-for="block in day.blocks"
                        :key="block.id"
                        class="calendar-block"
                        :class="{ 'calendar-block--split': block.isSplit }"
                        :style="{
                          top: `${block.top}px`,
                          height: `${block.height}px`,
                          left: block.left,
                          width: block.width,
                          background: block.color,
                        }"
                        :title="`${block.title} ${block.startLabel} - ${block.endLabel}${block.tooltipMeta ? ` ${block.tooltipMeta}` : ''}`"
                      >
                        <div class="calendar-block__title">{{ block.title }}</div>
                        <div v-if="block.density !== 'minimal'" class="calendar-block__time">
                          {{ block.startLabel }} - {{ block.endLabel }}
                        </div>
                        <div v-if="block.density === 'full' && !block.isSplit" class="calendar-block__meta">{{ block.duration }}</div>
                        <div
                          v-for="(metaLine, metaIndex) in block.isSplit ? [] : block.visibleMetaLines ?? []"
                          :key="`${block.id}-meta-${metaIndex}`"
                          class="calendar-block__meta"
                        >
                          {{ metaLine }}
                        </div>
                      </article>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </VCard>

      <VCard class="section-card">
        <template #header>
          <div class="entry-card-header">
            <div class="entry-card-header__title">外部日历订阅</div>

            <div class="entry-card-header__search"></div>

            <div v-if="canManageEntries" class="entry-card-header__actions">
              <VButton type="secondary" @click="openCreateExternalCalendarDialog">
                <template #icon>
                  <IconAddCircle />
                </template>
                新增订阅
              </VButton>
            </div>
          </div>
        </template>

        <VEntityContainer v-if="externalCalendars.length">
          <VEntity v-for="calendar in externalCalendars" :key="calendar.id">
            <template #start>
              <div class="entry-start">
                <span class="entry-dot" :style="{ background: calendar.color || '#4285f4' }"></span>
                <div class="entry-main">
                  <div class="entry-title">{{ calendar.name }}</div>
                  <div class="entry-meta">
                    <span
                      v-for="(item, index) in buildExternalCalendarMetaItems(calendar)"
                      :key="`${calendar.id}-${index}`"
                      class="entry-meta__item"
                      :class="{
                        'entry-meta__item--wide': item.wide,
                        'entry-meta__item--block': item.block,
                      }"
                    >
                      {{ item.text }}
                    </span>
                  </div>
                </div>
              </div>
            </template>
            <template #end>
              <div v-if="canManageEntries" class="entry-actions">
                <VButton ghost @click="openEditExternalCalendarDialog(calendar)">
                  <template #icon>
                    <IconRiPencilFill />
                  </template>
                  编辑
                </VButton>
                <VButton ghost @click="openRemoveExternalCalendarDialog(calendar)">
                  <template #icon>
                    <IconDeleteBin />
                  </template>
                  删除
                </VButton>
              </div>
            </template>
          </VEntity>
        </VEntityContainer>

        <div v-else-if="externalCalendarsLoading" class="calendar-loading">
          <VLoading />
        </div>

        <VEmpty
          v-else
          title="还没有外部日历订阅"
          message="新增一个 ICS 订阅后，会自动同步显示到前台日程视图。"
        />
      </VCard>

      <VCard class="section-card">
        <template #header>
          <div class="entry-card-header">
            <div class="entry-card-header__title">事项</div>

            <div v-if="entries.length" class="entry-card-header__search">
              <div class="entry-search">
                <div class="entry-search__field">
                  <span class="entry-search__icon">
                    <IconSearch />
                  </span>
                  <input
                    v-model="entryKeyword"
                    type="search"
                    class="entry-search__input"
                    placeholder="搜索标题、地点、备注、时间或展开信息"
                  />
                </div>
              </div>
            </div>

            <div v-if="canManageEntries" class="entry-card-header__actions">
              <VButton type="secondary" @click="openCreateDialog">
                <template #icon>
                  <IconAddCircle />
                </template>
                新增事项
              </VButton>
            </div>
          </div>
        </template>

        <VEntityContainer v-if="filteredSortedEntries.length">
          <VEntity v-for="entry in filteredSortedEntries" :key="entry.metadata.name">
            <template #start>
              <div class="entry-start">
                <span class="entry-dot" :style="{ background: entry.spec.color || '#3b82f6' }"></span>
                <div class="entry-main">
                  <div class="entry-title">{{ entry.spec.title }}</div>
                  <div class="entry-meta">
                    <span
                      v-for="(item, index) in buildEntryMetaItems(entry)"
                      :key="`${entry.metadata.name}-${index}`"
                      class="entry-meta__item"
                      :class="{
                        'entry-meta__item--wide': item.wide,
                        'entry-meta__item--block': item.block,
                      }"
                    >
                      {{ item.text }}
                    </span>
                  </div>
                </div>
              </div>
            </template>
            <template #end>
              <div v-if="canManageEntries" class="entry-actions">
                <VButton ghost @click="openEditDialog(entry)">
                  <template #icon>
                    <IconRiPencilFill />
                  </template>
                  编辑
                </VButton>
                <VButton ghost @click="openRemoveDialog(entry)">
                  <template #icon>
                    <IconDeleteBin />
                  </template>
                  删除
                </VButton>
              </div>
            </template>
          </VEntity>
        </VEntityContainer>

        <VEmpty
          v-else
          :title="entries.length ? '没有匹配的事项' : '还没有事项'"
          :message="entries.length ? '换个关键词试试，或者清空当前搜索条件。' : '新增一个事项后，会同时显示在下方列表和上方周历中。'"
        />
      </VCard>
    </div>

    <VModal
      :visible="dialogVisible"
      :title="dialogTitle"
      :width="dialogWidth"
      :layer-closable="false"
      :body-class="['schedule-modal-body']"
      @update:visible="handleDialogVisibleUpdate"
    >
      <div class="dialog-form">
        <VAlert
          v-if="dialogError"
          type="error"
          title="操作失败"
          :description="dialogError"
          :closable="false"
        />

        <label class="field">
          <span>事项标题</span>
          <input v-model="form.title" type="text" placeholder="例如：产品评审" />
        </label>

        <label class="field">
          <span>地点 / 链接</span>
          <input v-model="form.location" type="text" placeholder="会议室 A / 腾讯会议" />
        </label>

        <div class="field-row">
          <label class="field">
            <span>开始时间</span>
            <input v-model="form.startTimeLocal" type="datetime-local" />
          </label>

          <label class="field">
            <span>结束时间</span>
            <input v-model="form.endTimeLocal" type="datetime-local" />
          </label>
        </div>

        <div class="field-row field-row--recurrence">
          <label class="field">
            <span>循环规则</span>
            <select v-model="form.recurrenceFrequency">
              <option value="NONE">不重复</option>
              <option value="DAILY">每天</option>
              <option value="WEEKLY">每周</option>
              <option value="MONTHLY">每月</option>
              <option value="YEARLY">每年</option>
            </select>
          </label>

          <label v-if="form.recurrenceFrequency !== 'NONE'" class="field">
            <span>循环间隔</span>
            <input v-model.number="form.recurrenceInterval" type="number" min="1" step="1" />
          </label>

          <label v-if="form.recurrenceFrequency !== 'NONE'" class="field">
            <span>截止日期</span>
            <input v-model="form.recurrenceUntil" type="date" />
          </label>
        </div>

        <label class="field">
          <span>事项说明</span>
          <textarea
            v-model="form.description"
            rows="4"
            placeholder="可选：补充备注、参与人、准备事项"
          ></textarea>
        </label>

        <label class="field field--compact">
          <span>颜色</span>
          <button type="button" class="color-picker" @click="openColorPicker">
            <span class="color-picker__preview" :style="{ background: form.color }"></span>
            <span class="color-picker__value">{{ form.color }}</span>
          </button>
          <input ref="colorInputRef" v-model="form.color" type="color" class="field__color" />
        </label>
      </div>
      <template #footer>
        <div class="modal-footer">
          <VButton @click="closeDialog">取消</VButton>
          <VButton type="primary" :loading="saving" @click="submitEntry">
            {{ dialogSubmitLabel }}
          </VButton>
        </div>
      </template>
    </VModal>

    <VModal
      :visible="externalCalendarDialogVisible"
      :title="externalCalendarDialogTitle"
      :width="dialogWidth"
      :layer-closable="false"
      :body-class="['schedule-modal-body']"
      @update:visible="handleExternalCalendarDialogVisibleUpdate"
    >
      <div class="dialog-form">
        <VAlert
          v-if="externalCalendarDialogError"
          type="error"
          title="操作失败"
          :description="externalCalendarDialogError"
          :closable="false"
        />

        <label class="field">
          <span>日历名称</span>
          <input v-model="externalCalendarForm.name" type="text" placeholder="例如：Google Calendar / Apple 日历 / 节假日安排" />
        </label>

        <label class="field">
          <span>ICS 订阅地址</span>
          <input
            v-model="externalCalendarForm.icsUrl"
            type="url"
            placeholder="填写 Google Calendar 等日历服务导出的 iCal / ICS 订阅地址"
          />
        </label>

        <div class="field-row">
          <label class="field field--compact">
            <span>默认颜色</span>
            <input v-model="externalCalendarForm.color" type="color" class="external-calendar-color" />
          </label>

          <label class="field">
            <span>状态</span>
            <span class="external-calendar-toggle">
              <input v-model="externalCalendarForm.enabled" type="checkbox" />
              <span>启用该订阅</span>
            </span>
          </label>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <VButton @click="closeExternalCalendarDialog">取消</VButton>
          <VButton type="primary" :loading="externalCalendarSaving" @click="submitExternalCalendar">
            {{ externalCalendarDialogSubmitLabel }}
          </VButton>
        </div>
      </template>
    </VModal>

  </section>
</template>

<style scoped lang="scss">
.schedule-view {
  position: relative;
  z-index: 0;
  isolation: isolate;
  padding: 0;
}

.page-body {
  position: relative;
  z-index: 0;
  padding-left: 20px !important;
  padding-right: 20px !important;
  padding-bottom: 20px !important;
}

.page-alert,
.overview-card,
.section-card {
  position: relative;
  z-index: 0;
  margin-top: 16px;
}

.week-toolbar {
  display: grid;
  grid-template-columns: auto minmax(220px, 1fr) auto auto;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.week-toolbar__nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.week-toolbar__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 220px;
}

.week-toolbar__current,
.week-toolbar__mode {
  display: flex;
  justify-content: center;
}

.week-view-mode {
  display: flex;
  justify-content: center;
  width: fit-content;
  max-width: 100%;
}

.week-view-mode :deep(.tabbar-wrapper) {
  max-width: 100%;
}

.week-view-mode :deep(.tabbar-items) {
  max-width: 100%;
}

.week-toolbar__range {
  font-size: 13px;
  font-weight: 600;
  color: var(--halo-text-color, #111827);
}

.week-picker {
  width: 160px;
  height: 40px;
  min-height: 40px;
  border: 1px solid var(--halo-border-color, #d1d5db);
  border-radius: 8px;
  padding: 8px 12px;
  line-height: 1.2;
  box-sizing: border-box;
  font: inherit;
  color: var(--halo-text-color, #111827);
  background: var(--halo-bg-color, #fff);
}

.calendar-loading {
  padding: 48px 0;
}

.calendar-shell {
  position: relative;
  z-index: 0;
  overflow-x: auto;
}

.calendar-shell--agenda {
  overflow-x: visible;
}

.calendar-grid-scroll {
  position: relative;
  z-index: 0;
  isolation: isolate;
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-x pan-y pinch-zoom;
  padding-bottom: 4px;
}

.calendar-mobile {
  display: grid;
  gap: 12px;
}

.calendar-mobile-day {
  border: 1px solid var(--halo-border-color, #e5e7eb);
  border-radius: 12px;
  background: var(--halo-bg-color-secondary, #f8fafc);
  overflow: hidden;
}

.calendar-mobile-day__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--halo-border-color, #e5e7eb);
}

.calendar-mobile-day__heading {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.calendar-mobile-day__heading strong {
  color: var(--halo-text-color, #111827);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.4;
}

.calendar-mobile-day__heading span {
  margin-top: 4px;
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 12px;
  line-height: 1.4;
}

.calendar-mobile-day__list {
  display: grid;
  gap: 10px;
  padding: 12px;
}

.calendar-mobile-day__empty {
  padding: 18px 16px;
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 13px;
}

.calendar-mobile-block {
  display: grid;
  grid-template-columns: 4px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  background: var(--halo-bg-color, #fff);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.calendar-mobile-block__accent {
  width: 4px;
  border-radius: 999px;
}

.calendar-mobile-block__content {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.calendar-mobile-block__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.calendar-mobile-block__title {
  min-width: 0;
  color: var(--halo-text-color, #111827);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.calendar-mobile-block__time,
.calendar-mobile-block__meta {
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.calendar-grid {
  position: relative;
  z-index: 0;
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  width: max(100%, 1052px);
  border: 1px solid var(--halo-border-color, #e5e7eb);
  border-radius: 12px;
  overflow: hidden;
}

.time-column__header,
.day-column__header {
  display: flex;
  justify-content: center;
  padding: 10px 12px;
  background: var(--halo-bg-color-secondary, #f8fafc);
  border-bottom: 1px solid var(--halo-border-color, #e5e7eb);
  box-sizing: border-box;
}

.time-column__header {
  align-items: center;
  font-size: 12px;
  color: var(--halo-text-color-secondary, #6b7280);
}

.day-column__header {
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.time-column__body,
.day-column__body {
  background: var(--halo-bg-color, #fff);
}

.time-column__slot {
  padding: 4px 10px 0 0;
  text-align: right;
  font-size: 12px;
  color: var(--halo-text-color-tertiary, #9ca3af);
  border-top: 1px solid var(--halo-border-color-soft, #f3f4f6);
}

.day-columns {
  display: grid;
  grid-template-columns: repeat(7, minmax(120px, 1fr));
}

.day-column {
  border-left: 1px solid var(--halo-border-color, #e5e7eb);
}

.day-column__header strong {
  color: var(--halo-text-color, #111827);
}

.day-column__header span {
  margin-top: 4px;
  font-size: 12px;
  color: var(--halo-text-color-secondary, #6b7280);
}

.day-column__body {
  position: relative;
  z-index: 0;
  isolation: isolate;
  overflow: hidden;
}

.day-column__lines {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-image: repeating-linear-gradient(
    to bottom,
    transparent,
    transparent calc(56px - 1px),
    var(--halo-border-color-soft, #f3f4f6) calc(56px - 1px),
    var(--halo-border-color-soft, #f3f4f6) 56px
  );
}

.current-time-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: #ef4444;
  transform: translateY(-1px);
  z-index: 4;
  pointer-events: none;
}

.current-time-line__dot {
  position: absolute;
  left: 0;
  top: 50%;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.18);
  transform: translate(-50%, -50%);
}

.calendar-block {
  position: absolute;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  min-width: 0;
  border-radius: 10px;
  padding: 6px 8px;
  color: #fff;
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.12);
  overflow: hidden;
  text-align: center;
}

.calendar-block--split {
  border-radius: 8px;
  padding: 6px;
}

.calendar-block__title {
  font-weight: 700;
  line-height: 1.2;
  width: 100%;
  overflow-wrap: anywhere;
}

.calendar-block__time,
.calendar-block__meta {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.35;
  opacity: 0.95;
  white-space: pre-line;
  width: 100%;
  overflow-wrap: anywhere;
}

.entry-start {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.entry-dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 999px;
  flex: none;
}

.entry-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.entry-title {
  min-width: 0;
  color: var(--halo-text-color, #111827);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.entry-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.entry-meta__item {
  display: inline-flex;
  align-items: center;
  flex: 0 1 auto;
  max-width: 100%;
  width: fit-content;
  min-width: 0;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--halo-bg-color-secondary, #f8fafc);
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.entry-meta__item--wide {
  flex: 0 1 auto;
  max-width: 100%;
  border-radius: 12px;
  white-space: normal;
}

.entry-meta__item--block {
  flex: 0 0 100%;
  width: fit-content;
}

.entry-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.entry-card-header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 16px 20px;
}

.entry-card-header__title {
  color: var(--halo-text-color, #111827);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
}

.entry-card-header__search {
  display: flex;
  justify-content: center;
  min-width: 0;
}

.entry-card-header__actions {
  display: flex;
  justify-content: flex-end;
}

.external-calendar-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 13px;
}

.external-calendar-color {
  width: 56px !important;
  min-width: 56px;
  padding: 4px !important;
  cursor: pointer;
}

.field--checkbox {
  width: fit-content;
}

.entry-search {
  display: flex;
  justify-content: center;
  width: 100%;
  min-width: 0;
}

.entry-search__field {
  position: relative;
  width: min(100%, 460px);
}

.entry-search__icon {
  position: absolute;
  top: 50%;
  left: 12px;
  display: inline-flex;
  align-items: center;
  color: var(--halo-text-color-secondary, #6b7280);
  transform: translateY(-50%);
  pointer-events: none;
}

.entry-search__input {
  width: 100%;
  height: 40px;
  padding: 0 14px 0 38px;
  border: 1px solid var(--halo-border-color, #d1d5db);
  border-radius: 10px;
  background: var(--halo-bg-color, #fff);
  color: var(--halo-text-color, #111827);
  font-size: 14px;
  line-height: 1.5;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.entry-search__input::placeholder {
  color: var(--halo-text-color-secondary, #9ca3af);
}

.entry-search__input:focus {
  border-color: color-mix(in srgb, var(--halo-primary-color, #4f46e5) 42%, var(--halo-border-color, #d1d5db));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--halo-primary-color, #4f46e5) 14%, transparent);
}

.dialog-form {
  display: grid;
  gap: 16px;
  padding: 4px 0;
}

.field-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.field-row--recurrence {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field span {
  font-size: 12px;
  font-weight: 600;
  color: var(--halo-text-color-secondary, #6b7280);
}

.field input,
.field select,
.field textarea {
  width: 100%;
  border: 1px solid var(--halo-border-color, #d1d5db);
  border-radius: 8px;
  padding: 10px 12px;
  font: inherit;
  color: var(--halo-text-color, #111827);
  background: var(--halo-bg-color, #fff);
}

.field textarea {
  resize: vertical;
}

.field--compact {
  width: fit-content;
}

.color-picker {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: auto;
  min-width: 136px;
  height: 40px;
  padding: 4px 10px 4px 4px;
  border: 1px solid var(--halo-border-color, #d1d5db);
  border-radius: 10px;
  background: var(--halo-bg-color, #fff);
  cursor: pointer;
  font: inherit;
}

.color-picker__preview {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  flex: none;
}

.color-picker__value {
  color: var(--halo-text-color, #111827);
  font-size: 12px;
  letter-spacing: 0.02em;
}

.field__color {
  position: absolute;
  width: 1px !important;
  height: 1px !important;
  padding: 0;
  border: 0;
  opacity: 0;
  pointer-events: none;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 960px) {
  .page-body {
    padding-left: 16px !important;
    padding-right: 16px !important;
    padding-bottom: 16px !important;
  }

  .page-alert,
  .overview-card,
  .section-card {
    margin-top: 12px;
  }

  .week-toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    justify-items: center;
    gap: 12px;
  }

  .week-toolbar__nav,
  .week-toolbar__center,
  .week-toolbar__current,
  .week-toolbar__mode {
    width: 100%;
  }

  .week-toolbar__nav {
    grid-column: 1 / -1;
    justify-content: center;
  }

  .week-toolbar__center,
  .week-toolbar__current,
  .week-toolbar__mode {
    grid-column: 1 / -1;
  }

  .week-toolbar__center {
    min-width: 0;
  }

  .week-view-mode {
    width: 100%;
  }

  .week-view-mode :deep(.tabbar-wrapper),
  .week-view-mode :deep(.tabbar-items) {
    width: 100%;
  }

  .week-picker {
    width: 100%;
    max-width: 320px;
  }

  .calendar-grid {
    grid-template-columns: 60px minmax(0, 1fr);
    width: max(100%, 900px);
  }

  .time-column__header,
  .day-column__header {
    padding: 8px;
  }

  .time-column__slot {
    padding-right: 6px;
    font-size: 11px;
  }

  .calendar-block {
    padding: 6px;
    border-radius: 8px;
  }

  .calendar-block__title {
    font-size: 12px;
  }

  .calendar-block__time,
  .calendar-block__meta {
    margin-top: 2px;
    font-size: 10px;
    line-height: 1.25;
  }

  .field-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .card-actions {
    padding-right: 0;
  }
}

@media (max-width: 768px) {
  .calendar-grid {
    grid-template-columns: 48px minmax(0, 1fr);
    width: 100%;
  }

  .calendar-grid-scroll {
    overflow-x: visible;
  }

  .day-columns {
    grid-template-columns: repeat(7, minmax(0, 1fr));
  }

  .day-column__header {
    padding-left: 4px;
    padding-right: 4px;
  }

  .day-column__header strong {
    font-size: 11px;
  }

  .day-column__header span {
    font-size: 10px;
  }

  .time-column__header,
  .time-column__slot {
    font-size: 10px;
  }

  .time-column__slot {
    padding-right: 4px;
  }

  .calendar-block,
  .calendar-block--split {
    align-items: center;
    justify-content: center;
    padding: 5px 4px;
    border-radius: 6px;
  text-align: center;
  }

  .calendar-block__title {
    font-size: 12px;
    line-height: 1.2;
  }

  .calendar-block__time,
  .calendar-block__meta {
    display: none;
  }
}

@media (max-width: 640px) {
  .entry-card-header {
    grid-template-columns: minmax(0, 1fr);
    padding: 14px 16px;
    justify-items: center;
  }

  .entry-search {
    justify-content: stretch;
  }

  .entry-card-header__title,
  .entry-card-header__actions {
    width: 100%;
  }

  .entry-card-header__title {
    text-align: center;
  }

  .entry-card-header__actions {
    justify-content: center;
  }

  .external-calendar-actions {
    justify-content: center;
  }

  .entry-search__field {
    width: 100%;
  }

  .week-toolbar {
    grid-template-columns: minmax(0, 1fr);
  }

  .week-toolbar__nav {
    gap: 10px;
    flex-wrap: wrap;
  }

  .week-toolbar__current {
    justify-content: center;
  }

  .week-view-mode {
    width: 100%;
  }

  .week-view-mode :deep(.tabbar-wrapper),
  .week-view-mode :deep(.tabbar-items) {
    width: 100%;
  }

  .week-view-mode :deep(.tabbar-item) {
    flex: 1 1 0;
    min-width: 0;
  }

  .schedule-view {
    font-size: 14px;
  }

  .page-body {
    padding-left: 12px !important;
    padding-right: 12px !important;
    padding-bottom: calc(76px + env(safe-area-inset-bottom, 0px)) !important;
  }

  .calendar-grid {
    grid-template-columns: 44px minmax(0, 1fr);
  }

  .calendar-grid-scroll {
    padding-bottom: calc(76px + env(safe-area-inset-bottom, 0px));
  }

  .day-column__header strong {
    font-size: 12px;
  }

  .day-column__header span {
    font-size: 10px;
  }

  .time-column__header {
    font-size: 11px;
  }

  .week-toolbar :deep(button),
  .modal-footer :deep(button) {
    min-height: 34px;
  }

  .calendar-mobile-day__header {
    padding: 12px 14px;
  }

  .calendar-mobile-day__list {
    padding: 10px;
  }

  .calendar-mobile-block {
    gap: 10px;
    padding: 10px;
  }

  .calendar-mobile-block__top {
    flex-wrap: wrap;
  }

  .calendar-block {
    padding: 5px 6px;
  }

  .dialog-form {
    gap: 12px;
  }

  .field input,
  .field textarea,
  .week-picker {
    padding: 8px 10px;
  }

  .modal-footer {
    flex-direction: column-reverse;
  }

  .modal-footer :deep(button) {
    width: 100%;
  }
}

</style>
