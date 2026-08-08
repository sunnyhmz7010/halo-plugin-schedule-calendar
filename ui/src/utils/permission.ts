export type PermissionLevel = 'view' | 'manage'

export const MANAGE_PERMISSION_API = '/apis/console.api.schedule.calendar.sunny.dev/v1alpha1/permissions/manage'

interface PermissionHttpClient {
  get: (
    url: string,
    config?: {
      validateStatus?: (status: number) => boolean
    },
  ) => Promise<{ status: number }>
}

export const resolveConsolePermissionLevel = async (client: PermissionHttpClient): Promise<PermissionLevel> => {
  try {
    const response = await client.get(MANAGE_PERMISSION_API, {
      validateStatus: (status) => status >= 200 && status < 500,
    })

    if (response.status >= 200 && response.status < 300) {
      return 'manage'
    }
  } catch (error) {
    console.error(error)
  }

  return 'view'
}
