import { Clapperboard, Play, RotateCcw, WandSparkles } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useEditorStore } from './editorStore'
import { formatJava } from './formatJava'
import { TEMPLATES } from './templates'

/**
 * Run / Visualize are UI-only until Step 6 / Step 7.
 */
export function EditorToolbar() {
  const reset = useEditorStore((s) => s.reset)
  const loadTemplate = useEditorStore((s) => s.loadTemplate)

  const format = () => {
    const { source, setSource } = useEditorStore.getState()
    const formatted = formatJava(source)
    if (formatted !== source) setSource(formatted)
  }

  return (
    <div className="flex h-12 shrink-0 items-center gap-2 border-b bg-surface px-3">
      <Button size="sm" disabled title="Coming in Step 6">
        <Play size={16} /> Run
      </Button>
      <Button size="sm" variant="accent" disabled title="Coming in Step 7">
        <Clapperboard size={16} /> Visualize
      </Button>
      <Button size="sm" variant="ghost" onClick={reset}>
        <RotateCcw size={16} /> Reset
      </Button>
      <Button size="sm" variant="ghost" onClick={format} title="Re-indent code">
        <WandSparkles size={16} /> Format
      </Button>

      <label className="ml-auto flex items-center gap-2 text-sm text-muted">
        <span className="hidden sm:inline">Template</span>
        <select
          className="h-8 rounded-md border bg-surface-2 px-2 text-sm text-fg"
          defaultValue=""
          onChange={(e) => {
            const template = TEMPLATES.find((t) => t.id === e.target.value)
            if (template) loadTemplate(template.code)
          }}
        >
          <option value="" disabled>
            Choose…
          </option>
          {TEMPLATES.map((t) => (
            <option key={t.id} value={t.id}>
              {t.title}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}
