/// <reference types="vite/client" />
/// <reference types="unplugin-icons/types/vue" />

import type { DefineComponent } from 'vue'

declare module 'vue' {
  export interface GlobalComponents {
    SearchInput: DefineComponent<{
      modelValue?: string
      placeholder?: string
    }>
  }
}
