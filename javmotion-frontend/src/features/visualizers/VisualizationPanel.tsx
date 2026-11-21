import { useMemo, useState, type ReactNode } from 'react'
import { Clapperboard, X } from 'lucide-react'
import { IconButton } from '@/components/ui/IconButton'
import { useEditorStore } from '@/features/editor'
import {
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

interface VisualizationPanelProps {
  /** Program the trace refers to; defaults to the editor's source. */
  source?: string
  /** Shown while no trace is loaded. */
  empty?: ReactNode
}

export function VisualizationPanel({ source, empty }: VisualizationPanelProps) {
  usePlaybackTicker()
  usePlaybackShortcuts()
  const trace = usePlaybackStore((s) => s.trace)

  return (
    <section className="flex h-full flex-col bg-bg" aria-label="Visualization">
      {trace ? (
        <>
          {/* Re-mount per trace so the auto-selected tab resets. */}
          <TraceView key={traceKey(trace)} trace={trace} source={source} />
          <PlaybackControls />
        </>
      ) : (
        (empty ?? <DefaultEmptyState />)
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

function TraceView({ trace, source: sourceProp }: { trace: Trace; source?: string }) {
  const index = usePlaybackStore((s) => s.index)
  const clear = usePlaybackStore((s) => s.clear)
  const editorSource = useEditorStore((s) => s.source)
  const source = sourceProp ?? editorSource

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

function DefaultEmptyState() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-center">
      <Clapperboard size={40} className="text-accent" aria-hidden="true" />
      <p className="font-medium">Nothing to play yet</p>
      <p className="max-w-xs text-sm text-muted">
        Variables, arrays, loops and memory will animate here step by step.
      </p>
    </div>
  )
}
