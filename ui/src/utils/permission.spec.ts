import { describe, expect, it, vi } from 'vitest'
import { MANAGE_PERMISSION_API, resolveConsolePermissionLevel } from './permission'

describe('permission utils', () => {
  it('detects manage permission through the dedicated console permission endpoint', async () => {
    const get = vi.fn().mockResolvedValue({ status: 204 })

    await expect(resolveConsolePermissionLevel({ get })).resolves.toBe('manage')

    expect(get).toHaveBeenCalledWith(MANAGE_PERMISSION_API, {
      validateStatus: expect.any(Function),
    })
    expect(get.mock.calls[0]?.[1]?.validateStatus?.(403)).toBe(true)
    expect(get.mock.calls[0]?.[1]?.validateStatus?.(500)).toBe(false)
  })

  it('downgrades to view permission when manage permission is denied', async () => {
    const get = vi.fn().mockResolvedValue({ status: 403 })

    await expect(resolveConsolePermissionLevel({ get })).resolves.toBe('view')
  })

  it('downgrades to view permission when permission probing fails', async () => {
    const get = vi.fn().mockRejectedValue(new Error('network unavailable'))
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    await expect(resolveConsolePermissionLevel({ get })).resolves.toBe('view')

    consoleError.mockRestore()
  })
})
