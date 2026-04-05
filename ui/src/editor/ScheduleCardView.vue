<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { NodeViewProps } from '@halo-dev/richtext-editor'
import { NodeViewWrapper } from '@halo-dev/richtext-editor'
import { VButton, VEmpty, Toast } from '@halo-dev/components'
import { axiosInstance } from '@halo-dev/api-client'
import type { ScheduleCard, ScheduleEntry } from '../types/schedule'
import ScheduleCardPickerModal from './ScheduleCardPickerModal.vue'
import ScheduleEntryCreateModal from './ScheduleEntryCreateModal.vue'
import { ENTRY_API, fetchAllScheduleEntries, toScheduleCard, toScheduleCards } from './schedule-card-data'

const props = defineProps<NodeViewProps>()

const pickerVisible = ref(false)
const createVisible = ref(false)
const pickerItems = ref<ScheduleCard[]>([])

const attrs = computed(() => props.node.attrs as ScheduleCard)
const displayCard = ref<ScheduleCard>({ ...(attrs.value as ScheduleCard) })
const hasSelectedEntry = computed(() => Boolean(displayCard.value.name))

interface CardMetaItem {
  text: string
  wide?: boolean
  block?: boolean
}

const cloneCard = (value: ScheduleCard): ScheduleCard => ({
  name: value.name || '',
  title: value.title || '',
  description: value.description || '',
  location: value.location || '',
  startTime: value.startTime || '',
  endTime: value.endTime || '',
  recurrenceDescription: value.recurrenceDescription || '',
  nextOccurrenceLabel: value.nextOccurrenceLabel || '',
  color: value.color || '#0f766e',
})

const isSameCard = (left: ScheduleCard, right: ScheduleCard) =>
  left.name === right.name &&
  left.title === right.title &&
  left.description === right.description &&
  left.location === right.location &&
  left.startTime === right.startTime &&
  left.endTime === right.endTime &&
  left.recurrenceDescription === right.recurrenceDescription &&
  left.nextOccurrenceLabel === right.nextOccurrenceLabel &&
  left.color === right.color

const syncDisplayFromAttrs = () => {
  displayCard.value = cloneCard(attrs.value)
}

