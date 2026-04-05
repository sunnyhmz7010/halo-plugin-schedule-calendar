/// <reference types="vite/client" />
/// <reference types="unplugin-icons/types/vue" />

import type { DefineComponent } from 'vue'

declare global {
  const __SCHEDULE_CALENDAR_VERSION__: string

  interface Window {
    __SCHEDULE_CALENDAR_VERSION__?: string
  }
}

declare module 'vue' {
  export interface GlobalComponents {
    SearchInput: DefineComponent<{
      modelValue?: string
      placeholder?: string
    }>
  }
}

export {}
