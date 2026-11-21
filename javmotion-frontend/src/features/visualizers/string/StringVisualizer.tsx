import { LayoutGroup, motion } from 'framer-motion'
import { cn } from '@/lib/utils/cn'
import type { Highlight, TraceStep } from '@/lib/trace/types'
import { EmptyHint } from '../shared/EmptyHint'
import { Pointer } from '../shared/Pointer'
import { indexPointers, localsReferencing } from '../shared/traceUtils'
import type { VisualizerProps } from '../types'

const ROLE_CLASS: Partial<Record<Highlight['role'], string>> = {
  compare: 'border-primary bg-primary/20',
  swap: 'border-accent bg-accent/25',
  found: 'border-success bg-success/20',
  active: 'border-warning bg-warning/20',
}

/** Long strings are cut to keep the panel readable (backend caps at 1000 chars anyway). */
const MAX_TILES = 80

export function StringVisualizer({ step, prev }: VisualizerProps) {
  const strings = localsReferencing(step, 'STRING')
  if (strings.length === 0) return <EmptyHint>No strings in scope at this step.</EmptyHint>

  return (
    <div className="space-y-6">
      {strings.map(({ name, ref, obj }) => (
        <StringRow
          key={name}
          name={name}
          heapRef={ref}
          value={obj.value}
          interned={obj.interned}
          step={step}
          prevValue={prevValueOf(prev, name)}
          prevRef={prevRefOf(prev, name)}
        />
      ))}
    </div>
  )
}

function prevRefOf(prev: TraceStep | undefined, name: string) {
  const v = prev?.stack.at(-1)?.locals[name]
  return v?.kind === 'REF' ? v.ref : undefined
}

function prevValueOf(prev: TraceStep | undefined, name: string) {
  const ref = prevRefOf(prev, name)
  const obj = ref ? prev?.heap[ref] : undefined
  return obj?.kind === 'STRING' ? obj.value : undefined
}

interface StringRowProps {
  name: string
  heapRef: string
  value: string
  interned?: boolean
  step: TraceStep
  prevValue?: string
  prevRef?: string
}

function StringRow({ name, heapRef, value, interned, step, prevValue, prevRef }: StringRowProps) {
  const chars = [...value].slice(0, MAX_TILES)
  const highlights = (step.highlights ?? []).filter((h) => h.ref === heapRef)
  const wholeRole = highlights.find((h) => h.index === undefined)?.role
  const roleAt = (i: number) =>
    highlights.find((h) => h.index === i && h.role !== 'pointer')?.role ??
    (highlights.some((h) => h.index === i && h.role === 'pointer') ? 'active' : undefined)

  const explicit = highlights
    .filter((h) => h.index !== undefined && (h.role === 'pointer' || h.label))
    .map((h) => ({ label: h.label ?? '↑', index: h.index! }))
  // Concept traces (with highlights) say exactly where pointers go; never guess there.
  const pointers = step.highlights ? explicit : indexPointers(step, chars.length)

  // Strings are immutable: a changed value means a brand-new object was created.
  const newObject = prevRef !== undefined && prevRef !== heapRef
  const grewFrom = prevValue !== undefined && value.startsWith(prevValue) ? prevValue.length : null

  return (
    <figure>
      <figcaption className="mb-2 flex flex-wrap items-center gap-2 font-mono text-xs text-muted">
        <span>
          <span className="text-accent">{name}</span> : String · length {value.length}
        </span>
        {newObject && (
          <motion.span
            key={heapRef}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className="rounded bg-accent/15 px-1.5 py-0.5 text-[10px] text-accent"
            title="Strings are immutable, so this created a new String object"
          >
            new String object
          </motion.span>
        )}
        {interned && (
          <span className="rounded bg-surface-2 px-1.5 py-0.5 text-[10px]">string pool</span>
        )}
      </figcaption>
      {chars.length === 0 ? (
        <div className="flex h-10 items-center font-mono text-sm text-muted">"" (empty)</div>
      ) : (
        <LayoutGroup id={`str-${name}`}>
          <div className="overflow-x-auto pb-1">
            <div className="flex gap-1" role="list" aria-label={`String ${name}`}>
              {chars.map((ch, i) => {
                const role = roleAt(i) ?? wholeRole
                const isNew = grewFrom !== null && newObject && i >= grewFrom
                return (
                  <div
                    key={i}
                    className="flex w-9 shrink-0 flex-col items-center gap-1"
                    role="listitem"
                  >
                    <motion.div
                      key={isNew ? `${i}-${heapRef}` : i}
                      initial={isNew ? { opacity: 0, y: -14 } : false}
                      animate={{ opacity: 1, y: 0 }}
                      className={cn(
                        'flex h-10 w-9 items-center justify-center rounded-md border-2 bg-surface-2 font-mono text-sm transition-colors duration-300',
                        role ? ROLE_CLASS[role] : 'border-border',
                      )}
                      data-role={role}
                    >
                      {ch === ' ' ? '␣' : ch}
                    </motion.div>
                    <span className="font-mono text-[10px] text-muted">{i}</span>
                    <div className="flex h-7 flex-col items-center">
                      {pointers
                        .filter((p) => p.index === i)
                        .map((p) => (
                          <Pointer
                            key={p.label}
                            label={p.label}
                            layoutId={`str-${name}-${p.label}`}
                          />
                        ))}
                    </div>
                  </div>
                )
              })}
              {value.length > MAX_TILES && (
                <div className="flex h-10 items-center px-2 font-mono text-xs text-muted">
                  +{value.length - MAX_TILES} more
                </div>
              )}
            </div>
          </div>
        </LayoutGroup>
      )}
    </figure>
  )
}
