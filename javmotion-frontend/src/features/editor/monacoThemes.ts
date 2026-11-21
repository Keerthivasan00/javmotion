import type { Monaco } from '@monaco-editor/react'

export const MONACO_THEME = {
  dark: 'javmotion-dark',
  light: 'javmotion-light',
} as const

let defined = false

/** Register brand editor themes once (VS Code Dark / Light based). */
export function defineMonacoThemes(monaco: Monaco) {
  if (defined) return
  defined = true

  monaco.editor.defineTheme(MONACO_THEME.dark, {
    base: 'vs-dark',
    inherit: true,
    rules: [],
    colors: {
      'editor.background': '#0B1120',
      'editor.lineHighlightBackground': '#111827',
      'editorLineNumber.foreground': '#4B5563',
      'editorLineNumber.activeForeground': '#9CA3AF',
      'editorGutter.background': '#0B1120',
      'editor.selectionBackground': '#2563EB55',
      'editorCursor.foreground': '#A78BFA',
    },
  })

  monaco.editor.defineTheme(MONACO_THEME.light, {
    base: 'vs',
    inherit: true,
    rules: [],
    colors: {
      'editor.background': '#FFFFFF',
      'editor.lineHighlightBackground': '#F1F5F9',
      'editor.selectionBackground': '#2563EB33',
      'editorCursor.foreground': '#7C3AED',
    },
  })
}
