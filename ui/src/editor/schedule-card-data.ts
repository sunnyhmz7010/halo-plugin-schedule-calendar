import type { ExtensionListResult, ScheduleCard, ScheduleEntry } from '../types/schedule'
import { formatRecurrenceDescription } from '../utils/recurrence'

export const ENTRY_API = '/apis/schedule.calendar.sunny.dev/v1alpha1/scheduleentries'

const pad = (value: number) => String(value).padStart(2, '0')

const formatEntryDateTime = (value?: string) => {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export const toScheduleCard = (entry: ScheduleEntry): ScheduleCard => ({
  name: entry.metadata.name,
  title: entry.spec.title,
  description: entry.spec.description ?? '',
  location: entry.spec.location ?? '',
  startTime: formatEntryDateTime(entry.spec.startTime),
  endTime: formatEntryDateTime(entry.spec.endTime),
  recurrenceDescription: formatRecurrenceDescription(entry.spec.recurrence),
  color: entry.spec.color || '#3b82f6',
})

export const toScheduleCards = (result?: ExtensionListResult<ScheduleEntry>) =>
  (result?.items ?? []).map((entry) => toScheduleCard(entry))
