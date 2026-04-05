<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { NodeViewProps } from '@halo-dev/richtext-editor'
import { BlockActionButton, NodeViewWrapper } from '@halo-dev/richtext-editor'
import { Toast } from '@halo-dev/components'
import { axiosInstance } from '@halo-dev/api-client'
import type { ScheduleCard, ScheduleEntry } from '../types/schedule'
import ScheduleCardPickerModal from './ScheduleCardPickerModal.vue'
import ScheduleEntryCreateModal from './ScheduleEntryCreateModal.vue'
import { ENTRY_API, fetchAllScheduleEntries, toScheduleCard, toScheduleCards } from './schedule-card-data'
import MdiCalendarSearchOutline from '~icons/mdi/calendar-search-outline'
import MdiCalendarPlusOutline from '~icons/mdi/calendar-plus-outline'
import MdiCloseCircleOutline from '~icons/mdi/close-circle-outline'

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

const summaryText = computed(() =>
  hasSelectedEntry.value ? `${displayCard.value.startTime || ''} - ${displayCard.value.endTime || ''}`.trim() : '',
)

const selectNode = () => {
  if (typeof props.getPos !== 'function') {
    return
  }

  props.editor.commands.setNodeSelection(props.getPos())
}

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
    <section
      class="schedule-card-node-view__block editor-block group"
      :class="{ 'editor-block--selected': selected }"
    >
      <div class="editor-block__content">
        <div
          class="schedule-card-node-view__content"
          :class="{
            'schedule-card-node-view__content--placeholder': !hasSelectedEntry,
          }"
          contenteditable="false"
          @mousedown.prevent="selectNode"
        >
          <template v-if="!hasSelectedEntry">
            <div class="schedule-card-node-view__placeholder">
              <div class="schedule-card-node-view__placeholder-title">日程卡片</div>
              <div class="schedule-card-node-view__placeholder-text">
                选择已有事项，或直接在这里新增一个事项。
              </div>
            </div>
          </template>

          <template v-else>
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
          </template>
        </div>
      </div>

      <div
        class="schedule-card-node-view__floating-actions invisible group-hover:visible"
        :class="{ '!visible': selected }"
      >
        <div class="editor-block__actions">
        <BlockActionButton tooltip="选择日程" @click="openPicker">
          <template #icon>
            <MdiCalendarSearchOutline />
          </template>
        </BlockActionButton>
        <BlockActionButton tooltip="新增事项" @click="openCreateModal">
          <template #icon>
            <MdiCalendarPlusOutline />
          </template>
        </BlockActionButton>
        <BlockActionButton v-if="hasSelectedEntry" tooltip="清空" @click="handleReset">
          <template #icon>
            <MdiCloseCircleOutline />
          </template>
        </BlockActionButton>
        <BlockActionButton tooltip="删除" @click="deleteNode">
          <template #icon>
            <MdiCloseCircleOutline />
          </template>
        </BlockActionButton>
        </div>
      </div>
    </section>

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
  width: 100%;
}

.schedule-card-node-view__block {
  position: relative;
}

.schedule-card-node-view__floating-actions {
  position: absolute;
  top: -48px;
  right: 0;
  padding-bottom: 8px;
}

.schedule-card-node-view__content {
  min-height: 132px;
  padding: 18px;
  border-radius: 12px;
  border: 1px solid var(--halo-border-color, #e5e7eb);
  background: var(--halo-bg-color, #fff);
}

.schedule-card-node-view__content--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 184px;
  border-style: dashed;
  background: var(--halo-bg-color-secondary, #f8fafc);
}

.schedule-card-node-view__placeholder {
  display: grid;
  gap: 10px;
  max-width: 320px;
  text-align: center;
}

.schedule-card-node-view__placeholder-title {
  color: var(--halo-text-color, #111827);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
}

.schedule-card-node-view__placeholder-text {
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 13px;
  line-height: 1.7;
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
</style>
