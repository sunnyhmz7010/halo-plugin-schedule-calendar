/// <reference types="vite/client" />

import type { DefineComponent } from 'vue'

declare module 'vue' {
  export interface GlobalComponents {
    SearchInput: DefineComponent<{
      modelValue?: string
      placeholder?: string
    }>
  }
}
