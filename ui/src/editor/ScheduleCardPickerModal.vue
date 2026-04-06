<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { IconSearch, VButton, VEmpty, VEntityField, VModal } from '@halo-dev/components'
import type { ScheduleCard } from '../types/schedule'

const props = defineProps<{
  visible: boolean
  items: ScheduleCard[]
}>()

const emit = defineEmits<{
  close: []
  select: [item: ScheduleCard]
}>()

const keyword = ref('')
const keywordInputRef = ref<HTMLInputElement | null>(null)
const viewportWidth = ref(typeof window === 'undefined' ? 1280 : window.innerWidth)

const modalWidth = computed(() => Math.min(720, Math.max(280, viewportWidth.value - 24)))

const buildCardSummary = (card: ScheduleCard) => `${card.startTime} - ${card.endTime}`

const buildDetailLines = (card: ScheduleCard) => {
  const items: string[] = [card.recurrenceDescription ? `${card.recurrenceDescription} · 首次 ${buildCardSummary(card)}` : buildCardSummary(card)]

  if (card.nextOccurrenceLabel) {
    items.push(`下一次出现：${card.nextOccurrenceLabel}`)
  }

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
    card.nextOccurrenceLabel,
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

const updateViewportWidth = () => {
  viewportWidth.value = window.innerWidth
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

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      keyword.value = ''
      focusKeywordInput()
    }
  },
)

onMounted(() => {
  updateViewportWidth()
  window.addEventListener('resize', updateViewportWidth)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewportWidth)
})
</script>

<template>
  <VModal
    :visible="visible"
    title="选择日程"
    :width="modalWidth"
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
        <div
          v-for="item in filteredItems"
          :key="item.name"
          class="schedule-card-picker-modal__item"
        >
          <button
            type="button"
            class="schedule-card-picker-modal__item-main"
            @click="handleSelect(item)"
          >
            <span class="entry-dot" :style="{ background: item.color || '#3b82f6' }"></span>
            <VEntityField :title="item.title">
              <template #description>
                <div class="entry-details">
                  <div
                    v-for="(detailLine, index) in buildDetailLines(item)"
                    :key="`${item.name}-${index}`"
                    class="entry-detail"
                  >
                    {{ detailLine }}
                  </div>
                </div>
              </template>
            </VEntityField>
          </button>

          <div class="schedule-card-picker-modal__item-actions">
            <VButton size="sm" type="secondary" @click="handleSelect(item)">选择</VButton>
          </div>
        </div>
      </div>

      <VEmpty
        v-else
        title="没有匹配的事项"
        message="可以换个关键词再试，或者先在日程日历插件里创建事项。"
      />
    </div>

    <template #footer>
      <div class="schedule-card-picker-modal__footer">
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
  gap: 12px;
}

.schedule-card-picker-modal__item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--halo-border-color, #e5e7eb);
  border-radius: 12px;
  background: var(--halo-bg-color, #fff);
}

.schedule-card-picker-modal__item-main {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.schedule-card-picker-modal__item-actions {
  display: flex;
  justify-content: flex-end;
}

.entry-dot {
  width: 10px;
  height: 10px;
  flex: none;
  margin-top: 7px;
  border-radius: 999px;
}

.schedule-card-picker-modal__item :deep(.entity-field-wrapper) {
  width: 100%;
  max-width: none;
}

.schedule-card-picker-modal__item :deep(.entity-field-title) {
  font-size: 14px;
  font-weight: 600;
}

.schedule-card-picker-modal__item :deep(.entity-field-description-body) {
  margin-top: 6px;
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
    grid-template-columns: minmax(0, 1fr);
  }

  .schedule-card-picker-modal__item-actions {
    justify-content: flex-start;
  }

  .schedule-card-picker-modal__footer {
    flex-direction: column-reverse;
  }

  .schedule-card-picker-modal__footer :deep(button) {
    width: 100%;
  }
}
</style>
