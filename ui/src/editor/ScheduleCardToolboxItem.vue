<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  editor?: unknown
  icon?: Component
  title?: string
  description?: string
  action?: () => void
}>()
</script>

<template>
  <button
    type="button"
    class="schedule-card-toolbox-item"
    tabindex="-1"
    @click="action?.()"
  >
    <component :is="icon" class="schedule-card-toolbox-item__icon" />
    <div class="schedule-card-toolbox-item__content">
      <span class="schedule-card-toolbox-item__title">{{ title }}</span>
      <span v-if="description" class="schedule-card-toolbox-item__description">
        {{ description }}
      </span>
    </div>
  </button>
</template>

<style scoped lang="scss">
.schedule-card-toolbox-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.25rem 0.375rem;
  border: 0;
  border-radius: 0.375rem;
  background: transparent;
  cursor: pointer;
  text-align: left;

  &:hover {
    background: #f3f4f6;
  }
}

.schedule-card-toolbox-item__icon {
  width: 1.75rem;
  height: 1.75rem;
  flex: none;
  padding: 0.375rem;
  border-radius: 0.375rem;
  background: #f3f4f6;
}

.schedule-card-toolbox-item:hover .schedule-card-toolbox-item__icon {
  background: #ffffff;
}

.schedule-card-toolbox-item__content {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.schedule-card-toolbox-item__title {
  font-size: 0.875rem;
  color: #4b5563;
}

.schedule-card-toolbox-item__description {
  font-size: 0.75rem;
  color: #6b7280;
}

.schedule-card-toolbox-item:hover .schedule-card-toolbox-item__title {
  color: #111827;
  font-weight: 500;
}
</style>
