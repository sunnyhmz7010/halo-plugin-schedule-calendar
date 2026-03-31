import { describe, expect, it } from 'vitest'
import type { ScheduleEntry, ScheduleEntryRecurrence } from '../types/schedule'
import {
  expandEntryOccurrences,
  formatEntryScheduleSummary,
  formatRecurrenceDescription,
} from './recurrence'

const createRecurringEntry = (recurrence?: ScheduleEntryRecurrence): ScheduleEntry => ({
  metadata: {
    name: 'daily-standup',
  },
  spec: {
    title: 'Daily Standup',
    startTime: new Date(2026, 2, 30, 9, 0, 0).toISOString(),
    endTime: new Date(2026, 2, 30, 9, 30, 0).toISOString(),
    recurrence,
  },
})

describe('recurrence utils', () => {
  it('expands recurring entries inside the requested range', () => {
    const entry = createRecurringEntry({
      frequency: 'DAILY',
      interval: 1,
      until: '2026-04-03',
    })

    const occurrences = expandEntryOccurrences(
      entry,
      new Date(2026, 3, 1, 0, 0, 0),
      new Date(2026, 3, 5, 0, 0, 0),
    )

    expect(
      occurrences.map((occurrence) => ({
        month: occurrence.start.getMonth(),
        date: occurrence.start.getDate(),
        hour: occurrence.start.getHours(),
        minute: occurrence.start.getMinutes(),
      })),
    ).toEqual([
      { month: 3, date: 1, hour: 9, minute: 0 },
      { month: 3, date: 2, hour: 9, minute: 0 },
      { month: 3, date: 3, hour: 9, minute: 0 },
    ])
  })

  it('formats recurrence labels and summaries', () => {
    const recurrence: ScheduleEntryRecurrence = {
      frequency: 'WEEKLY',
      interval: 2,
      until: '2026-05-01',
    }

    expect(formatRecurrenceDescription(recurrence)).toBe('重复：每2周，截止 2026-05-01')
    expect(formatEntryScheduleSummary(createRecurringEntry(recurrence))).toContain('重复：每2周')
  })
})
