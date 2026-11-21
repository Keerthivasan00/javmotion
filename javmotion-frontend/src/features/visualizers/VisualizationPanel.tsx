import { useMemo, useState } from 'react'
import { Clapperboard, X } from 'lucide-react'
import { IconButton } from '@/components/ui/IconButton'
import { useEditorStore } from '@/features/editor'
import {
  DEMO_TRACES,
  PlaybackControls,
  topFrame,
  usePlaybackShortcuts,
  usePlaybackStore,
  usePlaybackTicker,
} from '@/features/playback'
import type { Trace } from '@/lib/trace/types'
import { cn } from '@/lib/utils/cn'
import { applicableVisualizers, defaultVisualizer } from './registry'
import { shortType } from './shared/traceUtils'
import type { VisualizerId } from './types'

export function VisualizationPanel() {
  usePlaybackTicker()
  usePlaybackShortcuts()
  const trace = usePlaybackStore((s) => s.trace)

  return (
    <section className="flex h-full flex-col bg-bg" aria-label="Visualization">
      {trace ? (
        <>
          {/* Re-mount per trace so the auto-selected tab resets. */}
          <TraceView key={traceKey(trace)} trace={trace} />
          <PlaybackControls />
        </>
      ) : (
        <EmptyState />
      )}
    </section>
  )
}

const traceIds = new WeakMap<Trace, number>()
let nextTraceId = 0
function traceKey(trace: Trace) {
  let id = traceIds.get(trace)
  if (id === undefined) traceIds.set(trace, (id = nextTraceId++))
  return id
}

function TraceView({ trace }: { trace: Trace }) {
  const index = usePlaybackStore((s) => s.index)
  const clear = usePlaybackStore((s) => s.clear)
  const source = useEditorStore((s) => s.source)

  const visualizers = useMemo(() => applicableVisualizers(trace), [trace])
  const [chosen, setChosen] = useState<VisualizerId>(() => defaultVisualizer(trace).id)
  const active = visualizers.find((v) => v.id === chosen) ?? visualizers[0]

  const step = trace.steps[index]
  if (!step) return null
  const prev = index > 0 ? trace.steps[index - 1] : undefined
  const frame = topFrame(step)
  const Active = active.component

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div
        className="flex shrink-0 items-center gap-1 overflow-x-auto border-b bg-surface px-2"
        role="tablist"
      >
        {visualizers.map((v) => (
          <button
            key={v.id}
            role="tab"
            id={`viz-tab-${v.id}`}
            aria-selected={v.id === active.id}
            aria-controls="viz-tabpanel"
            onClick={() => setChosen(v.id)}
            className={cn(
              'flex items-center gap-1.5 border-b-2 px-3 py-2 text-xs font-medium whitespace-nowrap',
              v.id === active.id
                ? 'border-accent text-fg'
                : 'border-transparent text-muted hover:text-fg',
            )}
          >
            <v.icon size={14} aria-hidden="true" />
            {v.label}
          </button>
        ))}
        <IconButton label="Close trace" className="ml-auto h-7 w-7 shrink-0" onClick={clear}>
          <X size={16} />
        </IconButton>
      </div>

      <div className="flex shrink-0 items-center gap-2 px-4 pt-3 text-xs">
        <span className="rounded bg-accent/15 px-2 py-0.5 font-mono text-accent">{step.event}</span>
        <span className="truncate text-muted">
          {frame ? `${shortType(frame.className)}.${frame.method}()` : 'program finished'} · line{' '}
          {step.line}
        </span>
      </div>

      <div
        id="viz-tabpanel"
        role="tabpanel"
        aria-labelledby={`viz-tab-${active.id}`}
        className="min-h-0 flex-1 overflow-auto p-4"
      >
        <Active trace={trace} index={index} step={step} prev={prev} source={source} />
      </div>
    </div>
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
