import { describe, expect, it } from 'vitest'
import router from './index'

describe('router permission metadata', () => {
  it('keeps compliance-sensitive routes protected by roles and permissions', () => {
    expectRouteAccess('/str-reports', ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], ['report:str'])
    expectRouteAccess('/reporting', ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], ['report:view'])
    expectRouteAccess('/special-prevention', ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], ['special:view'])
    expectRouteAccess('/rectifications', ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR', 'ROLE_VIEWER'], ['rectification:view'])
    expectRouteAccess('/system', ['ROLE_ADMIN'], ['system:view'])
  })

  it('keeps personal notification center authenticated but not role-restricted', () => {
    const route = router.getRoutes().find(item => item.path === '/notifications')

    expect(route).toBeTruthy()
    expect(route?.meta.roles).toBeUndefined()
    expect(route?.meta.permissions).toBeUndefined()
  })
})

function expectRouteAccess(path: string, roles: string[], permissions: string[]) {
  const route = router.getRoutes().find(item => item.path === path)

  expect(route, `${path} route should exist`).toBeTruthy()
  expect(route?.meta.roles).toEqual(expect.arrayContaining(roles))
  expect(route?.meta.permissions).toEqual(expect.arrayContaining(permissions))
}
