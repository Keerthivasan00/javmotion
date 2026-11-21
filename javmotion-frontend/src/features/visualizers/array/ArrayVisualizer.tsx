import { memo } from 'react'
import { LayoutGroup, motion } from 'framer-motion'
import { cn } from '@/lib/utils/cn'
import type { Highlight, TraceStep, Value } from '@/lib/trace/types'
import { EmptyHint } from '../shared/EmptyHint'
import { Pointer } from '../shared/Pointer'
import { indexPointers, localsReferencing, primitiveText, shortType } from '../shared/traceUtils'
import type { VisualizerProps } from '../types'
import { detectSwaps, sameText } from './detectSwaps'

type Role = Highlight['role']

const ROLE_CLASS: Partial<Record<Role, string>> = {
  compare: 'border-primary bg-primary/20',
  swap: 'border-accent bg-accent/25',
  found: 'border-success bg-success/20',
  active: 'border-warning bg-warning/20',
}

/** Later roles win when one cell has several highlights. */
const ROLE_ORDER: Role[] = ['found', 'active', 'compare', 'swap']

export function ArrayVisualizer({ step, prev }: VisualizerProps) {
  const arrays = localsReferencing(step, 'ARRAY')
  if (arrays.length === 0) return <EmptyHint>No arrays in scope at this step.</EmptyHint>

  return (
    <div className="space-y-6">
      {arrays.map(({ name, ref, obj }) => (
        <ArrayRow
          key={ref}
          name={name}
          heapRef={ref}
          type={obj.type}
          elements={obj.elements}
          length={obj.length ?? obj.elements.length}
          prevElements={prevElementsOf(prev, ref)}
          step={step}
        />
      ))}
    </div>
  )
}

function prevElementsOf(prev: TraceStep | undefined, ref: string): Value[] | undefined {
  const obj = prev?.heap[ref]
  return obj?.kind === 'ARRAY' ? obj.elements : undefined
}

interface ArrayRowProps {
  name: string
  heapRef: string
  type: string
  elements: Value[]
  length: number
  prevElements?: Value[]
  step: TraceStep
}

function ArrayRow({ name, heapRef, type, elements, length, prevElements, step }: ArrayRowProps) {
  const highlights = (step.highlights ?? []).filter((h) => h.ref === heapRef)
  const roleAt = (i: number): Role | undefined => {
    let best: Role | undefined
    for (const h of highlights) {
      if (h.index !== i || h.role === 'pointer') continue
      if (!best || ROLE_ORDER.indexOf(h.role) > ROLE_ORDER.indexOf(best)) best = h.role
    }
    return best
  }

  // JDI traces have no highlights: derive pointers from index-like locals instead.
  const explicit = highlights
    .filter((h) => h.index !== undefined && (h.role === 'pointer' || h.label))
    .map((h) => ({ label: h.label ?? '↑', index: h.index! }))
  // Concept traces (with highlights) say exactly where pointers go; never guess there.
  const pointers = step.highlights ? explicit : indexPointers(step, elements.length)

  const moves = detectSwaps(prevElements, elements)

  return (
    <figure>
      <figcaption className="mb-2 font-mono text-xs text-muted">
        <span className="text-accent">{name}</span> : {shortType(type)} · length {length}
      </figcaption>
      <LayoutGroup id={heapRef}>
        <div className="overflow-x-auto pb-1">
          <div className="flex gap-1" role="list" aria-label={`Array ${name}`}>
            {elements.map((value, i) => (
              <div
                key={i}
                className="flex w-11 shrink-0 flex-col items-center gap-1"
                role="listitem"
              >
                <Cell
                  // New key on a move restarts the slide-in from the old position.
                  key={moves.has(i) ? `${i}-${step.index}` : i}
                  text={primitiveText(value)}
                  role={roleAt(i)}
                  fromOffset={moves.get(i)}
                  changed={!moves.has(i) && !!prevElements && !sameText(prevElements[i], value)}
                />
                <span className="font-mono text-[10px] text-muted">{i}</span>
                <div className="flex h-7 flex-col items-center">
                  {pointers
                    .filter((p) => p.index === i)
                    .map((p) => (
                      <Pointer key={p.label} label={p.label} layoutId={`${heapRef}-${p.label}`} />
                    ))}
                </div>
              </div>
            ))}
            {length > elements.length && (
              <div className="flex h-11 items-center px-2 font-mono text-xs text-muted">
                +{length - elements.length} more
              </div>
            )}
          </div>
        </div>
      </LayoutGroup>
    </figure>
  )
}

interface CellProps {
  text: string
  role?: Role
  fromOffset?: number
  changed: boolean
}

const Cell = memo(function Cell({ text, role, fromOffset, changed }: CellProps) {
  return (
    <motion.div
      initial={fromOffset ? { x: fromOffset } : false}
      animate={{ x: 0 }}
      transition={{ type: 'spring', stiffness: 260, damping: 26 }}
      data-role={role}
      className={cn(
        'flex h-11 w-11 items-center justify-center rounded-md border-2 bg-surface-2 font-mono text-sm transition-colors duration-300',
        role ? ROLE_CLASS[role] : 'border-border',
        changed && 'jm-flash',
      )}
    >
      {text}
    </motion.div>
  )
})
