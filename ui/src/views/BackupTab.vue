<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { Toast, VAlert, VButton, VCard } from '@halo-dev/components'
import { computed, ref } from 'vue'
import { ENTRY_API, fetchAllScheduleEntries } from '../editor/schedule-card-data'
import type { ScheduleEntry, ScheduleEntrySpec } from '../types/schedule'

interface ScheduleBackupPayload {
  apiVersion?: string
  kind?: string
  schemaVersion: number
  exportedAt?: string
  settings?: Record<string, unknown>
  entries: Array<{
    name: string
    spec: ScheduleEntrySpec
  }>
}

const backupApi = '/apis/api.console.schedule.calendar.sunny.dev/v1alpha1/backups'
const pluginConfigApi = '/apis/api.console.halo.run/v1alpha1/plugins/schedule-calendar/json-config'

const exporting = ref(false)
const importing = ref(false)
const importSummary = ref('')
const importInputRef = ref<HTMLInputElement | null>(null)

const getErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error !== null) {
    const response = (error as { response?: { data?: { message?: string; detail?: string } } }).response
    const detail = response?.data?.detail
    const message = response?.data?.message

    if (detail) {
      return detail
    }

    if (message) {
      return message
    }
  }

  return fallback
}

const exportDescription = computed(() =>
  '导出当前插件设置和全部事项，生成一份可用于恢复的 JSON 备份文件。'
)

const importDescription = computed(() =>
  '导入会覆盖当前插件设置，并按照备份内容同步事项数据，请先确认备份文件来源正确。'
)

const buildFileName = (exportedAt?: string) => {
  const source = exportedAt ? new Date(exportedAt) : new Date()
  const target = Number.isNaN(source.getTime()) ? new Date() : source
  const year = String(target.getFullYear())
  const month = String(target.getMonth() + 1).padStart(2, '0')
  const day = String(target.getDate()).padStart(2, '0')
  const hour = String(target.getHours()).padStart(2, '0')
  const minute = String(target.getMinutes()).padStart(2, '0')

  return `schedule-calendar-backup-${year}${month}${day}-${hour}${minute}.json`
}

const downloadJson = (payload: ScheduleBackupPayload) => {
  const blob = new Blob([JSON.stringify(payload, null, 2)], {
    type: 'application/json;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')

  link.href = url
  link.download = buildFileName(payload.exportedAt)
  link.click()

  URL.revokeObjectURL(url)
}

const exportBackup = async () => {
  exporting.value = true

  try {
    const { data } = await axiosInstance.get<ScheduleBackupPayload>(`${backupApi}/export`)
    downloadJson(data)
    Toast.success('备份已导出')
  } catch (error) {
    console.error(error)
    Toast.error(getErrorMessage(error, '备份导出失败'))
  } finally {
    exporting.value = false
  }
}

const openImportPicker = () => {
  importInputRef.value?.click()
}

const toScheduleEntry = (item: ScheduleBackupPayload['entries'][number]): ScheduleEntry => ({
  apiVersion: 'schedule.calendar.sunny.dev/v1alpha1',
  kind: 'ScheduleEntry',
  metadata: {
    name: item.name,
  },
  spec: item.spec,
})

const restorePluginSettings = async (settings?: Record<string, unknown>) => {
  await axiosInstance.put(pluginConfigApi, settings ?? {})
}

const restoreEntries = async (items: ScheduleBackupPayload['entries']) => {
  const existingEntries = await fetchAllScheduleEntries()
  const existingByName = new Map(existingEntries.map((entry) => [entry.metadata.name, entry]))
  const importedEntries = items.map((item) => toScheduleEntry(item))
  const importedNames = new Set(importedEntries.map((entry) => entry.metadata.name))

  for (const entry of importedEntries) {
    const existing = existingByName.get(entry.metadata.name)

    if (existing) {
      await axiosInstance.put(`${ENTRY_API}/${encodeURIComponent(entry.metadata.name)}`, {
        apiVersion: existing.apiVersion ?? entry.apiVersion,
        kind: existing.kind ?? entry.kind,
        metadata: {
          ...existing.metadata,
          name: entry.metadata.name,
        },
        spec: entry.spec,
      })
      continue
    }

    await axiosInstance.post(ENTRY_API, entry)
  }

  for (const entry of existingEntries) {
    if (importedNames.has(entry.metadata.name)) {
      continue
    }

    await axiosInstance.delete(`${ENTRY_API}/${encodeURIComponent(entry.metadata.name)}`)
  }

  return {
    totalEntries: importedEntries.length,
    createdEntries: importedEntries.filter((entry) => !existingByName.has(entry.metadata.name)).length,
    deletedEntries: existingEntries.filter((entry) => !importedNames.has(entry.metadata.name)).length,
  }
}

const restoreBackup = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]

  if (!file) {
    return
  }

  importing.value = true

  try {
    const text = await file.text()
    const payload = JSON.parse(text) as ScheduleBackupPayload
    const data = await restoreEntries(payload.entries)
    await restorePluginSettings(payload.settings)

    importSummary.value = `已同步 ${data.totalEntries} 条事项，新建 ${data.createdEntries} 条，移除 ${data.deletedEntries} 条。`
    Toast.success('备份已恢复')
  } catch (error) {
    console.error(error)
    Toast.error(getErrorMessage(error, '备份恢复失败'))
  } finally {
    importing.value = false
    input.value = ''
  }
}
</script>

<template>
  <div class="backup-tab">
    <VAlert
      type="warning"
      title="备份说明"
      description="导出会包含插件设置与全部事项；导入会按备份内容覆盖当前设置，并同步事项数据。"
      :closable="false"
    />

    <div class="backup-grid">
      <VCard class="backup-card">
        <div class="backup-card__body">
          <div class="backup-card__header">
            <h3>导出备份</h3>
            <p>{{ exportDescription }}</p>
          </div>

          <VButton type="primary" :loading="exporting" @click="exportBackup">下载备份文件</VButton>
        </div>
      </VCard>

      <VCard class="backup-card">
        <div class="backup-card__body">
          <div class="backup-card__header">
            <h3>导入恢复</h3>
            <p>{{ importDescription }}</p>
          </div>

          <VButton :loading="importing" @click="openImportPicker">选择备份文件</VButton>
          <input
            ref="importInputRef"
            class="backup-input"
            type="file"
            accept="application/json,.json"
            @change="restoreBackup"
          />
        </div>
      </VCard>
    </div>

    <VAlert
      v-if="importSummary"
      type="success"
      title="最近一次恢复结果"
      :description="importSummary"
      :closable="false"
    />
  </div>
</template>

<style scoped>
.backup-tab {
  display: grid;
  gap: 16px;
}

.backup-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.backup-card {
  height: 100%;
}

.backup-card__body {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.backup-card__header {
  display: grid;
  gap: 8px;
}

.backup-card__header h3 {
  margin: 0;
  color: var(--halo-text-color, #111827);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
}

.backup-card__header p {
  margin: 0;
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 14px;
  line-height: 1.6;
}

.backup-input {
  display: none;
}

@media (max-width: 960px) {
  .backup-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .backup-card__body {
    padding: 16px;
  }
}
</style>
