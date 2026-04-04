<script setup lang="ts">
import { computed, nextTick, ref, type ComponentPublicInstance, watch } from 'vue'
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
const keywordInputRef = ref<ComponentPublicInstance | null>(null)

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
    const host = keywordInputRef.value?.$el as HTMLElement | undefined
    host?.querySelector('input')?.focus()
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
        <SearchInput
          ref="keywordInputRef"
          v-model="keyword"
          placeholder="搜索标题、名称、地点、备注或循环规则"
        />
      </div>

      <div class="schedule-card-picker-modal__count">共 {{ filteredItems.length }} 个可选事项</div>

      <VEntityContainer v-if="filteredItems.length">
        <VEntity
          v-for="item in filteredItems"
          :key="item.name"
          class="schedule-card-picker-modal__entity"
        >
          <template #start>
            <div
              class="schedule-card-picker-modal__entity-content"
              role="button"
              tabindex="0"
              @mousedown.stop.prevent
              @click.stop.prevent="handleSelect(item)"
              @keydown.enter.stop.prevent="handleSelect(item)"
              @keydown.space.stop.prevent="handleSelect(item)"
            >
              <span class="entry-dot" :style="{ background: item.color || '#3b82f6' }"></span>

              <div class="entry-main">
                <div class="entry-title">{{ item.title }}</div>
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
            </div>
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
  gap: 12px;
}

.schedule-card-picker-modal__search {
  width: 100%;
}

.schedule-card-picker-modal__search :deep(.search-input) {
  width: 100%;
}

.schedule-card-picker-modal__count {
  font-size: 12px;
  color: var(--halo-text-color-secondary, #6b7280);
}

.schedule-card-picker-modal__entity {
  transition: background-color 0.2s ease;
}

.schedule-card-picker-modal__entity-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  min-width: 0;
  cursor: pointer;
  padding: 2px 0;
  outline: none;
}

.schedule-card-picker-modal__entity-content:focus-visible {
  border-radius: 8px;
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--halo-primary-color, #4f46e5) 18%, transparent);
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
</style>
