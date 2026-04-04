<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { VButton, VEmpty, VEntity, VEntityContainer, VModal } from '@halo-dev/components'
import type { ScheduleCard } from '../types/schedule'

const props = defineProps<{
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

const buildMetaLines = (card: ScheduleCard) =>
  [
    card.location ? `地点：${card.location}` : '',
    card.description ? `备注：${card.description}` : '',
    card.recurrenceDescription ?? '',
  ].filter(Boolean)

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
    :visible="true"
    title="插入日程卡片"
    :width="760"
    :body-class="['schedule-card-picker-modal__body']"
    @update:visible="handleVisibleUpdate"
  >
    <div class="schedule-card-picker-modal">
      <div class="schedule-card-picker-modal__intro">
        搜索并选择一个事项，使用 Halo 控制台原生样式插入到当前文章中。
      </div>

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
            <div class="schedule-card-picker-modal__item">
              <span
                class="schedule-card-picker-modal__dot"
                :style="{ background: item.color || '#0f766e' }"
              ></span>

              <div class="schedule-card-picker-modal__item-main">
                <div class="schedule-card-picker-modal__title">{{ item.title }}</div>
                <div class="schedule-card-picker-modal__summary">{{ buildCardSummary(item) }}</div>

                <div
                  v-if="buildMetaLines(item).length"
                  class="schedule-card-picker-modal__meta"
                >
                  <span
                    v-for="(line, index) in buildMetaLines(item)"
                    :key="`${item.name}-${index}`"
                    class="schedule-card-picker-modal__meta-item"
                  >
                    {{ line }}
                  </span>
                </div>
              </div>
            </div>
          </template>

          <template #end>
            <VButton type="secondary" @click="handleSelect(item)">插入</VButton>
          </template>
        </VEntity>
      </VEntityContainer>

      <VEmpty
        v-else
        title="没有匹配的事项"
        message="可以换个关键词再试，或者先在日程日历插件里创建事项。"
      >
        <template #actions>
          <VButton type="secondary" @click="handleCreate">去添加事项</VButton>
        </template>
      </VEmpty>
    </div>

    <template #footer>
      <div class="schedule-card-picker-modal__footer">
        <VButton type="secondary" @click="handleCreate">去添加事项</VButton>
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

.schedule-card-picker-modal__intro {
  font-size: 0.875rem;
  line-height: 1.6;
  color: #6b7280;
}

.schedule-card-picker-modal__search {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;

  span {
    font-size: 0.875rem;
    font-weight: 600;
    color: #111827;
  }

  input {
    width: 100%;
    padding: 0.75rem 0.875rem;
    border: 1px solid #d1d5db;
    border-radius: 0.75rem;
    background: #ffffff;
    color: #111827;
    outline: none;
  }

  input:focus {
    border-color: #4ccba0;
    box-shadow: 0 0 0 3px rgba(76, 203, 160, 0.16);
  }
}

.schedule-card-picker-modal__count {
  font-size: 0.8125rem;
  color: #6b7280;
}

.schedule-card-picker-modal__item {
  display: flex;
  align-items: flex-start;
  gap: 0.875rem;
}

.schedule-card-picker-modal__dot {
  width: 0.75rem;
  height: 0.75rem;
  flex: none;
  margin-top: 0.375rem;
  border-radius: 999px;
}

.schedule-card-picker-modal__item-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.schedule-card-picker-modal__title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: #111827;
}

.schedule-card-picker-modal__summary {
  font-size: 0.875rem;
  color: #374151;
}

.schedule-card-picker-modal__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem 0.5rem;
}

.schedule-card-picker-modal__meta-item {
  font-size: 0.8125rem;
  line-height: 1.5;
  color: #6b7280;
}

.schedule-card-picker-modal__footer {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
}
</style>