const fetchCards = async () => {
  try {
    pickerItems.value = toScheduleCards({
      items: await fetchAllScheduleEntries(),
    })
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
  displayCard.value = cloneCard(card)
  props.updateAttributes(card)
  pickerVisible.value = false
}

const handleCreated = (card: ScheduleCard) => {
  displayCard.value = cloneCard(card)
  props.updateAttributes(card)
  createVisible.value = false
}

const handleReset = () => {
  const emptyCard = {
    name: '',
    title: '',
    description: '',
    location: '',
    startTime: '',
    endTime: '',
    recurrenceDescription: '',
    nextOccurrenceLabel: '',
    color: '#0f766e',
  }
  displayCard.value = emptyCard
  props.updateAttributes(emptyCard)
}

const selectedMetaItems = computed<CardMetaItem[]>(() => {
  if (!hasSelectedEntry.value) {
    return []
  }

  const items: CardMetaItem[] = []

  if (displayCard.value.recurrenceDescription) {
    items.push({
      text: `${displayCard.value.recurrenceDescription} · 首次 ${displayCard.value.startTime} - ${displayCard.value.endTime}`,
      wide: true,
      block: true,
    })
  } else if (summaryText.value) {
    items.push({ text: summaryText.value })
  }

  if (displayCard.value.nextOccurrenceLabel) {
    items.push({ text: `下一次出现：${displayCard.value.nextOccurrenceLabel}`, wide: true, block: true })
  }

  if (displayCard.value.location) {
    items.push({ text: `地点：${displayCard.value.location}`, wide: true, block: true })
  }

  if (displayCard.value.description) {
    items.push({ text: `备注：${displayCard.value.description}`, wide: true, block: true })
  }

  return items
})

const refreshSelectedCard = async (silent = true) => {
  if (!attrs.value.name) {
    syncDisplayFromAttrs()
    return
  }

  try {
    const { data } = await axiosInstance.get<ScheduleEntry>(`${ENTRY_API}/${encodeURIComponent(attrs.value.name)}`)
    const latestCard = toScheduleCard(data)
    displayCard.value = latestCard

    if (!isSameCard(attrs.value, latestCard)) {
      props.updateAttributes(latestCard)
    }
  } catch (error) {
    console.error(error)

    if (!silent) {
      Toast.error('读取事项详情失败，请稍后重试。')
    }
  }
}

const summaryText = computed(() => {
  if (!hasSelectedEntry.value) {
    return '选择已有事项，或直接在这里新增一个事项。'
  }

  return `${displayCard.value.startTime || ''} - ${displayCard.value.endTime || ''}`.trim()
})

watch(
  () => attrs.value,
  (value) => {
    if (!value.name) {
      syncDisplayFromAttrs()
      return
    }

    if (!isSameCard(displayCard.value, value)) {
      displayCard.value = cloneCard(value)
    }
  },
  { deep: true },
)

watch(
  () => attrs.value.name,
  (name, previousName) => {
    if (!name) {
      syncDisplayFromAttrs()
      return
    }

    if (name !== previousName) {
      void refreshSelectedCard(true)
    }
  },
)

const handleWindowFocus = () => {
  void refreshSelectedCard(true)
}

const handleVisibilityChange = () => {
  if (!document.hidden) {
    void refreshSelectedCard(true)
  }
}

onMounted(() => {
  syncDisplayFromAttrs()
  void refreshSelectedCard(true)
  window.addEventListener('focus', handleWindowFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('focus', handleWindowFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <node-view-wrapper as="div" class="schedule-card-node-view">
    <div
      class="schedule-card-node-view__panel"
      :class="{
        'schedule-card-node-view__panel--selected': selected,
        'schedule-card-node-view__panel--placeholder': !hasSelectedEntry,
      }"
    >
      <template v-if="!hasSelectedEntry">
        <div class="schedule-card-node-view__state" contenteditable="false">
          <VEmpty title="日程卡片" :message="summaryText">
            <template #actions>
              <div class="schedule-card-node-view__actions schedule-card-node-view__actions--center">
                <VButton type="secondary" @click="openPicker">选择日程</VButton>
                <VButton @click="openCreateModal">新增事项</VButton>
              </div>
            </template>
          </VEmpty>
        </div>
      </template>

      <template v-else>
        <div class="schedule-card-node-view__state schedule-card-node-view__state--selected">
          <div class="schedule-card-node-view__selected-card" contenteditable="false">
            <div class="schedule-card-node-view__selected-card-body">
              <div class="schedule-card-node-view__entry">
                <span class="entry-dot" :style="{ background: displayCard.color || '#3b82f6' }"></span>
                <div class="entry-main">
                  <div class="entry-title">{{ displayCard.title }}</div>
                  <div class="entry-meta">
                    <span
                      v-for="(item, index) in selectedMetaItems"
                      :key="`${displayCard.name}-${index}`"
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

            <div class="schedule-card-node-view__entity-actions">
              <VButton size="sm" type="secondary" @click="openPicker">选择日程</VButton>
              <VButton size="sm" @click="openCreateModal">新增事项</VButton>
              <VButton size="sm" @click="handleReset">清空</VButton>
            </div>
          </div>
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
  margin: 12px 0;
}

.schedule-card-node-view__panel {
  width: 100%;
  border-radius: 12px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease, background-color 0.2s ease;
}

.schedule-card-node-view__panel--selected {
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--halo-primary-color, #4f46e5) 22%, transparent);
}

.schedule-card-node-view__panel--placeholder {
  border: 1px solid var(--halo-border-color, #e5e7eb);
  background: var(--halo-bg-color, #fff);
}

.schedule-card-node-view__state {
  padding: 12px;
}

.schedule-card-node-view__state--selected {
  padding: 0;
}

.schedule-card-node-view__selected-card {
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--halo-border-color, #e5e7eb);
  border-radius: 12px;
  background: var(--halo-bg-color, #fff);
}

.schedule-card-node-view__selected-card-body {
  display: grid;
  gap: 12px;
}

.schedule-card-node-view__entry {
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
}

.schedule-card-node-view__actions--center {
  justify-content: center;
}

.schedule-card-node-view__entity-actions {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--halo-border-color, #eef2f7);
}

.schedule-card-node-view__state :deep(.empty) {
  padding: 18px 0 6px;
}

.schedule-card-node-view__state :deep(.empty__image) {
  margin-bottom: 12px;
}

@media (max-width: 640px) {
  .schedule-card-node-view__selected-card {
    gap: 12px;
    padding: 14px;
  }

  .schedule-card-node-view__actions,
  .schedule-card-node-view__entity-actions {
    justify-content: flex-start;
  }

  .schedule-card-node-view__actions :deep(button),
  .schedule-card-node-view__entity-actions :deep(button) {
    flex: 1 1 100%;
  }
}
</style>
