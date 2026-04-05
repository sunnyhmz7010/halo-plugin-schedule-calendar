import { readFileSync } from 'fs'
import { fileURLToPath, URL } from 'url'

import { viteConfig } from '@halo-dev/ui-plugin-bundler-kit'
import Icons from 'unplugin-icons/vite'
import { configDefaults } from 'vitest/config'

const gradleProperties = readFileSync(new URL('../gradle.properties', import.meta.url), 'utf8')
const pluginVersion =
  gradleProperties
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line.startsWith('version='))?.slice('version='.length) ?? '0.0.0-dev'

// For more info,
// please see https://github.com/halo-dev/halo/tree/main/ui/packages/ui-plugin-bundler-kit
export default viteConfig({
  vite: {
    define: {
      __SCHEDULE_CALENDAR_VERSION__: JSON.stringify(pluginVersion),
    },
    plugins: [Icons({ compiler: 'vue3' })],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },

    // If you don't use Vitest, you can remove the following configuration
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
    },
  },
})
