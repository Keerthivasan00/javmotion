import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { cn } from '@/lib/utils/cn'
import { EmptyHint } from '../shared/EmptyHint'
import { formatValue, shortType } from '../shared/traceUtils'
import type { VisualizerProps } from '../types'

export function CallStack({ step }: VisualizerProps) {
  const [selected, setSelected] = useState<string | null>(null)
  if (step.stack.length === 0) return <EmptyHint>Call stack is empty — program finished.</EmptyHint>

  const frames = [...step.stack].reverse() // top of stack first
  const open = frames.find((f) => f.id === selected) ?? frames[0]

  return (
    <div className="space-y-3">
      <ol className="space-y-1" aria-label="Call stack">
        <AnimatePresence initial={false}>
          {frames.map((frame, i) => (
            <motion.li
              key={frame.id}
              layout
              initial={{ opacity: 0, y: -16 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -16 }}
            >
              <button
                type="button"
                onClick={() => setSelected(frame.id)}
                aria-current={frame.id === open.id || undefined}
                className={cn(
                  'flex w-full items-center gap-2 rounded-md border px-3 py-2 text-left font-mono text-sm',
                  frame.id === open.id
                    ? 'border-accent bg-accent/10'
                    : 'bg-surface hover:bg-surface-2',
                )}
              >
                <span className="text-fg">
                  {shortType(frame.className)}.{frame.method}()
                </span>
                {i === 0 && (
                  <span className="rounded bg-primary/20 px-1.5 text-[10px] text-primary">
                    running
                  </span>
                )}
                <span className="ml-auto text-xs text-muted">line {frame.line}</span>
              </button>
            </motion.li>
          ))}
        </AnimatePresence>
      </ol>

      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 font-mono text-xs">
        {Object.entries(open.locals).map(([name, value]) => (
          <div key={name} className="contents">
            <dt className="text-accent">{name}</dt>
            <dd className="truncate">{formatValue(value, step.heap)}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}
