import { create } from 'zustand'
import { DEFAULT_TEMPLATE } from './templates'

interface EditorState {
  source: string
  stdin: string
  dirty: boolean
  setSource: (source: string) => void
  setStdin: (stdin: string) => void
  loadTemplate: (code: string) => void
  reset: () => void
}

export const useEditorStore = create<EditorState>((set) => ({
  source: DEFAULT_TEMPLATE.code,
  stdin: '',
  dirty: false,
  setSource: (source) => set({ source, dirty: true }),
  setStdin: (stdin) => set({ stdin }),
  loadTemplate: (code) => set({ source: code, dirty: false }),
  reset: () => set({ source: DEFAULT_TEMPLATE.code, stdin: '', dirty: false }),
}))
