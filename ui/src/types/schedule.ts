export interface ScheduleEntrySpec {
  title: string
  description?: string
  location?: string
  startTime: string
  endTime: string
  color?: string
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
