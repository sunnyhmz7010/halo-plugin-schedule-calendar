<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, reactive, ref } from 'vue'
import type { ExtensionListResult, ScheduleEntry } from '../types/schedule'

const apiBase = '/apis/schedule.calendar.sunny.dev/v1alpha1/scheduleentries'

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
  color: '#0f766e',
})

interface ViewBlock {
  start: string
  end: string
  title: string
  meta?: string
  duration: string
  color: string
}

const resetForm = () => {
  form.title = ''
  form.description = ''
  form.location = ''
  form.startTimeLocal = ''
  form.endTimeLocal = ''
  form.color = '#0f766e'
}

const parseClock = (date: Date, time: string) => {
  const [hours, minutes] = time.split(':').map(Number)
  const next = new Date(date)
  next.setHours(hours, minutes, 0, 0)
  return next
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

const weekStart = computed(() => {
  const now = new Date()
  const copy = new Date(now)
  const day = copy.getDay()
  const diff = day === 0 ? -6 : 1 - day
  copy.setHours(0, 0, 0, 0)
  copy.setDate(copy.getDate() + diff)
  return copy
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

    const occupied = entries.value
      .map((entry) => {
        const start = new Date(entry.spec.startTime)
        const end = new Date(entry.spec.endTime)

        if (end <= startOfDay || start >= endOfDay) {
          return null
        }

        const clippedStart = start < startOfDay ? startOfDay : start
        const clippedEnd = end > endOfDay ? endOfDay : end

        return {
          start: clippedStart.toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false,
          }),
          end: clippedEnd.toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false,
          }),
          title: entry.spec.title,
          meta: [entry.spec.description, entry.spec.location ? `地点：${entry.spec.location}` : '']
            .filter(Boolean)
            .join(' / '),
          duration: formatDuration(clippedStart, clippedEnd),
          color: entry.spec.color || '#0f766e',
        } satisfies ViewBlock
      })
      .filter(Boolean) as ViewBlock[]

    occupied.sort((left, right) => left.start.localeCompare(right.start))

    const free: ViewBlock[] = []
    let cursor = new Date(startOfDay)
    occupied.forEach((block) => {
      const blockStart = parseClock(day, block.start)
      const blockEnd = parseClock(day, block.end)

      if (blockStart > cursor) {
        free.push({
          start: cursor.toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false,
          }),
          end: blockStart.toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false,
          }),
          title: '空闲时间',
          duration: formatDuration(cursor, blockStart),
          color: '#94a3b8',
        })
      }

      if (blockEnd > cursor) {
        cursor = blockEnd
      }
    })

    if (cursor < endOfDay) {
      free.push({
        start: cursor.toLocaleTimeString('zh-CN', {
          hour: '2-digit',
          minute: '2-digit',
          hour12: false,
        }),
        end: '23:59',
        title: '空闲时间',
        duration: formatDuration(cursor, new Date(new Date(day).setHours(23, 59, 0, 0))),
        color: '#94a3b8',
      })
    }

    return {
      id: day.toISOString(),
      label: day.toLocaleDateString('zh-CN', { weekday: 'long' }),
      date: day.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }),
      occupied,
      free,
    }
  })
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

