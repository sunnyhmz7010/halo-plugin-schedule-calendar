import { createApp, h } from 'vue'
import type { ScheduleCard } from '../types/schedule'
import ScheduleCardPickerModal from './ScheduleCardPickerModal.vue'

export const openScheduleCardPicker = (items: ScheduleCard[]) =>
  new Promise<ScheduleCard | null>((resolve) => {
    const container = document.createElement('div')
    document.body.appendChild(container)

    let settled = false

    const cleanup = () => {
      app.unmount()
      container.remove()
    }

    const settle = (item: ScheduleCard | null) => {
      if (settled) {
        return
      }

      settled = true
      cleanup()
      resolve(item)
    }

    const app = createApp({
      render() {
        return h(ScheduleCardPickerModal, {
          items,
          onClose: () => settle(null),
          onSelect: (item: ScheduleCard) => settle(item),
        })
      },
    })

    app.mount(container)
  })
