export type ScheduleEntryRecurrenceFrequency = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'

export interface ScheduleEntryRecurrence {
  frequency?: ScheduleEntryRecurrenceFrequency
  interval?: number
  until?: string
}

export interface ScheduleEntrySpec {
  title: string
  description?: string
  location?: string
  startTime: string
  endTime: string
  color?: string
  recurrence?: ScheduleEntryRecurrence
}

export interface ScheduleEntry {
  metadata: {
    name: string
    creationTimestamp?: string
  }
  spec: ScheduleEntrySpec
}

export interface ExtensionListResult<T> {
  items: T[]
}
