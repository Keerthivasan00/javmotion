import { useMemo, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { cn } from '@/lib/utils/cn'
import { EmptyHint } from '../shared/EmptyHint'
import { primitiveText } from '../shared/traceUtils'
import type { VisualizerProps } from '../types'
import { activeLoops, parseLoopHeader, type ActiveLoop } from './loopAnalysis'

/** Show only the latest iterations so long loops stay compact. */
const HISTORY_LIMIT = 8

export function LoopVisualizer({ trace, index, source }: VisualizerProps) {
  const loops = useMemo(() => activeLoops(trace, index), [trace, index])
  const lines = useMemo(() => source?.split('\n'), [source])

  if (loops.length === 0) return <EmptyHint>Not inside a loop at this step.</EmptyHint>

  return (
    <div className="space-y-4">
      {loops.map((loop, depth) => (
        <LoopCard
          key={loop.header}
          loop={loop}
          depth={depth}
          headerText={lines?.[loop.header - 1]}
        />
      ))}
    </div>
  )
}

function LoopCard({
  loop,
  depth,
  headerText,
}: {
  loop: ActiveLoop
  depth: number
  headerText?: string
}) {
  const header = parseLoopHeader(headerText)
  const history = loop.history.slice(-HISTORY_LIMIT)
  const hidden = loop.history.length - history.length
  const condition = loop.atHeader ? loop.lastCondition : undefined

  return (
    <section
      className="rounded-lg border bg-surface p-3"
      style={{ marginLeft: depth * 16 }}
      aria-label={`Loop at line ${loop.header}`}
    >
      <div className="mb-3 flex items-center gap-2">
        <span className="font-mono text-xs text-muted">
          {depth > 0 ? 'nested ' : ''}
          {header?.kind ?? 'loop'} · line {loop.header}
        </span>
        <span className="ml-auto text-xs text-muted">iteration</span>
        <motion.span
          key={loop.iterations}
          initial={{ scale: 1.4, opacity: 0.4 }}
          animate={{ scale: 1, opacity: 1 }}
          className="min-w-7 rounded bg-accent/15 px-1.5 text-center font-mono text-sm font-semibold text-accent"
          data-testid="iteration-count"
        >
          {loop.iterations}
        </motion.span>
      </div>

      {header && (
        <div className="mb-3 grid grid-cols-[auto_1fr_auto] items-center gap-x-3 gap-y-1 font-mono text-xs">
          {'init' in header && header.init && <Part label="init" code={header.init} />}
          <Part
            label="condition"
            code={header.condition}
            badge={
              <AnimatePresence mode="wait">
                {condition !== undefined && (
                  <motion.span
                    key={`${loop.iterations}-${String(condition)}`}
                    initial={{ rotateX: 90, opacity: 0 }}
                    animate={{ rotateX: 0, opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className={cn(
                      'rounded px-1.5 py-0.5 text-[11px] font-semibold',
                      condition ? 'bg-success/20 text-success' : 'bg-error/20 text-error',
                    )}
                    data-testid="condition-badge"
                  >
                    {String(condition)}
                  </motion.span>
                )}
              </AnimatePresence>
            }
          />
          {'update' in header && header.update && <Part label="update" code={header.update} />}
        </div>
      )}

      {!loop.atHeader && <p className="mb-2 text-xs text-muted">Running the loop body…</p>}
      {loop.atHeader && condition === false && (
        <p className="mb-2 text-xs text-error">Condition is false — the loop ends here.</p>
      )}

      {history.length > 0 && (
        <ol className="space-y-0.5 font-mono text-xs" aria-label="Iteration history">
          {hidden > 0 && <li className="text-muted">… {hidden} earlier checks</li>}
          {history.map((h) => (
            <li key={h.n} className="flex items-center gap-2">
              <span className="w-8 text-muted">#{h.n}</span>
              <span className="flex-1 truncate">
                {Object.entries(h.vars)
                  .map(([k, v]) => `${k} = ${primitiveText(v)}`)
                  .join(', ') || '—'}
              </span>
              <span className={h.condition ? 'text-success' : 'text-error'}>
                {h.condition ? '✓' : '✗'}
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

function Part({ label, code, badge }: { label: string; code: string; badge?: ReactNode }) {
  return (
    <>
      <span className="text-muted">{label}</span>
      <code className="truncate text-fg">{code}</code>
      <span>{badge}</span>
    </>
  )
}
