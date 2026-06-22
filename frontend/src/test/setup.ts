import { afterEach, vi } from 'vitest'

function createStorageStub(): Storage {
  const store = new Map<string, string>()

  return {
    get length() {
      return store.size
    },
    clear() {
      store.clear()
    },
    getItem(key: string) {
      return store.has(key) ? store.get(key)! : null
    },
    key(index: number) {
      return Array.from(store.keys())[index] ?? null
    },
    removeItem(key: string) {
      store.delete(key)
    },
    setItem(key: string, value: string) {
      store.set(key, String(value))
    }
  }
}

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

if (typeof globalThis.localStorage?.clear !== 'function') {
  vi.stubGlobal('localStorage', createStorageStub())
}

if (typeof globalThis.sessionStorage?.clear !== 'function') {
  vi.stubGlobal('sessionStorage', createStorageStub())
}

vi.stubGlobal('ResizeObserver', ResizeObserverStub)

afterEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})
