<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  IconAddCircle,
  IconArrowLeft,
  IconArrowRight,
  IconCalendar,
  IconDeleteBin,
  VAlert,
  VButton,
  VCard,
  VDescription,
  VDescriptionItem,
  VEmpty,
  VEntity,
  VEntityContainer,
  VEntityField,
  VLoading,
  VModal,
  VPageHeader,
  VStatusDot,
  VTag,
  Toast,
} from '@halo-dev/components'
import type { ExtensionListResult, ScheduleEntry } from '../types/schedule'

const apiBase = '/apis/schedule.calendar.sunny.dev/v1alpha1/scheduleentries'
const hourHeight = 56
const dayColumnHeight = hourHeight * 24
const headerHeight = 64

const loading = ref(false)
const saving = ref(false)
const createDialogVisible = ref(false)
const entries = ref<ScheduleEntry[]>([])
const pageError = ref('')
const dialogError = ref('')
const colorInputRef = ref<HTMLInputElement | null>(null)

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
  tooltipMeta?: string
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

const hourLabels = Array.from({ length: 24 }, (_, hour) => `${String(hour).padStart(2, '0')}:00`)

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

const weekRangeLabel = computed(() => {
  const end = new Date(currentWeekStart.value)
  end.setDate(end.getDate() + 6)

  const formatter = new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
  })

  return `${formatter.format(currentWeekStart.value)} 至 ${formatter.format(end)}`
})

const goToCurrentWeek = () => {
  currentWeekStart.value = startOfWeek(new Date())
  syncWeekInput()
}

const buildBlockMeta = (entry: ScheduleEntry) =>
  [entry.spec.location, entry.spec.description].filter(Boolean).join(' · ')

const buildTooltipMeta = (entry: ScheduleEntry) =>
  [entry.spec.location ? `地点：${entry.spec.location}` : '', entry.spec.description]
    .filter(Boolean)
    .join(' · ')

const buildDayBlocks = (
  dayEntries: ScheduleEntry[],
  startOfDay: Date,
  endOfDay: Date,
  dayIndex: number,
) => {
  const rawBlocks = dayEntries
    .map((entry) => {
      const start = new Date(entry.spec.startTime)
      const end = new Date(entry.spec.endTime)

      if (end <= startOfDay || start >= endOfDay) {
        return null
      }

      const clippedStart = start < startOfDay ? startOfDay : start
      const clippedEnd = end > endOfDay ? endOfDay : end
      const startMinutes = clippedStart.getHours() * 60 + clippedStart.getMinutes()
      const endMinutes = clippedEnd.getHours() * 60 + clippedEnd.getMinutes()
      const durationMinutes = Math.max(Math.round((clippedEnd.getTime() - clippedStart.getTime()) / 60000), 30)
      const height = Math.max((durationMinutes / 60) * hourHeight - 6, 26)

      return {
        id: `${entry.metadata.name}-${dayIndex}`,
        title: entry.spec.title,
        meta: buildBlockMeta(entry),
        tooltipMeta: buildTooltipMeta(entry),
        startLabel: formatClock(clippedStart),
        endLabel: formatClock(clippedEnd),
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
    Omit<CalendarBlock, 'left' | 'width' | 'density'>
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

      return {
        ...block,
        left,
        width,
        density,
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

    const blocks = buildDayBlocks(entries.value, startOfDay, endOfDay, index)

    return {
      id: day.toISOString(),
      weekday: day.toLocaleDateString('zh-CN', { weekday: 'short' }),
      date: day.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }),
      blocks,
    }
  })
})

