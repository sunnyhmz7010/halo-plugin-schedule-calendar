import type {
  ScheduleEntry,
  ScheduleEntryRecurrence,
  ScheduleEntryRecurrenceFrequency,
  ScheduleEntrySpec,
} from '../types/schedule'

export interface ScheduleOccurrence {
  id: string
  entry: ScheduleEntry
  start: Date
  end: Date
}

type SupportedRecurrenceFrequency = Exclude<ScheduleEntryRecurrenceFrequency, 'NONE'>

const recurringFrequencies: SupportedRecurrenceFrequency[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY']

const clockFormatter = new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

const startOfDay = (value: Date) => {
  const next = new Date(value)
  next.setHours(0, 0, 0, 0)
  return next
}

const toDateKey = (value: Date) => {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const normalizeInterval = (interval?: number) => {
  if (!interval || Number.isNaN(interval) || interval < 1) {
    return 1
  }

  return Math.trunc(interval)
}

const isSupportedRecurrenceFrequency = (
  frequency?: ScheduleEntryRecurrenceFrequency,
): frequency is SupportedRecurrenceFrequency =>
  !!frequency && recurringFrequencies.includes(frequency as SupportedRecurrenceFrequency)

const resolveRecurrence = (recurrence?: ScheduleEntryRecurrence) => {
  if (!isSupportedRecurrenceFrequency(recurrence?.frequency)) {
    return null
  }

  return {
    frequency: recurrence.frequency,
    interval: normalizeInterval(recurrence.interval),
    until: recurrence.until,
  }
}

const advanceOccurrence = (
  value: Date,
  frequency: SupportedRecurrenceFrequency,
  interval: number,
) => {
  const next = new Date(value)

  switch (frequency) {
    case 'DAILY':
      next.setDate(next.getDate() + interval)
      return next
    case 'WEEKLY':
      next.setDate(next.getDate() + interval * 7)
      return next
    case 'MONTHLY':
      next.setMonth(next.getMonth() + interval)
      return next
    case 'YEARLY':
      next.setFullYear(next.getFullYear() + interval)
      return next
  }
}

const alignOccurrenceStart = (
  baseStart: Date,
  baseEnd: Date,
  rangeStart: Date,
  frequency: SupportedRecurrenceFrequency,
  interval: number,
) => {
  const duration = Math.max(baseEnd.getTime() - baseStart.getTime(), 0)
  const target = new Date(rangeStart.getTime() - duration)

  if (baseStart >= target) {
    return new Date(baseStart)
  }

  const cursor = new Date(baseStart)
  let steps = 0

  if (frequency === 'DAILY') {
    const diffDays = Math.floor(
      (startOfDay(target).getTime() - startOfDay(baseStart).getTime()) / (24 * 60 * 60 * 1000),
    )
    steps = Math.max(Math.floor(diffDays / interval), 0)
  } else if (frequency === 'WEEKLY') {
    const diffDays = Math.floor(
      (startOfDay(target).getTime() - startOfDay(baseStart).getTime()) / (24 * 60 * 60 * 1000),
    )
    steps = Math.max(Math.floor(diffDays / 7 / interval), 0)
  } else if (frequency === 'MONTHLY') {
    const diffMonths =
      (target.getFullYear() - baseStart.getFullYear()) * 12 + target.getMonth() - baseStart.getMonth()
    steps = Math.max(Math.floor(diffMonths / interval), 0)
  } else if (frequency === 'YEARLY') {
    const diffYears = target.getFullYear() - baseStart.getFullYear()
    steps = Math.max(Math.floor(diffYears / interval), 0)
  }

  let next = advanceOccurrence(cursor, frequency, steps * interval)
  while (next.getTime() + duration <= rangeStart.getTime()) {
    next = advanceOccurrence(next, frequency, interval)
  }

  return next
}

export const isRecurringEntry = (value: ScheduleEntry | ScheduleEntrySpec) => {
  const spec = 'spec' in value ? value.spec : value
  return resolveRecurrence(spec.recurrence) !== null
}

export const formatRecurrenceDescription = (recurrence?: ScheduleEntryRecurrence) => {
  const normalized = resolveRecurrence(recurrence)
  if (!normalized) {
    return ''
  }

  const label =
    normalized.frequency === 'DAILY'
      ? normalized.interval === 1
        ? '重复：每天'
        : `重复：每${normalized.interval}天`
      : normalized.frequency === 'WEEKLY'
        ? normalized.interval === 1
          ? '重复：每周'
          : `重复：每${normalized.interval}周`
        : normalized.frequency === 'MONTHLY'
          ? normalized.interval === 1
            ? '重复：每月'
            : `重复：每${normalized.interval}个月`
          : normalized.interval === 1
            ? '重复：每年'
            : `重复：每${normalized.interval}年`

  if (!normalized.until) {
    return label
  }

  return `${label}，截止 ${normalized.until}`
}

export const formatEntryScheduleSummary = (entry: ScheduleEntry) => {
  const recurrenceLabel = formatRecurrenceDescription(entry.spec.recurrence)
  const start = new Date(entry.spec.startTime)
  const end = new Date(entry.spec.endTime)

  if (!recurrenceLabel) {
    return `${dateTimeFormatter.format(start)} - ${dateTimeFormatter.format(end)}`
  }

  const endLabel =
    toDateKey(start) === toDateKey(end) ? clockFormatter.format(end) : dateTimeFormatter.format(end)

  return `${recurrenceLabel} · 首次 ${dateTimeFormatter.format(start)} - ${endLabel}`
}

export const expandEntryOccurrences = (
  entry: ScheduleEntry,
  rangeStart: Date,
  rangeEnd: Date,
) => {
  const baseStart = new Date(entry.spec.startTime)
  const baseEnd = new Date(entry.spec.endTime)

  if (Number.isNaN(baseStart.getTime()) || Number.isNaN(baseEnd.getTime()) || baseEnd <= baseStart) {
    return [] as ScheduleOccurrence[]
  }

  const recurrence = resolveRecurrence(entry.spec.recurrence)
  if (!recurrence) {
    if (baseEnd <= rangeStart || baseStart >= rangeEnd) {
      return [] as ScheduleOccurrence[]
    }

    return [
      {
        id: entry.metadata.name,
        entry,
        start: baseStart,
        end: baseEnd,
      },
    ] satisfies ScheduleOccurrence[]
  }

  const results: ScheduleOccurrence[] = []
  const duration = baseEnd.getTime() - baseStart.getTime()
  let cursor = alignOccurrenceStart(baseStart, baseEnd, rangeStart, recurrence.frequency, recurrence.interval)

  while (cursor < rangeEnd) {
    if (recurrence.until && toDateKey(cursor) > recurrence.until) {
      break
    }

    const end = new Date(cursor.getTime() + duration)
    if (end > rangeStart && cursor < rangeEnd) {
      results.push({
        id: `${entry.metadata.name}-${cursor.toISOString()}`,
        entry,
        start: new Date(cursor),
        end,
      })
    }

    cursor = advanceOccurrence(cursor, recurrence.frequency, recurrence.interval)
  }

  return results
}
