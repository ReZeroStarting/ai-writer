import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'ai-writer-theme'
type Theme = 'light' | 'dark' | 'eye-care'

const themes: Theme[] = ['light', 'dark', 'eye-care']

function loadTheme(): Theme {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved && themes.includes(saved as Theme)) return saved as Theme
  return 'light'
}

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<Theme>(loadTheme())

  function apply() {
    document.documentElement.classList.remove('dark', 'eye-care')
    if (theme.value === 'dark') document.documentElement.classList.add('dark')
    if (theme.value === 'eye-care') document.documentElement.classList.add('eye-care')
  }

  function toggle() {
    const idx = themes.indexOf(theme.value)
    theme.value = themes[(idx + 1) % themes.length]
    localStorage.setItem(STORAGE_KEY, theme.value)
    apply()
  }

  watch(theme, (t) => {
    localStorage.setItem(STORAGE_KEY, t)
    apply()
  })

  return { theme, toggle, apply }
})
