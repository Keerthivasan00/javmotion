import { useMemo, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { changedLocals } from '@/features/playback'
import { cn } from '@/lib/utils/cn'
import type { Frame } from '@/lib/trace/types'
import { EmptyHint } from '../shared/EmptyHint'
import { formatValue, shortType } from '../shared/traceUtils'
import type { VisualizerProps } from '../types'

export function VariableInspector({ step, prev }: VisualizerProps) {
  const [allFrames, setAllFrames] = useState(false)
  const changed = useMemo(() => changedLocals(step, prev), [step, prev])

  if (step.stack.length === 0)
    return <EmptyHint>Program finished — no variables in scope.</EmptyHint>

  // Top of stack first.
  const frames = allFrames ? [...step.stack].reverse() : [step.stack[step.stack.length - 1]]

  return (
    <div className="space-y-4">
      {step.stack.length > 1 && (
        <label className="flex items-center gap-2 text-xs text-muted">
          <input
            type="checkbox"
            checked={allFrames}
            onChange={(e) => setAllFrames(e.target.checked)}
          />
          Show all frames
        </label>
      )}
      {frames.map((frame, i) => (
        <FrameTable
          key={frame.id}
          frame={frame}
          heap={step.heap}
          changed={i === 0 ? changed : undefined}
        />
      ))}
    </div>
  )
}

function FrameTable({
  frame,
  heap,
  changed,
}: {
  frame: Frame
  heap: VisualizerProps['step']['heap']
  changed?: Set<string>
}) {
  const entries = Object.entries(frame.locals)

  return (
    <div>
      <h3 className="mb-1 font-mono text-xs text-muted">
        {shortType(frame.className)}.{frame.method}() · line {frame.line}
      </h3>
      {entries.length === 0 ? (
        <p className="text-xs text-muted">No local variables yet.</p>
      ) : (
        <table className="w-full font-mono text-sm">
          <thead className="sr-only">
            <tr>
              <th>Name</th>
              <th>Type</th>
              <th>Value</th>
            </tr>
          </thead>
          <tbody>
            <AnimatePresence initial={false}>
              {entries.map(([name, value]) => {
                const isChanged = changed?.has(name) ?? false
                const text = formatValue(value, heap)
                const type =
                  value.kind === 'PRIMITIVE'
                    ? value.type
                    : value.kind === 'REF'
                      ? shortType(heap[value.ref]?.type ?? '')
                      : ''
                return (
                  <motion.tr
                    key={name}
                    layout="position"
                    initial={{ opacity: 0, x: -12 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0 }}
                    className="border-b border-border/60"
                    data-changed={isChanged || undefined}
                  >
                    <td className="py-1.5 pr-3 text-accent">{name}</td>
                    <td className="py-1.5 pr-3 text-xs text-muted">{type}</td>
                    <td className="py-1.5">
                      {/* Re-keying on value restarts the flash animation each change. */}
                      <span
                        key={isChanged ? text : 'same'}
                        className={cn('rounded px-1', isChanged && 'jm-flash')}
                      >
                        {text}
                      </span>
                    </td>
                  </motion.tr>
                )
              })}
            </AnimatePresence>
          </tbody>
        </table>
      )}
    </div>
  )
}