const sortedEntries = computed(() => {
  return [...entries.value].sort((left, right) =>
    new Date(left.spec.startTime).getTime() - new Date(right.spec.startTime).getTime(),
  )
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

onMounted(() => {
  void fetchEntries()
})
</script>

<template>
  <section class="schedule-view">
    <header class="hero">
      <div>
        <p class="eyebrow">Halo 插件控制台页面</p>
        <h1>日程日历</h1>
        <p class="summary">
          统一维护事项时间段，并预览本周已占用与空闲时间。
        </p>
      </div>
      <a class="public-link" href="/schedule-calendar" target="_blank">打开前台页面</a>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <section class="panel panel--form">
      <div class="panel__header">
        <div>
          <p class="panel__eyebrow">事项录入</p>
          <h2>新增时间段</h2>
        </div>
        <span class="panel__badge">{{ sortedEntries.length }} 条事项</span>
      </div>

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
          <textarea v-model="form.description" rows="3" placeholder="可选：补充备注、参与人、准备事项"></textarea>
        </label>

        <label class="field field--color">
          <span>颜色</span>
          <input v-model="form.color" type="color" />
        </label>
      </div>

      <button class="primary-button" :disabled="saving" @click="createEntry">
        {{ saving ? '保存中...' : '保存事项' }}
      </button>
    </section>

    <section class="week">
      <div class="panel__header">
        <div>
          <p class="panel__eyebrow">周视图</p>
          <h2>{{ weekRangeLabel }}</h2>
        </div>
        <span class="panel__badge">{{ loading ? '加载中' : '本周预览' }}</span>
      </div>

      <div class="week-grid">
        <article v-for="day in weekDays" :key="day.id" class="day-card">
          <header class="day-card__header">
            <strong>{{ day.label }}</strong>
            <span>{{ day.date }}</span>
          </header>

          <div class="block-group">
            <p class="block-group__title">已占用</p>
            <div v-if="day.occupied.length" class="block-list">
              <article
                v-for="block in day.occupied"
                :key="`${day.id}-${block.start}-${block.end}-${block.title}`"
                class="block block--occupied"
                :style="{ borderLeftColor: block.color }"
              >
                <div class="block__time">{{ block.start }} - {{ block.end }} · {{ block.duration }}</div>
                <div class="block__title">{{ block.title }}</div>
                <div v-if="block.meta" class="block__meta">{{ block.meta }}</div>
              </article>
            </div>
            <p v-else class="empty">暂无事项</p>
          </div>

          <div class="block-group">
            <p class="block-group__title">空闲</p>
            <div class="block-list">
              <article
                v-for="block in day.free"
                :key="`${day.id}-${block.start}-${block.end}-free`"
                class="block block--free"
              >
                <div class="block__time">{{ block.start }} - {{ block.end }} · {{ block.duration }}</div>
                <div class="block__title">{{ block.title }}</div>
              </article>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="panel__header">
        <div>
          <p class="panel__eyebrow">事项列表</p>
          <h2>已录入事项</h2>
        </div>
      </div>

      <div v-if="sortedEntries.length" class="entry-list">
        <article v-for="entry in sortedEntries" :key="entry.metadata.name" class="entry-card">
          <div class="entry-card__accent" :style="{ background: entry.spec.color || '#0f766e' }"></div>
          <div class="entry-card__content">
            <strong>{{ entry.spec.title }}</strong>
            <p>
              {{ new Date(entry.spec.startTime).toLocaleString('zh-CN', { hour12: false }) }}
              -
              {{ new Date(entry.spec.endTime).toLocaleString('zh-CN', { hour12: false }) }}
            </p>
            <p v-if="entry.spec.location">{{ entry.spec.location }}</p>
            <p v-if="entry.spec.description">{{ entry.spec.description }}</p>
          </div>
          <button class="ghost-button" @click="removeEntry(entry.metadata.name)">删除</button>
        </article>
      </div>
      <p v-else class="empty">还没有事项，可以先新增一个时间段。</p>
    </section>
  </section>
</template>

<style scoped lang="scss">
.schedule-view {
  padding: 32px;
  color: #1f2937;
  background:
    radial-gradient(circle at top left, rgba(15, 118, 110, 0.18), transparent 28%),
    linear-gradient(180deg, #f6f3eb 0%, #eef5f2 100%);
  min-height: 100vh;
}

.hero,
.panel__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
}

.hero {
  margin-bottom: 24px;
}

.eyebrow,
.panel__eyebrow {
  margin: 0 0 6px;
  color: #0f766e;
  font-size: 0.85rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  font-size: clamp(2.1rem, 4vw, 3.5rem);
  line-height: 1;
}

.summary {
  margin-top: 10px;
  max-width: 720px;
  color: #4b5563;
}

.public-link,
.primary-button,
.ghost-button {
  border: none;
  cursor: pointer;
  text-decoration: none;
}

.public-link,
.primary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 12px 18px;
  background: #0f766e;
  color: #fff;
  font-weight: 700;
}

.panel,
.day-card {
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(10px);
}

.panel {
  padding: 24px;
  margin-top: 24px;
}

.panel--form {
  margin-top: 0;
}

.panel__badge {
  border-radius: 999px;
  padding: 8px 12px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 0.9rem;
  font-weight: 700;
}

.form-grid,
.week-grid {
  display: grid;
  gap: 16px;
}

.form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 20px 0;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field span {
  font-size: 0.92rem;
  color: #4b5563;
}

.field input,
.field textarea {
  width: 100%;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: rgba(255, 255, 255, 0.92);
  padding: 14px 16px;
  font: inherit;
  color: inherit;
}

.field--full {
  grid-column: 1 / -1;
}

.field--color input {
  padding: 6px;
  height: 52px;
}

.week {
  margin-top: 28px;
}

.week-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  margin-top: 18px;
}

.day-card {
  padding: 18px;
}

.day-card__header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 16px;
}

.day-card__header span {
  color: #6b7280;
  font-size: 0.92rem;
}

.block-group + .block-group {
  margin-top: 18px;
}

.block-group__title {
  margin-bottom: 10px;
  color: #6b7280;
  font-size: 0.88rem;
}

.block-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.block {
  border-radius: 18px;
  padding: 12px 14px;
  border-left: 5px solid transparent;
}

.block--occupied {
  background: rgba(15, 118, 110, 0.1);
}

.block--free {
  background: rgba(148, 163, 184, 0.14);
}

.block__time,
.block__meta,
.empty,
.entry-card__content p,
.error {
  color: #6b7280;
}

.block__time,
.block__meta,
.entry-card__content p,
.empty {
  font-size: 0.9rem;
}

.block__title {
  margin-top: 6px;
  font-weight: 700;
}

.block__meta {
  margin-top: 6px;
  line-height: 1.5;
}

.entry-list {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.entry-card {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(246, 248, 250, 0.92);
}

.entry-card__accent {
  width: 8px;
  align-self: stretch;
  border-radius: 999px;
}

.entry-card__content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ghost-button {
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(15, 23, 42, 0.06);
  color: #111827;
}

.error {
  margin-bottom: 16px;
}

@media (max-width: 860px) {
  .schedule-view {
    padding: 20px;
  }

  .hero,
  .panel__header {
    flex-direction: column;
    align-items: start;
  }

  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
