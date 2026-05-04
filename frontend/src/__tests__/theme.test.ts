import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore } from '@/stores/theme'

describe('useThemeStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    document.documentElement.classList.remove('dark', 'eye-care')
  })

  it('defaults to light theme', () => {
    const store = useThemeStore()
    expect(store.theme).toBe('light')
  })

  it('cycles light -> dark -> eye-care -> light', () => {
    const store = useThemeStore()
    expect(store.theme).toBe('light')
    store.toggle()
    expect(store.theme).toBe('dark')
    store.toggle()
    expect(store.theme).toBe('eye-care')
    store.toggle()
    expect(store.theme).toBe('light')
  })

  it('persists theme to localStorage', () => {
    const store = useThemeStore()
    store.toggle()
    expect(localStorage.getItem('ai-writer-theme')).toBe('dark')
  })

  it('initializes from localStorage', () => {
    localStorage.setItem('ai-writer-theme', 'eye-care')
    const store = useThemeStore()
    expect(store.theme).toBe('eye-care')
  })

  it('applies dark class to html element', () => {
    const store = useThemeStore()
    store.toggle() // light -> dark
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(document.documentElement.classList.contains('eye-care')).toBe(false)
  })

  it('applies eye-care class to html element', () => {
    const store = useThemeStore()
    store.toggle() // light -> dark
    store.toggle() // dark -> eye-care
    expect(document.documentElement.classList.contains('eye-care')).toBe(true)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })
})
