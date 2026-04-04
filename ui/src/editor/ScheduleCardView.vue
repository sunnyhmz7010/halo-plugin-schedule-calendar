<script setup lang="ts">
import { computed, ref } from 'vue'
import type { NodeViewProps } from '@halo-dev/richtext-editor'
import { NodeViewWrapper } from '@halo-dev/richtext-editor'
import { VButton, Toast } from '@halo-dev/components'
import MdiCalendarClockOutline from '~icons/mdi/calendar-clock-outline'
import type { ExtensionListResult, ScheduleCard, ScheduleEntry } from '../types/schedule'
import ScheduleCardPickerModal from './ScheduleCardPickerModal.vue'
import ScheduleEntryCreateModal from './ScheduleEntryCreateModal.vue'
import { ENTRY_API, toScheduleCards } from './schedule-card-data'

const props = defineProps<NodeViewProps>()

const pickerVisible = ref(false)
const createVisible = ref(false)
const pickerItems = ref<ScheduleCard[]>([])

const attrs = computed(() => props.node.attrs as ScheduleCard)
const hasSelectedEntry = computed(() => Boolean(attrs.value.name))

const fetchCards = async () => {
  try {
    const { axiosInstance } = await import('@halo-dev/api-client')
    const { data } = await axiosInstance.get<ExtensionListResult<ScheduleEntry>>(ENTRY_API, {
      params: {
        page: 1,
        size: 200,
      },
    })
    pickerItems.value = toScheduleCards(data)
    return true
  } catch (error) {
    console.error(error)
    Toast.error('读取事项列表失败，请稍后重试。')
    return false
  }
}

const openPicker = async () => {
  if (await fetchCards()) {
    pickerVisible.value = true
  }
}

const openCreateModal = () => {
  pickerVisible.value = false
  createVisible.value = true
}

const handleCardSelected = (card: ScheduleCard) => {
  props.updateAttributes(card)
  pickerVisible.value = false
}

const handleCreated = (card: ScheduleCard) => {
  props.updateAttributes(card)
  createVisible.value = false
}

const handleReset = () => {
  props.updateAttributes({
    name: '',
    title: '',
    description: '',
    location: '',
    startTime: '',
    endTime: '',
    recurrenceDescription: '',
    color: '#0f766e',
  })
}

const summaryText = computed(() => {
  if (!hasSelectedEntry.value) {
    return '选择已有事项，或直接在这里添加一个新的事项。'
  }

  return `${attrs.value.startTime || ''} - ${attrs.value.endTime || ''}`.trim()
})
</script>

<template>
  <node-view-wrapper as="div" class="schedule-card-node-view">
    <div
      class="schedule-card-node-view__container"
      :class="{
        'schedule-card-node-view__container--selected': selected,
        'schedule-card-node-view__container--placeholder': !hasSelectedEntry,
      }"
    >
      <template v-if="!hasSelectedEntry">
        <div class="schedule-card-node-view__placeholder-inner">
          <div class="schedule-card-node-view__icon-shell">
            <MdiCalendarClockOutline class="schedule-card-node-view__icon" />
          </div>

          <div class="schedule-card-node-view__placeholder-title">日程卡片</div>
          <div class="schedule-card-node-view__placeholder-summary">{{ summaryText }}</div>

          <div class="schedule-card-node-view__actions schedule-card-node-view__actions--center">
            <VButton type="secondary" @click="openPicker">选择日程</VButton>
            <VButton @click="openCreateModal">添加事项</VButton>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="schedule-card-node-view__badge">日程卡片</div>
        <div class="schedule-card-node-view__title">{{ attrs.title }}</div>
        <div class="schedule-card-node-view__summary">{{ summaryText }}</div>

        <div v-if="attrs.recurrenceDescription" class="schedule-card-node-view__meta schedule-card-node-view__meta--accent">
          {{ attrs.recurrenceDescription }}
        </div>

        <div v-if="attrs.location" class="schedule-card-node-view__meta">地点：{{ attrs.location }}</div>
        <div v-if="attrs.description" class="schedule-card-node-view__meta">备注：{{ attrs.description }}</div>

        <div class="schedule-card-node-view__actions">
          <VButton size="sm" type="secondary" @click="openPicker">选择日程</VButton>
          <VButton size="sm" @click="openCreateModal">添加事项</VButton>
          <VButton size="sm" @click="handleReset">清空</VButton>
        </div>
      </template>
    </div>

    <ScheduleCardPickerModal
      :visible="pickerVisible"
      :items="pickerItems"
      @close="pickerVisible = false"
      @select="handleCardSelected"
      @create="openCreateModal"
    />

    <ScheduleEntryCreateModal
      :visible="createVisible"
      @close="createVisible = false"
      @created="handleCreated"
    />
  </node-view-wrapper>
</template>

<style scoped lang="scss">
.schedule-card-node-view {
  width: calc(100% - 1px);
}

.schedule-card-node-view__container {
  width: 100%;
  overflow: hidden;
  border-radius: 16px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.schedule-card-node-view__container--selected {
  box-shadow: 0 0 0 2px rgba(76, 203, 160, 0.28);
}

.schedule-card-node-view__container--placeholder {
  min-height: 260px;
  border: 2px dashed #d1d5db;
  background: #f9fafb;
}

.schedule-card-node-view__placeholder-inner {
  display: flex;
  min-height: 260px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
  text-align: center;
}

.schedule-card-node-view__icon-shell {
  display: flex;
  width: 84px;
  height: 84px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(76, 203, 160, 0.16);
  color: #69b79c;
}

.schedule-card-node-view__icon {
  font-size: 2rem;
}

.schedule-card-node-view__placeholder-title {
  margin-top: 18px;
  font-size: 1.25rem;
  font-weight: 700;
  color: #111827;
}

.schedule-card-node-view__placeholder-summary {
  margin-top: 8px;
  max-width: 420px;
  font-size: 0.875rem;
  line-height: 1.6;
  color: #6b7280;
}

.schedule-card-node-view__badge {
  font-size: 12px;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.schedule-card-node-view__title {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.schedule-card-node-view__summary {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: #374151;
}

.schedule-card-node-view__meta {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: #6b7280;
}

.schedule-card-node-view__meta--accent {
  color: #0f766e;
  font-weight: 600;
}

.schedule-card-node-view__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.schedule-card-node-view__actions--center {
  justify-content: center;
}
</style>