const currentWeekEntries = computed(() => {
  const start = currentWeekStart.value.getTime()
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

const openCreateDialog = () => {
  dialogError.value = ''
  createDialogVisible.value = true
}

const closeCreateDialog = () => {
  createDialogVisible.value = false
  dialogError.value = ''
}

const openColorPicker = () => {
  colorInputRef.value?.click()
}

const fetchEntries = async () => {
  loading.value = true
  pageError.value = ''

  try {
    const { data } = await axiosInstance.get<ExtensionListResult<ScheduleEntry>>(apiBase, {
      params: {
        page: 1,
        size: 200,
      },
    })
    entries.value = data.items ?? []
  } catch (err) {
    pageError.value = '事项加载失败，请检查插件权限或 Halo 运行状态。'
    console.error(err)
  } finally {
    loading.value = false
  }
}

const createEntry = async () => {
  dialogError.value = ''

  if (!form.title || !form.startTimeLocal || !form.endTimeLocal) {
    dialogError.value = '标题、开始时间、结束时间是必填项。'
    return
  }

  const startDate = new Date(form.startTimeLocal)
  const endDate = new Date(form.endTimeLocal)
  if (endDate <= startDate) {
    dialogError.value = '结束时间必须晚于开始时间。'
    return
  }

  saving.value = true

  try {
    await axiosInstance.post(apiBase, {
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
    closeCreateDialog()
    await fetchEntries()
  } catch (err) {
    dialogError.value = '事项创建失败。'
    console.error(err)
  } finally {
    saving.value = false
  }
}

const removeEntry = async (name: string) => {
  pageError.value = ''
  const previousEntries = [...entries.value]

  try {
    entries.value = entries.value.filter((entry) => entry.metadata.name !== name)
    await axiosInstance.delete(`${apiBase}/${encodeURIComponent(name)}`)
    Toast.success('事项已删除')
  } catch (err) {
    entries.value = previousEntries
    pageError.value = '事项删除失败。'
    Toast.error('事项删除失败')
    console.error(err)
  }
}

onMounted(() => {
  syncWeekInput()
  void fetchEntries()
})
</script>

<template>
  <section class="schedule-view">
    <VPageHeader title="日程日历">
      <template #icon>
        <IconCalendar class="mr-2 h-5 w-5" />
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

      <VCard class="overview-card">
        <VDescription>
          <VDescriptionItem label="周范围" :content="weekRangeLabel" />
          <VDescriptionItem label="事项数">
            <VTag theme="default">{{ currentWeekEntries.length }} 个事项</VTag>
          </VDescriptionItem>
          <VDescriptionItem label="总占用">
            <VStatusDot state="success" :text="weekOccupiedSummary" />
          </VDescriptionItem>
        </VDescription>
      </VCard>

      <VCard class="section-card">
        <div class="week-toolbar">
          <div class="week-toolbar__side week-toolbar__side--left">
            <VButton @click="moveWeek(-1)">
              <template #icon>
                <IconArrowLeft />
              </template>
              上一周
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

          <div class="week-toolbar__side week-toolbar__side--right">
            <VButton @click="goToCurrentWeek">回到本周</VButton>
            <VButton @click="moveWeek(1)">
              下一周
              <template #icon>
                <IconArrowRight />
              </template>
            </VButton>
          </div>
        </div>

        <div v-if="loading" class="calendar-loading">
          <VLoading />
        </div>

        <div v-else class="calendar-shell">
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

                  <article
                    v-for="block in day.blocks"
                    :key="block.id"
                    class="calendar-block"
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
                  <div v-if="block.density === 'full'" class="calendar-block__meta">{{ block.duration }}</div>
                  <div v-if="block.density === 'full' && block.meta" class="calendar-block__meta">
                    {{ block.meta }}
                  </div>
                </article>
                </div>
              </div>
            </div>
          </div>
        </div>
      </VCard>

      <VCard title="事项" class="section-card">
        <template #actions>
          <div class="card-actions">
            <VButton type="secondary" @click="openCreateDialog">
              <template #icon>
                <IconAddCircle />
              </template>
              新增事项
            </VButton>
          </div>
        </template>

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
          message="新增一个事项后，会同时显示在下方列表和上方周历中。"
        />
      </VCard>
    </div>

    <VModal
      :visible="createDialogVisible"
      title="新增事项"
      :width="720"
      :layer-closable="false"
      :body-class="['schedule-modal-body']"
      @update:visible="createDialogVisible = $event"
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
          <VButton @click="closeCreateDialog">取消</VButton>
          <VButton type="primary" :loading="saving" @click="createEntry">
            保存事项
          </VButton>
        </div>
      </template>
    </VModal>
  </section>
</template>

<style scoped lang="scss">
.schedule-view {
  padding: 0;
}

.page-body {
  padding-left: 20px !important;
  padding-right: 20px !important;
  padding-bottom: 20px !important;
}

.page-alert,
.overview-card,
.section-card {
  margin-top: 16px;
}

.week-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.week-toolbar__side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.week-toolbar__side--right {
  justify-content: flex-end;
}

.week-toolbar__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 220px;
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
  overflow-x: auto;
}

.calendar-grid {
  display: grid;
  grid-template-columns: 72px minmax(980px, 1fr);
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
}

.day-column__lines {
  position: absolute;
  inset: 0;
  background-image: repeating-linear-gradient(
    to bottom,
    transparent,
    transparent calc(56px - 1px),
    var(--halo-border-color-soft, #f3f4f6) calc(56px - 1px),
    var(--halo-border-color-soft, #f3f4f6) 56px
  );
}

.calendar-block {
  position: absolute;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  padding: 8px 10px;
  color: #fff;
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.12);
  overflow: hidden;
  text-align: center;
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

.entry-start {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.entry-dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 999px;
  flex: none;
}

.entry-extra {
  margin-left: 8px;
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 12px;
}

.entry-description {
  margin: 0;
  padding: 0 16px 12px 44px;
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 13px;
  line-height: 1.6;
}

.card-actions {
  padding-right: 8px;
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
    flex-wrap: wrap;
    justify-content: center;
    gap: 12px;
  }

  .week-toolbar__side,
  .week-toolbar__side--right {
    justify-content: center;
    flex: none;
    width: 100%;
  }

  .week-toolbar__center {
    width: 100%;
    min-width: 0;
  }

  .week-picker {
    width: 100%;
    max-width: 320px;
  }

  .calendar-grid {
    grid-template-columns: 60px minmax(700px, 1fr);
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

  .entry-description {
    padding-left: 22px;
  }

  .field-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .card-actions {
    padding-right: 0;
  }
}

@media (max-width: 640px) {
  .schedule-view {
    font-size: 14px;
  }

  .page-body {
    padding-left: 12px !important;
    padding-right: 12px !important;
    padding-bottom: 12px !important;
  }

  .calendar-grid {
    grid-template-columns: 52px minmax(560px, 1fr);
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

  .dialog-form {
    gap: 12px;
  }

  .field input,
  .field textarea,
  .week-picker {
    padding: 8px 10px;
  }
}
</style>
