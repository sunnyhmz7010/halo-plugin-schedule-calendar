<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { Dialog, Toast, VAlert, VButton, VCard, VEntity, VEntityContainer, VEntityField } from '@halo-dev/components'
import { computed, onMounted, ref } from 'vue'
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

interface ScheduleBackupImportResult {
  totalEntries: number
  createdEntries: number
  deletedEntries: number
}

const backupExportApi = '/apis/console.api.schedule.calendar.sunny.dev/v1alpha1/backupexports'
const pluginConfigApi = '/apis/api.console.halo.run/v1alpha1/plugins/schedule-calendar/json-config'

const exporting = ref(false)
const importing = ref(false)
const importSummary = ref('')
const importInputRef = ref<HTMLInputElement | null>(null)
const permissionLevel = ref<'unknown' | 'view' | 'manage'>('unknown')

const canManageEntries = computed(() => permissionLevel.value === 'manage')
const showReadonlyNotice = computed(() => permissionLevel.value === 'view')

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

const toScheduleEntry = (name: string, spec: ScheduleEntrySpec): ScheduleEntry => ({
  apiVersion: 'schedule.calendar.sunny.dev/v1alpha1',
  kind: 'ScheduleEntry',
  metadata: {
    name,
  },
  spec: {
    title: spec.title,
    description: spec.description || undefined,
    location: spec.location || undefined,
    startTime: spec.startTime,
    endTime: spec.endTime,
    color: spec.color || '#3b82f6',
    recurrence:
      spec.recurrence?.frequency && spec.recurrence.frequency !== 'NONE'
        ? {
            frequency: spec.recurrence.frequency,
            interval: spec.recurrence.interval ?? 1,
            until: spec.recurrence.until || undefined,
          }
        : undefined,
  },
})

const restorePluginSettings = async (settings?: Record<string, unknown>) => {
  await axiosInstance.put(pluginConfigApi, settings ?? {})
}

const restoreEntries = async (
  items: ScheduleBackupPayload['entries'],
): Promise<ScheduleBackupImportResult> => {
  const existingEntries = await fetchAllScheduleEntries()
  const existingByName = new Map(existingEntries.map((entry) => [entry.metadata.name, entry]))
  const importedEntries = items.map((item) => toScheduleEntry(item.name, item.spec))
  const importedNames = new Set(importedEntries.map((entry) => entry.metadata.name))
  let createdEntries = 0

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
    createdEntries += 1
  }

  let deletedEntries = 0

  for (const entry of existingEntries) {
    if (importedNames.has(entry.metadata.name)) {
      continue
    }

    await axiosInstance.delete(`${ENTRY_API}/${encodeURIComponent(entry.metadata.name)}`)
    deletedEntries += 1
  }

  return {
    totalEntries: importedEntries.length,
    createdEntries,
    deletedEntries,
  }
}

const exportBackup = async () => {
  if (!canManageEntries.value) {
    Toast.error('当前账号没有数据备份管理权限')
    return
  }

  exporting.value = true

  try {
    const { data } = await axiosInstance.get<ScheduleBackupPayload>(backupExportApi)
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
  if (!canManageEntries.value) {
    Toast.error('当前账号没有数据备份管理权限')
    return
  }

  if (importing.value) {
    return
  }

  importInputRef.value?.click()
}

const importBackupFile = async (file: File, input: HTMLInputElement) => {
  importing.value = true

  try {
    const text = await file.text()
    const payload = JSON.parse(text) as ScheduleBackupPayload
    const data = await restoreEntries(payload.entries ?? [])
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

const restoreBackup = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]

  if (!file) {
    return
  }

  Dialog.warning({
    title: '确认恢复备份',
    description: `即将导入 ${file.name}。恢复会覆盖当前插件设置，并按备份内容同步事项数据。`,
    confirmType: 'danger',
    confirmText: '确认恢复',
    cancelText: '取消',
    showCancel: true,
    onConfirm: () => {
      void importBackupFile(file, input)
    },
    onCancel: () => {
      input.value = ''
    },
  })
}

const loadPermissionLevel = async () => {
  permissionLevel.value = 'unknown'

  try {
    const response = await axiosInstance.delete(
      `${ENTRY_API}/${encodeURIComponent('__permission_probe__')}`,
      {
        validateStatus: (status) => status >= 200 && status < 500,
      },
    )

    if (response.status !== 401 && response.status !== 403) {
      permissionLevel.value = 'manage'
      return
    }
  } catch (error) {
    console.error(error)
  }

  permissionLevel.value = 'view'
}

onMounted(() => {
  void loadPermissionLevel()
})
</script>

<template>
  <div class="backup-tab">
    <VAlert
      v-if="showReadonlyNotice"
      type="info"
      title="当前为只读权限"
      description="你可以查看导出导入入口，但导出备份和导入恢复需要“日程日历管理”权限。"
      :closable="false"
    />

    <VCard>
      <VEntityContainer>
        <VEntity>
          <template #start>
            <VEntityField title="导出备份">
              <template #description>
                导出当前插件设置和全部事项，生成一份可用于恢复的 JSON 文件。
              </template>
            </VEntityField>
          </template>
          <template #end>
            <VButton type="primary" :loading="exporting" :disabled="!canManageEntries" @click="exportBackup">
              下载备份文件
            </VButton>
          </template>
        </VEntity>

        <VEntity>
          <template #start>
            <VEntityField title="导入恢复">
              <template #description>
                导入会覆盖当前插件设置，并按照备份内容同步事项数据。
              </template>
            </VEntityField>
          </template>
          <template #end>
            <VButton :loading="importing" :disabled="!canManageEntries" @click="openImportPicker">
              选择备份文件
            </VButton>
          </template>
        </VEntity>
      </VEntityContainer>

      <input
        ref="importInputRef"
        class="backup-input"
        type="file"
        accept="application/json,.json"
        @change="restoreBackup"
      />
    </VCard>

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

.backup-input {
  display: none;
}
</style>
