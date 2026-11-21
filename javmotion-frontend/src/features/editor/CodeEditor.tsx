import Editor from '@monaco-editor/react'
import { PageLoader } from '@/components/ui/PageLoader'
import { useThemeStore } from '@/lib/theme/themeStore'
import { useEditorStore } from './editorStore'
import { defineMonacoThemes, MONACO_THEME } from './monacoThemes'

/**
 * Monaco wrapper bound to editorStore.
 * TODO (Step 7): current-line + breakpoint decorations, read-only during playback.
 * TODO (Step 6): compile error markers.
 */
export function CodeEditor() {
  const source = useEditorStore((s) => s.source)
  const setSource = useEditorStore((s) => s.setSource)
  const theme = useThemeStore((s) => s.theme)

  return (
    <Editor
      language="java"
      value={source}
      onChange={(value) => setSource(value ?? '')}
      theme={MONACO_THEME[theme]}
      beforeMount={defineMonacoThemes}
      loading={<PageLoader />}
      options={{
        fontFamily: "'JetBrains Mono', monospace",
        fontSize: 14,
        fontLigatures: false,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        glyphMargin: true, // breakpoints later
        automaticLayout: true,
        tabSize: 4,
        padding: { top: 12 },
      }}
    />
  )
}
