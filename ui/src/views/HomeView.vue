<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  IconAddCircle,
  IconCalendar,
  IconDeleteBin,
  IconExternalLinkLine,
  VAlert,
  VButton,
  VCard,
  VEmpty,
  VEntity,
  VEntityContainer,
  VEntityField,
  VLoading,
  VPageHeader,
} from '@halo-dev/components'
import type { ExtensionListResult, ScheduleEntry } from '../types/schedule'

const apiBase = '/apis/schedule.calendar.sunny.dev/v1alpha1/scheduleentries'
const hourHeight = 56
const dayColumnHeight = hourHeight * 24

const loading = ref(false)
const saving = ref(false)
const entries = ref<ScheduleEntry[]>([])
const error = ref('')

const form = reactive({
  title: '',
  description: '',
  location: '',
  startTimeLocal: '',
  endTimeLocal: '',
  color: '#3b82f6',
})

interface CalendarBlock {
  id: string
  title: string
  meta?: string
  startLabel: string
  endLabel: string
  duration: string
  color: string
  top: number
  height: number
}

const hourLabels = Array.from({ length: 24 }, (_, hour) => `${String(hour).padStart(2, '0')}:00`)

const resetForm = () => {
  form.title = ''
  form.description = ''
  form.location = ''
  form.startTimeLocal = ''
  form.endTimeLocal = ''
  form.color = '#3b82f6'
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

const weekStart = computed(() => {
  const now = new Date()
  const copy = new Date(now)
  const day = copy.getDay()
  const diff = day === 0 ? -6 : 1 - day
  copy.setHours(0, 0, 0, 0)
  copy.setDate(copy.getDate() + diff)
  return copy
})

const weekRangeLabel = computed(() => {
  const end = new Date(weekStart.value)
  end.setDate(end.getDate() + 6)

  const formatter = new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
  })

  return `${formatter.format(weekStart.value)} 至 ${formatter.format(end)}`
})

const weekDays = computed(() => {
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(weekStart.value)
    day.setDate(day.getDate() + index)

    const startOfDay = new Date(day)
    startOfDay.setHours(0, 0, 0, 0)

    const endOfDay = new Date(day)
    endOfDay.setDate(endOfDay.getDate() + 1)
    endOfDay.setHours(0, 0, 0, 0)

    const blocks = entries.value
      .map((entry) => {
        const start = new Date(entry.spec.startTime)
        const end = new Date(entry.spec.endTime)

        if (end <= startOfDay || start >= endOfDay) {
          return null
        }

        const clippedStart = start < startOfDay ? startOfDay : start
        const clippedEnd = end > endOfDay ? endOfDay : end
        const startMinutes =
          clippedStart.getHours() * 60 + clippedStart.getMinutes()
        const durationMinutes = Math.max(
          Math.round((clippedEnd.getTime() - clippedStart.getTime()) / 60000),
          30,
        )

        return {
          id: `${entry.metadata.name}-${index}`,
          title: entry.spec.title,
          meta: [entry.spec.location, entry.spec.description].filter(Boolean).join(' · '),
          startLabel: formatClock(clippedStart),
          endLabel: formatClock(clippedEnd),
          duration: formatDuration(clippedStart, clippedEnd),
          color: entry.spec.color || '#3b82f6',
          top: (startMinutes / 60) * hourHeight,
          height: Math.max((durationMinutes / 60) * hourHeight - 6, 26),
        } satisfies CalendarBlock
      })
      .filter(Boolean) as CalendarBlock[]

    return {
      id: day.toISOString(),
      weekday: day.toLocaleDateString('zh-CN', { weekday: 'short' }),
      date: day.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }),
      blocks,
    }
  })
})

const currentWeekEntries = computed(() => {
  const start = weekStart.value.getTime()
  const end = start + 7 * 24 * 60 * 60 * 1000

  return entries.value.filter((entry) => {
    const entryStart = new Date(entry.spec.startTime).getTime()
    const entryEnd = new Date(entry.spec.endTime).getTime()
    return entryEnd > start && entryStart < end
  })
})

