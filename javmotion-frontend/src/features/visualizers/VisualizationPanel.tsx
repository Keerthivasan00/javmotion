import { useState } from 'react'
import { Clapperboard, X } from 'lucide-react'
import { IconButton } from '@/components/ui/IconButton'
import { useEditorStore } from '@/features/editor'
import {
  DEMO_TRACES,
  PlaybackControls,
  topFrame,
  useCurrentStep,
  useHasTrace,
  usePlaybackShortcuts,
  usePlaybackStore,
  usePlaybackTicker,
} from '@/features/playback'
import type { HeapObject, Value } from '@/lib/trace/types'

/**
 * TODO (Step 3): visualizer registry + tabs (Variables, Array, String, Loop, Call Stack)
 * replacing the plain StepSummary below.
 */
export function VisualizationPanel() {
  usePlaybackTicker()
  usePlaybackShortcuts()
  const hasTrace = useHasTrace()

  return (
    <section className="flex h-full flex-col bg-bg" aria-label="Visualization">
      {hasTrace ? (
        <>
          <div className="min-h-0 flex-1 overflow-auto p-4">
            <StepSummary />
          </div>
          <PlaybackControls />
        </>
      ) : (
        <EmptyState />
      )}
    </section>
  )
}

function EmptyState() {
  const [error, setError] = useState<string | null>(null)

  const loadDemo = async (id: string) => {
    const demo = DEMO_TRACES.find((d) => d.id === id)
    if (!demo) return
    try {
      const trace = await demo.load()
      useEditorStore.getState().loadTemplate(demo.code)
      usePlaybackStore.getState().load(trace)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load demo trace')
    }
  }

  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-center">
      <Clapperboard size={40} className="text-accent" aria-hidden="true" />
      <p className="font-medium">Click Visualize to see your code in motion</p>
      <p className="max-w-xs text-sm text-muted">
        Variables, arrays, loops and memory will animate here step by step.
      </p>
      <label className="mt-2 flex items-center gap-2 text-sm text-muted">
        Try a demo
        <select
          className="h-8 rounded-md border bg-surface-2 px-2 text-sm text-fg"
          defaultValue=""
          onChange={(e) => void loadDemo(e.target.value)}
        >
          <option value="" disabled>
            Choose…
          </option>
          {DEMO_TRACES.map((d) => (
            <option key={d.id} value={d.id}>
              {d.title}
            </option>
          ))}
        </select>
      </label>
      {error && <p className="text-sm text-error">{error}</p>}
    </div>
  )
}

/** Temporary plain view of the current step until Step 3 visualizers land. */
function StepSummary() {
  const step = useCurrentStep()
  const clear = usePlaybackStore((s) => s.clear)
  if (!step) return null
  const frame = topFrame(step)

  return (
    <div className="space-y-3 text-sm">
      <div className="flex items-center gap-2">
        <span className="rounded bg-accent/15 px-2 py-0.5 font-mono text-xs text-accent">
          {step.event}
        </span>
        <span className="text-muted">
          {frame ? `${frame.className}.${frame.method}()` : 'program finished'} · line {step.line}
        </span>
        <IconButton label="Close trace" className="ml-auto h-7 w-7" onClick={clear}>
          <X size={16} />
        </IconButton>
      </div>
      {frame && (
        <table className="w-full font-mono text-xs">
          <tbody>
            {Object.entries(frame.locals).map(([name, value]) => (
              <tr key={name} className="border-b border-border/60">
                <td className="py-1 pr-4 text-accent">{name}</td>
                <td className="py-1">{renderValue(value, step.heap)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

function renderValue(value: Value, heap: Record<string, HeapObject>) {
  if (value.kind === 'NULL') return 'null'
  if (value.kind === 'PRIMITIVE') return String(value.value)
  const obj = heap[value.ref]
  if (!obj) return `→ ${value.ref}`
  if (obj.kind === 'STRING') return `"${obj.value}"`
  if (obj.kind === 'ARRAY')
    return `[${obj.elements.map((e) => (e.kind === 'PRIMITIVE' ? e.value : '…')).join(', ')}]`
  return `${obj.type} (${value.ref})`
}
