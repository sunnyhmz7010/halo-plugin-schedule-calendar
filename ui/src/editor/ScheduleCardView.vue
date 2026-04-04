<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
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

interface CardMetaItem {
  text: string
  wide?: boolean
  block?: boolean
}

const fetchCards = async () => {
  try {
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

const selectedMetaItems = computed<CardMetaItem[]>(() => {
  if (!hasSelectedEntry.value) {
    return []
  }

  const items: CardMetaItem[] = []

  if (summaryText.value) {
    items.push({ text: summaryText.value })
  }

  if (attrs.value.recurrenceDescription) {
    items.push({ text: attrs.value.recurrenceDescription, wide: true, block: true })
  }

  if (attrs.value.location) {
    items.push({ text: `地点：${attrs.value.location}`, wide: true, block: true })
  }

  if (attrs.value.description) {
    items.push({ text: `备注：${attrs.value.description}`, wide: true, block: true })
  }

  return items
})

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

          <div class="schedule-card-node-view__actions schedule-card-node-view__actions--center" contenteditable="false">
            <VButton type="secondary" @click="openPicker">选择日程</VButton>
            <VButton @click="openCreateModal">添加事项</VButton>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="schedule-card-node-view__selected">
          <div class="schedule-card-node-view__badge">日程卡片</div>

          <div class="entry-start">
            <span class="entry-dot" :style="{ background: attrs.color || '#3b82f6' }"></span>
            <div class="entry-main">
              <div class="entry-title">{{ attrs.title }}</div>
              <div class="entry-meta">
                <span
                  v-for="(item, index) in selectedMetaItems"
                  :key="`${attrs.name}-${index}`"
                  class="entry-meta__item"
                  :class="{
                    'entry-meta__item--wide': item.wide,
                    'entry-meta__item--block': item.block,
                  }"
                >
                  {{ item.text }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div class="schedule-card-node-view__actions" contenteditable="false">
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
  border: 1px solid var(--halo-border-color, #e5e7eb);
  background: var(--halo-bg-color, #fff);
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.schedule-card-node-view__container--selected {
  border-color: rgba(76, 203, 160, 0.6);
  box-shadow: 0 0 0 2px rgba(76, 203, 160, 0.18);
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
  color: var(--halo-text-color-secondary, #6b7280);
}

.schedule-card-node-view__selected {
  display: grid;
  gap: 12px;
  padding: 18px;
}

.entry-start {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.entry-dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 999px;
  flex: none;
}

.entry-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.entry-title {
  min-width: 0;
  color: var(--halo-text-color, #111827);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.entry-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.entry-meta__item {
  display: inline-flex;
  align-items: center;
  flex: 0 1 auto;
  max-width: 100%;
  min-width: 0;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--halo-bg-color-secondary, #f8fafc);
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.entry-meta__item--wide {
  flex: 1 1 320px;
  max-width: 100%;
  border-radius: 12px;
  white-space: normal;
}

.entry-meta__item--block {
  flex-basis: 100%;
}

.schedule-card-node-view__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 0 18px 18px;
}

.schedule-card-node-view__actions--center {
  justify-content: center;
}
</style>