const weekOccupiedSummary = computed(() => {
  const totalMinutes = currentWeekEntries.value.reduce((sum, entry) => {
    const start = new Date(entry.spec.startTime)
    const end = new Date(entry.spec.endTime)
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

const formatDateTime = (value: string) =>
  new Date(value).toLocaleString('zh-CN', {
    hour12: false,
  })

const fetchEntries = async () => {
  loading.value = true
  error.value = ''

  try {
    const { data } = await axios.get<ExtensionListResult<ScheduleEntry>>(apiBase, {
      params: {
        page: 1,
        size: 200,
      },
    })
    entries.value = data.items ?? []
  } catch (err) {
    error.value = '事项加载失败，请检查插件权限或 Halo 运行状态。'
    console.error(err)
  } finally {
    loading.value = false
  }
}

const createEntry = async () => {
  error.value = ''

  if (!form.title || !form.startTimeLocal || !form.endTimeLocal) {
    error.value = '标题、开始时间、结束时间是必填项。'
    return
  }

  const startDate = new Date(form.startTimeLocal)
  const endDate = new Date(form.endTimeLocal)
  if (endDate <= startDate) {
    error.value = '结束时间必须晚于开始时间。'
    return
  }

  saving.value = true

  try {
    await axios.post(apiBase, {
      apiVersion: 'schedule.calendar.sunny.dev/v1alpha1',
      kind: 'ScheduleEntry',
      metadata: {
        name: `schedule-entry-${Date.now()}`,
      },
      spec: {
        title: form.title,
        description: form.description || undefined,
        location: form.location || undefined,
        startTime: startDate.toISOString(),
        endTime: endDate.toISOString(),
        color: form.color,
      },
    })

    resetForm()
    await fetchEntries()
  } catch (err) {
    error.value = '事项创建失败。'
    console.error(err)
  } finally {
    saving.value = false
  }
}

const removeEntry = async (name: string) => {
  try {
    await axios.delete(`${apiBase}/${encodeURIComponent(name)}`)
    await fetchEntries()
  } catch (err) {
    error.value = '事项删除失败。'
    console.error(err)
  }
}

const openPublicPage = () => {
  window.open('/schedule-calendar', '_blank')
}

onMounted(() => {
  void fetchEntries()
})
</script>

<template>
  <section class="schedule-view">
    <VPageHeader title="日程日历">
      <template #icon>
        <IconCalendar class="mr-2 h-5 w-5" />
      </template>
      <template #actions>
        <VButton @click="openPublicPage">
          <template #icon>
            <IconExternalLinkLine />
          </template>
          打开前台页面
        </VButton>
      </template>
    </VPageHeader>

    <p class="page-description">
      在系统工具中统一维护事项时间段，并用原生控制台风格展示整周时间栅格。
    </p>

    <VAlert
      v-if="error"
      class="page-alert"
      type="error"
      title="操作失败"
      :description="error"
      :closable="false"
    />

    <div class="view-grid">
      <VCard title="新增事项">
        <div class="form-grid">
          <label class="field">
            <span>事项标题</span>
            <input v-model="form.title" type="text" placeholder="例如：产品评审" />
          </label>

          <label class="field">
            <span>地点 / 链接</span>
            <input v-model="form.location" type="text" placeholder="会议室 A / 腾讯会议" />
          </label>

          <label class="field">
            <span>开始时间</span>
            <input v-model="form.startTimeLocal" type="datetime-local" />
          </label>

          <label class="field">
            <span>结束时间</span>
            <input v-model="form.endTimeLocal" type="datetime-local" />
          </label>

          <label class="field field--full">
            <span>事项说明</span>
            <textarea
              v-model="form.description"
              rows="4"
              placeholder="可选：补充备注、参与人、准备事项"
            ></textarea>
          </label>

          <label class="field">
            <span>颜色</span>
            <input v-model="form.color" type="color" class="field__color" />
          </label>
        </div>

        <div class="form-actions">
          <VButton :loading="saving" @click="createEntry">
            <template #icon>
              <IconAddCircle />
            </template>
            保存事项
          </VButton>
        </div>
      </VCard>

      <VCard :title="`本周周历 · ${weekRangeLabel}`">
        <template #actions>
          <div class="calendar-summary">
            <span>{{ currentWeekEntries.length }} 个事项</span>
            <span>总占用 {{ weekOccupiedSummary }}</span>
          </div>
        </template>

        <div v-if="loading" class="calendar-loading">
          <VLoading />
        </div>

        <div v-else class="calendar-shell">
          <div class="calendar-grid">
            <div class="time-column">
              <div class="time-column__header">时间</div>
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
                <header class="day-column__header">
                  <strong>{{ day.weekday }}</strong>
                  <span>{{ day.date }}</span>
                </header>

                <div
                  class="day-column__body"
                  :style="{ height: `${dayColumnHeight}px` }"
                >
                  <div class="day-column__lines"></div>

                  <article
                    v-for="block in day.blocks"
                    :key="block.id"
                    class="calendar-block"
                    :style="{
                      top: `${block.top}px`,
                      height: `${block.height}px`,
                      background: block.color,
                    }"
                  >
                    <div class="calendar-block__title">{{ block.title }}</div>
                    <div class="calendar-block__time">{{ block.startLabel }} - {{ block.endLabel }}</div>
                    <div class="calendar-block__meta">{{ block.duration }}</div>
                    <div v-if="block.meta" class="calendar-block__meta">{{ block.meta }}</div>
                  </article>
                </div>
              </div>
            </div>
          </div>
        </div>
      </VCard>
    </div>

    <VCard title="事项列表" class="entry-card-list">
      <VEntityContainer v-if="sortedEntries.length">
        <VEntity v-for="entry in sortedEntries" :key="entry.metadata.name">
          <template #start>
            <div class="entry-start">
              <span class="entry-dot" :style="{ background: entry.spec.color || '#3b82f6' }"></span>
              <VEntityField
                :title="entry.spec.title"
                :description="`${formatDateTime(entry.spec.startTime)} - ${formatDateTime(entry.spec.endTime)}`"
              >
                <template #extra>
                  <span v-if="entry.spec.location" class="entry-extra">{{ entry.spec.location }}</span>
                </template>
              </VEntityField>
            </div>
          </template>
          <template #end>
            <VButton ghost @click="removeEntry(entry.metadata.name)">
              <template #icon>
                <IconDeleteBin />
              </template>
              删除
            </VButton>
          </template>
          <template #footer>
            <p v-if="entry.spec.description" class="entry-description">
              {{ entry.spec.description }}
            </p>
          </template>
        </VEntity>
      </VEntityContainer>

      <VEmpty
        v-else
        title="还没有事项"
        message="先在上方录入一个时间段，周历里就会出现对应的色块。"
      />
    </VCard>
  </section>
</template>

<style scoped lang="scss">
.schedule-view {
  padding: 20px;
}

.page-description {
  margin: 12px 0 20px;
  color: #6b7280;
}

.page-alert {
  margin-bottom: 20px;
}

.view-grid {
  display: grid;
  gap: 20px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field span {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
}

.field input,
.field textarea {
  width: 100%;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  padding: 10px 12px;
  font: inherit;
  color: #111827;
  background: #fff;
}

.field textarea {
  resize: vertical;
}

.field--full {
  grid-column: 1 / -1;
}

.field__color {
  min-height: 42px;
  padding: 6px;
}

.form-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.calendar-summary {
  display: flex;
  gap: 12px;
  color: #6b7280;
  font-size: 12px;
}

.calendar-loading {
  padding: 48px 0;
}

.calendar-shell {
  overflow-x: auto;
}

.calendar-grid {
  display: grid;
  grid-template-columns: 72px minmax(980px, 1fr);
  gap: 0;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}

.time-column__header,
.day-column__header {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 56px;
  padding: 12px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.time-column__header {
  font-size: 12px;
  color: #6b7280;
  align-items: center;
}

.time-column__body {
  background: #fff;
}

.time-column__slot {
  padding: 4px 10px 0 0;
  text-align: right;
  font-size: 12px;
  color: #9ca3af;
  border-top: 1px solid #f3f4f6;
}

.day-columns {
  display: grid;
  grid-template-columns: repeat(7, minmax(120px, 1fr));
}

.day-column {
  border-left: 1px solid #e5e7eb;
}

.day-column__header strong {
  color: #111827;
}

.day-column__header span {
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
}

.day-column__body {
  position: relative;
  background: #fff;
}

.day-column__lines {
  position: absolute;
  inset: 0;
  background-image: repeating-linear-gradient(
    to bottom,
    transparent,
    transparent calc(56px - 1px),
    #f3f4f6 calc(56px - 1px),
    #f3f4f6 56px
  );
}

.calendar-block {
  position: absolute;
  left: 8px;
  right: 8px;
  z-index: 1;
  border-radius: 10px;
  padding: 8px 10px;
  color: #fff;
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.12);
  overflow: hidden;
}

.calendar-block__title {
  font-weight: 700;
  line-height: 1.2;
}

.calendar-block__time,
.calendar-block__meta {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.35;
  opacity: 0.95;
}

.entry-card-list {
  margin-top: 20px;
}

.entry-start {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.entry-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  margin-top: 6px;
  flex: none;
}

.entry-extra {
  margin-left: 8px;
  color: #6b7280;
  font-size: 12px;
}

.entry-description {
  margin: 0;
  padding: 0 16px 12px 44px;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 960px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .field--full {
    grid-column: auto;
  }

  .calendar-summary {
    display: none;
  }
}
</style>
