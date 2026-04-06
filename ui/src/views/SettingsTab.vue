<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { Toast, VAlert, VButton, VCard } from '@halo-dev/components'
import { onMounted, reactive, ref } from 'vue'

interface PublicPageSetting {
  title?: string
}

const settingsApi = '/apis/console.api.schedule.calendar.sunny.dev/v1alpha1/settings/public-page'

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')

const form = reactive<PublicPageSetting>({
  title: '',
})

const loadSettings = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    const { data } = await axiosInstance.get<PublicPageSetting>(settingsApi)
    form.title = data.title ?? ''
  } catch (error) {
    console.error(error)
    errorMessage.value = '设置加载失败。'
  } finally {
    loading.value = false
  }
}

const saveSettings = async () => {
  saving.value = true
  errorMessage.value = ''

  try {
    await axiosInstance.put(settingsApi, {
      title: form.title || undefined,
    } satisfies PublicPageSetting)
    Toast.success('设置已保存')
  } catch (error) {
    console.error(error)
    errorMessage.value = '设置保存失败。'
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <div class="settings-tab">
    <VAlert
      v-if="errorMessage"
      type="error"
      title="操作失败"
      :description="errorMessage"
      :closable="false"
    />

    <VCard class="settings-card">
      <div class="settings-card__body">
        <div class="settings-card__header">
          <h3>前台页面</h3>
          <p>用于控制公开路由 `/schedule-calendar` 的标题和页头文案。</p>
        </div>

        <label class="field">
          <span>页面标题</span>
          <input v-model="form.title" type="text" placeholder="日程日历" :disabled="loading || saving" />
        </label>

        <div class="settings-card__actions">
          <VButton type="primary" :loading="saving" :disabled="loading" @click="saveSettings">
            保存设置
          </VButton>
        </div>
      </div>
    </VCard>
  </div>
</template>

<style scoped>
.settings-tab {
  display: grid;
  gap: 16px;
}

.settings-card__body {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.settings-card__header {
  display: grid;
  gap: 8px;
}

.settings-card__header h3 {
  margin: 0;
  color: var(--halo-text-color, #111827);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
}

.settings-card__header p {
  margin: 0;
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 14px;
  line-height: 1.6;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: var(--halo-text-color-secondary, #6b7280);
  font-size: 12px;
  font-weight: 600;
}

.field input {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--halo-border-color, #d1d5db);
  border-radius: 8px;
  background: var(--halo-bg-color, #fff);
  color: var(--halo-text-color, #111827);
  font: inherit;
}

.settings-card__actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .settings-card__body {
    padding: 16px;
  }
}
</style>
