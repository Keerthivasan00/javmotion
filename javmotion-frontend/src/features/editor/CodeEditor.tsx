import { useEffect, useRef } from 'react'
import Editor, { type OnMount } from '@monaco-editor/react'
import { PageLoader } from '@/components/ui/PageLoader'
import { useThemeStore } from '@/lib/theme/themeStore'
import { useEditorStore } from './editorStore'
import { toMarkers, type EditorDiagnostic } from './markers'
import { defineMonacoThemes, MONACO_THEME } from './monacoThemes'

type MonacoApi = Parameters<OnMount>[1]
type EditorInstance = Parameters<OnMount>[0]

const MARKER_OWNER = 'javac'

interface CodeEditorProps {
  /** Compile errors to show as red squiggles; replaced on every change. */
  diagnostics?: EditorDiagnostic[]
  /** Ctrl+Enter inside the editor (Monaco would otherwise insert a line). */
  onRun?: () => void
}

/**
 * Monaco wrapper bound to editorStore.
 * TODO (Step 7): current-line + breakpoint decorations, read-only during playback.
 */
export function CodeEditor({ diagnostics = [], onRun }: CodeEditorProps) {
  const source = useEditorStore((s) => s.source)
  const setSource = useEditorStore((s) => s.setSource)
  const theme = useThemeStore((s) => s.theme)
  const editorRef = useRef<EditorInstance | null>(null)
  const monacoRef = useRef<MonacoApi | null>(null)
  const onRunRef = useRef(onRun)

  useEffect(() => {
    onRunRef.current = onRun
  }, [onRun])

  const handleMount: OnMount = (editor, monaco) => {
    editorRef.current = editor
    monacoRef.current = monaco
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => onRunRef.current?.())
    applyMarkers(editor, monaco, diagnostics)
  }

  useEffect(() => {
    if (editorRef.current && monacoRef.current) {
      applyMarkers(editorRef.current, monacoRef.current, diagnostics)
    }
  }, [diagnostics])

  return (
    <Editor
      language="java"
      value={source}
      onChange={(value) => setSource(value ?? '')}
      theme={MONACO_THEME[theme]}
      beforeMount={defineMonacoThemes}
      onMount={handleMount}
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

function applyMarkers(editor: EditorInstance, monaco: MonacoApi, diagnostics: EditorDiagnostic[]) {
  const model = editor.getModel()
  if (!model) return
  monaco.editor.setModelMarkers(
    model,
    MARKER_OWNER,
    toMarkers(diagnostics, model.getLineCount(), monaco),
  )
}
