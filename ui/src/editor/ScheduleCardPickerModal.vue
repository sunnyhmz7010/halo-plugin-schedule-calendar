<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { VButton, VEmpty, VEntity, VEntityContainer, VModal } from '@halo-dev/components'
import type { ScheduleCard } from '../types/schedule'

const props = defineProps<{
  visible: boolean
  items: ScheduleCard[]
}>()

const emit = defineEmits<{
  close: []
  select: [item: ScheduleCard]
  create: []
}>()

const keyword = ref('')
const keywordInputRef = ref<HTMLInputElement | null>(null)

interface PickerMetaItem {
  text: string
  wide?: boolean
  block?: boolean
}

const buildCardSummary = (card: ScheduleCard) => `${card.startTime} - ${card.endTime}`

const buildMetaLines = (card: ScheduleCard): PickerMetaItem[] => {
  const items: PickerMetaItem[] = [
    card.recurrenceDescription
      ? {
          text: `${card.recurrenceDescription} · 首次 ${buildCardSummary(card)}`,
          wide: true,
          block: true,
        }
      : { text: buildCardSummary(card) },
  ]

  if (card.location) {
    items.push({ text: `地点：${card.location}`, wide: true, block: true })
  }

  if (card.description) {
    items.push({ text: `备注：${card.description}`, wide: true, block: true })
  }

  return items
}

const buildCardSearchText = (card: ScheduleCard) =>
  [
    card.name,
    card.title,
    card.location,
    card.description,
    card.startTime,
    card.endTime,
    card.recurrenceDescription,
  ]
    .filter(Boolean)
    .join(' ')
    .toLocaleLowerCase()

const filteredItems = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLocaleLowerCase()

  if (!normalizedKeyword) {
    return props.items
  }

  return props.items.filter((card) => buildCardSearchText(card).includes(normalizedKeyword))
})

const focusKeywordInput = () => {
  void nextTick(() => keywordInputRef.value?.focus())
}

const handleClose = () => {
  emit('close')
}

const handleVisibleUpdate = (visible: boolean) => {
  if (!visible) {
    handleClose()
  }
}

const handleSelect = (item: ScheduleCard) => {
  emit('select', item)
}

const handleCreate = () => {
  emit('create')
}

const handleKeywordKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    event.preventDefault()
    handleClose()
    return
  }

  if (event.key === 'Enter' && filteredItems.value.length === 1) {
    event.preventDefault()
    handleSelect(filteredItems.value[0])
  }
}

onMounted(() => {
  focusKeywordInput()
})
</script>

<template>
  <VModal
    :visible="visible"
    title="选择日程卡片"
    :width="760"
    :body-class="['schedule-card-picker-modal__body']"
    @update:visible="handleVisibleUpdate"
  >
    <div class="schedule-card-picker-modal">
      <label class="schedule-card-picker-modal__search">
        <span>搜索事项</span>
        <input
          ref="keywordInputRef"
          v-model="keyword"
          type="search"
          placeholder="搜索标题、名称、地点、备注或循环规则"
          @keydown="handleKeywordKeydown"
        />
      </label>

      <div class="schedule-card-picker-modal__count">共 {{ filteredItems.length }} 个可选事项</div>

      <VEntityContainer v-if="filteredItems.length">
        <VEntity
          v-for="item in filteredItems"
          :key="item.name"
        >
          <template #start>
            <div
              class="entry-start entry-start--interactive"
              role="button"
              tabindex="0"
              @mousedown.stop.prevent
              @click.stop.prevent="handleSelect(item)"
            >
              <span class="entry-dot" :style="{ background: item.color || '#3b82f6' }"></span>

              <div class="entry-main">
                <div class="entry-title">{{ item.title }}</div>
                <div class="entry-meta">
                  <span
                    v-for="(metaItem, index) in buildMetaLines(item)"
                    :key="`${item.name}-${index}`"
                    class="entry-meta__item"
                    :class="{
                      'entry-meta__item--wide': metaItem.wide,
                      'entry-meta__item--block': metaItem.block,
                    }"
                  >
                    {{ metaItem.text }}
                  </span>
                </div>
              </div>
            </div>
          </template>

          <template #end>
            <VButton type="secondary" @click="handleSelect(item)">选择</VButton>
          </template>
        </VEntity>
      </VEntityContainer>

      <VEmpty
        v-else
        title="没有匹配的事项"
        message="可以换个关键词再试，或者先在日程日历插件里创建事项。"
      >
        <template #actions>
          <VButton type="secondary" @click="handleCreate">添加事项</VButton>
        </template>
      </VEmpty>
    </div>

    <template #footer>
      <div class="schedule-card-picker-modal__footer">
        <VButton type="secondary" @click="handleCreate">添加事项</VButton>
        <VButton @click="handleClose">取消</VButton>
      </div>
    </template>
  </VModal>
</template>

<style scoped lang="scss">
.schedule-card-picker-modal {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.schedule-card-picker-modal__search {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;

  span {
    font-size: 12px;
    font-weight: 600;
    color: var(--halo-text-color-secondary, #6b7280);
  }

  input {
    width: 100%;
    border: 1px solid var(--halo-border-color, #d1d5db);
    border-radius: 8px;
    padding: 10px 12px;
    font: inherit;
    color: var(--halo-text-color, #111827);
    background: var(--halo-bg-color, #fff);
  }

  input:focus {
    outline: none;
  }
}

.schedule-card-picker-modal__count {
  font-size: 12px;
  color: var(--halo-text-color-secondary, #6b7280);
}

.entry-start {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.entry-start--interactive {
  cursor: pointer;
}

.entry-dot {
  width: 10px;
  height: 10px;
  flex: none;
  margin-top: 6px;
  border-radius: 999px;
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
  font-size: 14px;
  font-weight: 600;
  color: var(--halo-text-color, #111827);
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

.schedule-card-picker-modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
