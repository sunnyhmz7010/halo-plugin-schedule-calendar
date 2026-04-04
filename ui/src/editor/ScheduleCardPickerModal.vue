<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { IconSearch, VButton, VEmpty, VModal } from '@halo-dev/components'
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

const buildCardSummary = (card: ScheduleCard) => `${card.startTime} - ${card.endTime}`

const buildDetailLines = (card: ScheduleCard) => {
  const items: string[] = [card.recurrenceDescription ? `${card.recurrenceDescription} · 首次 ${buildCardSummary(card)}` : buildCardSummary(card)]

  if (card.location) {
    items.push(`地点：${card.location}`)
  }

  if (card.description) {
    items.push(`备注：${card.description}`)
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
  void nextTick(() => {
    keywordInputRef.value?.focus()
  })
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

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      keyword.value = ''
      focusKeywordInput()
    }
  },
)
</script>

<template>
  <VModal
    :visible="visible"
    title="选择日程"
    :width="720"
    :body-class="['schedule-card-picker-modal__body']"
    @update:visible="handleVisibleUpdate"
  >
    <div class="schedule-card-picker-modal">
      <div class="schedule-card-picker-modal__search">
        <div class="schedule-card-picker-modal__search-field">
          <span class="schedule-card-picker-modal__search-icon">
            <IconSearch />
          </span>
          <input
            ref="keywordInputRef"
            v-model="keyword"
            type="search"
            class="schedule-card-picker-modal__search-input"
            placeholder="搜索标题、名称、地点、备注或循环规则"
          />
        </div>
      </div>

      <div class="schedule-card-picker-modal__count">共 {{ filteredItems.length }} 个可选事项</div>

      <div v-if="filteredItems.length" class="schedule-card-picker-modal__list">
        <button
          v-for="item in filteredItems"
          :key="item.name"
          type="button"
          class="schedule-card-picker-modal__item"
          @click="handleSelect(item)"
        >
          <span class="entry-dot" :style="{ background: item.color || '#3b82f6' }"></span>

          <div class="entry-main">
            <div class="schedule-card-picker-modal__item-header">
              <div class="entry-title">{{ item.title }}</div>
              <span class="schedule-card-picker-modal__item-action">选择</span>
            </div>

            <div class="entry-details">
              <div
                v-for="(detailLine, index) in buildDetailLines(item)"
                :key="`${item.name}-${index}`"
                class="entry-detail"
              >
                {{ detailLine }}
              </div>
            </div>
          </div>
        </button>
      </div>

      <VEmpty
        v-else
        title="没有匹配的事项"
        message="可以换个关键词再试，或者先在日程日历插件里创建事项。"
      >
        <template #actions>
          <VButton type="secondary" @click="handleCreate">新增事项</VButton>
        </template>
      </VEmpty>
    </div>

    <template #footer>
      <div class="schedule-card-picker-modal__footer">
        <VButton type="secondary" @click="handleCreate">新增事项</VButton>
        <VButton @click="handleClose">取消</VButton>
      </div>
    </template>
  </VModal>
</template>

<style scoped lang="scss">
.schedule-card-picker-modal {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.schedule-card-picker-modal__search {
  width: 100%;
}

.schedule-card-picker-modal__search-field {
  position: relative;
  width: 100%;
}

.schedule-card-picker-modal__search-icon {
  position: absolute;
  top: 50%;
  left: 12px;
  display: inline-flex;
  align-items: center;
  color: var(--halo-text-color-secondary, #6b7280);
  transform: translateY(-50%);
  pointer-events: none;
}

.schedule-card-picker-modal__search-input {
  width: 100%;
  height: 40px;
  padding: 0 14px 0 38px;
  border: 1px solid var(--halo-border-color, #d1d5db);
  border-radius: 10px;
  background: var(--halo-bg-color, #fff);
  color: var(--halo-text-color, #111827);
  font-size: 14px;
  line-height: 1.5;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.schedule-card-picker-modal__search-input::placeholder {
  color: var(--halo-text-color-secondary, #9ca3af);
}

.schedule-card-picker-modal__search-input:focus {
  border-color: color-mix(in srgb, var(--halo-primary-color, #4f46e5) 42%, var(--halo-border-color, #d1d5db));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--halo-primary-color, #4f46e5) 14%, transparent);
}

.schedule-card-picker-modal__count {
  font-size: 12px;
  color: var(--halo-text-color-secondary, #6b7280);
}

.schedule-card-picker-modal__list {
  display: grid;
  gap: 10px;
}

.schedule-card-picker-modal__item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--halo-border-color, #e5e7eb);
  border-radius: 12px;
  background: var(--halo-bg-color, #fff);
  cursor: pointer;
  text-align: left;
  outline: none;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.schedule-card-picker-modal__item:hover {
  border-color: color-mix(in srgb, var(--halo-primary-color, #4f46e5) 28%, var(--halo-border-color, #e5e7eb));
  background: color-mix(in srgb, var(--halo-primary-color, #4f46e5) 3%, var(--halo-bg-color, #fff));
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
  transform: translateY(-1px);
}

.schedule-card-picker-modal__item:focus-visible {
  border-color: color-mix(in srgb, var(--halo-primary-color, #4f46e5) 38%, var(--halo-border-color, #e5e7eb));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--halo-primary-color, #4f46e5) 14%, transparent);
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
  gap: 6px;
}

.schedule-card-picker-modal__item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.schedule-card-picker-modal__item-action {
  flex: none;
  color: var(--halo-primary-color, #4f46e5);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
}

.entry-title {
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--halo-text-color, #111827);
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

.schedule-card-picker-modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 640px) {
  .schedule-card-picker-modal__item {
    padding: 12px 14px;
  }

  .schedule-card-picker-modal__item-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
