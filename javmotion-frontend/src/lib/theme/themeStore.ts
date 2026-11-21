import { create } from 'zustand'
import { storage } from '@/lib/utils/storage'

export type Theme = 'dark' | 'light'

const KEY = 'jm-theme'

function initialTheme(): Theme {
  const saved = storage.get(KEY)
  if (saved === 'dark' || saved === 'light') return saved
  // Brand default is dark; only switch if the OS explicitly asks for light
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

interface ThemeState {
  theme: Theme
  setTheme: (theme: Theme) => void
  toggle: () => void
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  theme: initialTheme(),
  setTheme: (theme) => {
    storage.set(KEY, theme)
    set({ theme })
  },
  toggle: () => get().setTheme(get().theme === 'dark' ? 'light' : 'dark'),
}))
