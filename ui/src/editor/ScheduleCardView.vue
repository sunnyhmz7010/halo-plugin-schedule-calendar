<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { NodeViewProps } from '@halo-dev/richtext-editor'
import { NodeViewWrapper } from '@halo-dev/richtext-editor'
import { VButton, VEmpty, Toast } from '@halo-dev/components'
import type { ExtensionListResult, ScheduleCard, ScheduleEntry } from '../types/schedule'
import ScheduleCardPickerModal from './ScheduleCardPickerModal.vue'
import ScheduleEntryCreateModal from './ScheduleEntryCreateModal.vue'
import { ENTRY_API, toScheduleCard, toScheduleCards } from './schedule-card-data'

const props = defineProps<NodeViewProps>()

const pickerVisible = ref(false)
const createVisible = ref(false)
const pickerItems = ref<ScheduleCard[]>([])

const attrs = computed(() => props.node.attrs as ScheduleCard)
const displayCard = ref<ScheduleCard>({ ...(attrs.value as ScheduleCard) })
const hasSelectedEntry = computed(() => Boolean(displayCard.value.name))

const cloneCard = (value: ScheduleCard): ScheduleCard => ({
  name: value.name || '',
  title: value.title || '',
  description: value.description || '',
  location: value.location || '',
  startTime: value.startTime || '',
  endTime: value.endTime || '',
  recurrenceDescription: value.recurrenceDescription || '',
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
  left.color === right.color

const syncDisplayFromAttrs = () => {
  displayCard.value = cloneCard(attrs.value)
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
    color: '#0f766e',
  }
  displayCard.value = emptyCard
  props.updateAttributes(emptyCard)
}

const selectedDetailLines = computed(() => {
  if (!hasSelectedEntry.value) {
    return []
  }

  const items: string[] = []

  if (displayCard.value.recurrenceDescription) {
    items.push(`${displayCard.value.recurrenceDescription} · 首次 ${displayCard.value.startTime} - ${displayCard.value.endTime}`)
  } else if (summaryText.value) {
    items.push(summaryText.value)
  }

  if (displayCard.value.location) {
    items.push(`地点：${displayCard.value.location}`)
  }

  if (displayCard.value.description) {
    items.push(`备注：${displayCard.value.description}`)
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
    return '选择已有事项，或直接在这里添加一个新的事项。'
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
                <VButton @click="openCreateModal">添加事项</VButton>
              </div>
            </template>
          </VEmpty>
        </div>
      </template>

      <template v-else>
        <div class="schedule-card-node-view__state schedule-card-node-view__state--selected">
          <div class="schedule-card-node-view__selected-card" contenteditable="false">
            <div class="schedule-card-node-view__entry">
              <span class="entry-dot" :style="{ background: displayCard.color || '#3b82f6' }"></span>
              <div class="entry-main">
                <div class="entry-title">{{ displayCard.title }}</div>
                <div class="entry-details">
                  <div
                    v-for="(line, index) in selectedDetailLines"
                    :key="`${displayCard.name}-${index}`"
                    class="entry-detail"
                  >
                    {{ line }}
                  </div>
                </div>
              </div>
            </div>

            <div class="schedule-card-node-view__entity-actions">
              <VButton size="sm" type="secondary" @click="openPicker">选择日程</VButton>
              <VButton size="sm" @click="openCreateModal">添加事项</VButton>
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
}

.schedule-card-node-view__panel {
  width: 100%;
  border-radius: 12px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease, background-color 0.2s ease;
}

.schedule-card-node-view__panel--selected {
  background: color-mix(in srgb, var(--halo-primary-color, #4f46e5) 4%, transparent);
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
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid color-mix(in srgb, var(--halo-primary-color, #4f46e5) 14%, var(--halo-border-color, #e5e7eb));
  border-radius: 12px;
  background: var(--halo-bg-color, #fff);
}

.schedule-card-node-view__entry {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
  padding: 2px 0;
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
  gap: 6px;
}

.entry-title {
  min-width: 0;
  color: var(--halo-text-color, #111827);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.entry-details {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.entry-detail {
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
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
  justify-content: flex-end;
  gap: 8px;
}

.schedule-card-node-view__state :deep(.empty) {
  padding: 18px 0 6px;
}

.schedule-card-node-view__state :deep(.empty__image) {
  margin-bottom: 12px;
}

@media (max-width: 640px) {
  .schedule-card-node-view__selected-card {
    grid-template-columns: minmax(0, 1fr);
  }

  .schedule-card-node-view__entity-actions {
    justify-content: flex-start;
  }
}
</style>
