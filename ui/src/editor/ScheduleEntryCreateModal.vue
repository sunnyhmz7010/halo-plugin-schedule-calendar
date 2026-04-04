<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { Toast, VAlert, VButton, VModal } from '@halo-dev/components'
import { reactive, ref } from 'vue'
import type { ScheduleCard, ScheduleEntry, ScheduleEntryRecurrenceFrequency, ScheduleEntrySpec } from '../types/schedule'
import { ENTRY_API, toScheduleCard } from './schedule-card-data'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
  created: [card: ScheduleCard]
}>()

const saving = ref(false)
const errorMessage = ref('')
const colorInputRef = ref<HTMLInputElement | null>(null)

const form = reactive({
  title: '',
  description: '',
  location: '',
  startTimeLocal: '',
  endTimeLocal: '',
  color: '#3b82f6',
  recurrenceFrequency: 'NONE' as ScheduleEntryRecurrenceFrequency,
  recurrenceInterval: 1,
  recurrenceUntil: '',
})

const spansMultipleDates = (start: Date, end: Date) => {
  const startKey = start.toISOString().slice(0, 10)
  const endKey = end.toISOString().slice(0, 10)
  return startKey !== endKey
}

const buildEntrySpec = (startDate: Date, endDate: Date): ScheduleEntrySpec => ({
  title: form.title,
  description: form.description || undefined,
  location: form.location || undefined,
  startTime: startDate.toISOString(),
  endTime: endDate.toISOString(),
  color: form.color,
  recurrence:
    form.recurrenceFrequency === 'NONE'
      ? undefined
      : {
          frequency: form.recurrenceFrequency,
          interval: form.recurrenceInterval,
          until: form.recurrenceUntil || undefined,
        },
})

const validateForm = () => {
  errorMessage.value = ''

  if (!form.title || !form.startTimeLocal || !form.endTimeLocal) {
    errorMessage.value = '标题、开始时间、结束时间是必填项。'
    return null
  }

  const startDate = new Date(form.startTimeLocal)
  const endDate = new Date(form.endTimeLocal)

  if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) {
    errorMessage.value = '请输入有效的开始时间和结束时间。'
    return null
  }

  if (endDate <= startDate) {
    errorMessage.value = '结束时间必须晚于开始时间。'
    return null
  }

  if (form.recurrenceFrequency !== 'NONE') {
    if (spansMultipleDates(startDate, endDate)) {
      errorMessage.value = '跨天事项暂不支持循环，请拆分为单次事项或取消循环。'
      return null
    }

    if (!Number.isInteger(form.recurrenceInterval) || form.recurrenceInterval < 1) {
      errorMessage.value = '循环间隔必须是大于 0 的整数。'
      return null
    }

    const startDateKey = form.startTimeLocal.slice(0, 10)
    if (form.recurrenceUntil && form.recurrenceUntil < startDateKey) {
      errorMessage.value = '循环截止日期不能早于开始日期。'
      return null
    }
  }

  return { startDate, endDate }
}

const resetForm = () => {
  form.title = ''
  form.description = ''
  form.location = ''
  form.startTimeLocal = ''
  form.endTimeLocal = ''
  form.color = '#3b82f6'
  form.recurrenceFrequency = 'NONE'
  form.recurrenceInterval = 1
  form.recurrenceUntil = ''
  errorMessage.value = ''
}

const openColorPicker = () => {
  colorInputRef.value?.click()
}

const handleClose = () => {
  resetForm()
  emit('close')
}

const handleVisibleUpdate = (visible: boolean) => {
  if (!visible) {
    handleClose()
  }
}

