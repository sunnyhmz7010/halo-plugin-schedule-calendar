import { axiosInstance } from '@halo-dev/api-client'
import type { ExtensionListResult, ScheduleCard, ScheduleEntry } from '../types/schedule'
import {
  formatDisplayDateTime,
  formatRecurrenceDescription,
  getNextOccurrenceLabel,
} from '../utils/recurrence'

export const ENTRY_API = '/apis/schedule.calendar.sunny.dev/v1alpha1/scheduleentries'
const ENTRY_PAGE_SIZE = 200

const formatEntryDateTime = (value?: string) => {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return formatDisplayDateTime(date)
}

export const toScheduleCard = (entry: ScheduleEntry): ScheduleCard => ({
  name: entry.metadata.name,
  title: entry.spec.title,
  description: entry.spec.description ?? '',
  location: entry.spec.location ?? '',
  attachments: entry.spec.attachments ?? [],
  startTime: formatEntryDateTime(entry.spec.startTime),
  endTime: formatEntryDateTime(entry.spec.endTime),
  recurrenceDescription: formatRecurrenceDescription(entry.spec.recurrence),
  nextOccurrenceLabel: getNextOccurrenceLabel(entry),
  color: entry.spec.color || '#3b82f6',
})

export const toScheduleCards = (result?: ExtensionListResult<ScheduleEntry>) =>
  (result?.items ?? []).map((entry) => toScheduleCard(entry))

export const fetchAllScheduleEntries = async () => {
  const entries: ScheduleEntry[] = []
  const seenNames = new Set<string>()
  let page = 1

  while (true) {
    const { data } = await axiosInstance.get<ExtensionListResult<ScheduleEntry>>(ENTRY_API, {
      params: {
        page,
        size: ENTRY_PAGE_SIZE,
      },
    })

    const pageItems = data.items ?? []
    let addedCount = 0

    pageItems.forEach((entry) => {
      const name = entry.metadata.name
      if (seenNames.has(name)) {
        return
      }

      seenNames.add(name)
      entries.push(entry)
      addedCount += 1
    })

    if (!pageItems.length || pageItems.length < ENTRY_PAGE_SIZE || data.last === true || data.hasNext === false) {
      break
    }

    if (addedCount === 0) {
      break
    }

    page += 1
  }

  return entries
}