const submit = async () => {
  const validated = validateForm()
  if (!validated) {
    return
  }

  const name = `schedule-entry-${Date.now()}`
  const { startDate, endDate } = validated

  saving.value = true

  try {
    await axiosInstance.post(ENTRY_API, {
      apiVersion: 'schedule.calendar.sunny.dev/v1alpha1',
      kind: 'ScheduleEntry',
      metadata: {
        name,
      },
      spec: buildEntrySpec(startDate, endDate),
    })

    const { data } = await axiosInstance.get<ScheduleEntry>(`${ENTRY_API}/${encodeURIComponent(name)}`)
    Toast.success('事项已添加')
    resetForm()
    emit('created', toScheduleCard(data))
  } catch (error) {
    console.error(error)
    errorMessage.value = '事项创建失败。'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <VModal
    :visible="visible"
    title="添加事项"
    :width="720"
    :layer-closable="false"
    :body-class="['schedule-entry-create-modal__body']"
    @update:visible="handleVisibleUpdate"
  >
    <div class="dialog-form">
      <VAlert
        v-if="errorMessage"
        type="error"
        title="操作失败"
        :description="errorMessage"
        :closable="false"
      />

      <label class="field">
        <span>事项标题</span>
        <input v-model="form.title" type="text" placeholder="例如：产品评审" />
      </label>

      <label class="field">
        <span>地点 / 链接</span>
        <input v-model="form.location" type="text" placeholder="会议室 A / 腾讯会议" />
      </label>

      <div class="field-row">
        <label class="field">
          <span>开始时间</span>
          <input v-model="form.startTimeLocal" type="datetime-local" />
        </label>

        <label class="field">
          <span>结束时间</span>
          <input v-model="form.endTimeLocal" type="datetime-local" />
        </label>
      </div>

      <div class="field-row field-row--recurrence">
        <label class="field">
          <span>循环规则</span>
          <select v-model="form.recurrenceFrequency">
            <option value="NONE">不重复</option>
            <option value="DAILY">每天</option>
            <option value="WEEKLY">每周</option>
            <option value="MONTHLY">每月</option>
            <option value="YEARLY">每年</option>
          </select>
        </label>

        <label v-if="form.recurrenceFrequency !== 'NONE'" class="field">
          <span>循环间隔</span>
          <input v-model.number="form.recurrenceInterval" type="number" min="1" step="1" />
        </label>

        <label v-if="form.recurrenceFrequency !== 'NONE'" class="field">
          <span>截止日期</span>
          <input v-model="form.recurrenceUntil" type="date" />
        </label>
      </div>

      <label class="field">
        <span>事项说明</span>
        <textarea
          v-model="form.description"
          rows="4"
          placeholder="可选：补充备注、参与人、准备事项"
        ></textarea>
      </label>

      <label class="field field--compact">
        <span>颜色</span>
        <button type="button" class="color-picker" @click="openColorPicker">
          <span class="color-picker__preview" :style="{ background: form.color }"></span>
          <span class="color-picker__value">{{ form.color }}</span>
        </button>
        <input ref="colorInputRef" v-model="form.color" type="color" class="field__color" />
      </label>
    </div>

    <template #footer>
      <div class="modal-footer">
        <VButton @click="handleClose">取消</VButton>
        <VButton type="primary" :loading="saving" @click="submit">添加并选中</VButton>
      </div>
    </template>
  </VModal>
</template>

<style scoped lang="scss">
.dialog-form {
  display: grid;
  gap: 16px;
  padding: 4px 0;
}

.field-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.field-row--recurrence {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field span {
  font-size: 12px;
  font-weight: 600;
  color: var(--halo-text-color-secondary, #6b7280);
}

.field input,
.field select,
.field textarea {
  width: 100%;
  border: 1px solid var(--halo-border-color, #d1d5db);
  border-radius: 8px;
  padding: 10px 12px;
  font: inherit;
  color: var(--halo-text-color, #111827);
  background: var(--halo-bg-color, #fff);
}

.field textarea {
  resize: vertical;
}

.field--compact {
  width: fit-content;
}

.color-picker {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: auto;
  min-width: 136px;
  height: 40px;
  padding: 4px 10px 4px 4px;
  border: 1px solid var(--halo-border-color, #d1d5db);
  border-radius: 10px;
  background: var(--halo-bg-color, #fff);
  cursor: pointer;
  font: inherit;
}

.color-picker__preview {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  flex: none;
}

.color-picker__value {
  color: var(--halo-text-color, #111827);
  font-size: 12px;
  letter-spacing: 0.02em;
}

.field__color {
  position: absolute;
  width: 1px !important;
  height: 1px !important;
  padding: 0;
  border: 0;
  opacity: 0;
  pointer-events: none;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 768px) {
  .field-row,
  .field-row--recurrence {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
