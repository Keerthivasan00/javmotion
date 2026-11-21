import type { HeapObject, Trace, TraceStep, Value } from '@/lib/trace/types'
import { topFrame } from '@/features/playback'

/** Local names that usually act as indexes into an array or string. */
export const POINTER_NAMES = new Set([
  'i',
  'j',
  'k',
  'l',
  'r',
  'p',
  'q',
  'lo',
  'hi',
  'low',
  'mid',
  'high',
  'left',
  'right',
  'start',
  'end',
  'idx',
  'index',
])

export type HeapKind = HeapObject['kind']
export type HeapOf<K extends HeapKind> = Extract<HeapObject, { kind: K }>

export interface NamedRef<K extends HeapKind> {
  name: string
  ref: string
  obj: HeapOf<K>
}

/** Top-frame locals that point to a heap object of the given kind, in declaration order. */
export function localsReferencing<K extends HeapKind>(
  step: TraceStep | undefined,
  kind: K,
): NamedRef<K>[] {
  const frame = topFrame(step)
  if (!step || !frame) return []
  const out: NamedRef<K>[] = []
  for (const [name, value] of Object.entries(frame.locals)) {
    if (value.kind !== 'REF') continue
    const obj = step.heap[value.ref]
    // `main(String[] args)` is almost always empty — noise in every JDI trace.
    const emptyMainArgs =
      frame.method === 'main' &&
      name === 'args' &&
      obj?.kind === 'ARRAY' &&
      obj.elements.length === 0
    if (obj?.kind === kind && !emptyMainArgs)
      out.push({ name, ref: value.ref, obj: obj as HeapOf<K> })
  }
  return out
}

/** Integer locals of the top frame that look like indexes and fall inside [0, length). */
export function indexPointers(step: TraceStep | undefined, length: number) {
  const frame = topFrame(step)
  if (!frame) return []
  const out: { label: string; index: number }[] = []
  for (const [name, value] of Object.entries(frame.locals)) {
    if (
      POINTER_NAMES.has(name) &&
      value.kind === 'PRIMITIVE' &&
      typeof value.value === 'number' &&
      Number.isInteger(value.value) &&
      value.value >= 0 &&
      value.value < length
    ) {
      out.push({ label: name, index: value.value })
    }
  }
  return out
}

export function primitiveText(value: Value): string {
  if (value.kind === 'NULL') return 'null'
  if (value.kind === 'REF') return `→${value.ref}`
  if (value.type === 'char') return `'${String(value.value)}'`
  return String(value.value)
}

/** Short human-readable value, following one level of reference for strings and arrays. */
export function formatValue(value: Value, heap: Record<string, HeapObject>): string {
  if (value.kind !== 'REF') return primitiveText(value)
  const obj = heap[value.ref]
  if (!obj) return `→ ${value.ref}`
  switch (obj.kind) {
    case 'STRING':
      return JSON.stringify(obj.value)
    case 'ARRAY': {
      const items = obj.elements.map(primitiveText).join(', ')
      const more = obj.length && obj.length > obj.elements.length ? ', …' : ''
      return `[${items}${more}]`
    }
    case 'LIST':
      return `${shortType(obj.type)} (${obj.elements.length})`
    case 'MAP':
      return `${shortType(obj.type)} (${obj.entries.length})`
    case 'OBJECT':
      return `${shortType(obj.type)} @${value.ref}`
  }
}

export function shortType(type: string): string {
  return type.replace(/\b[a-z]+(\.[a-z]+)*\./g, '')
}

/** Memoize a trace-level computation (traces are immutable once loaded). */
export function memoByTrace<T>(fn: (trace: Trace) => T): (trace: Trace) => T {
  const cache = new WeakMap<Trace, T>()
  return (trace) => {
    let hit = cache.get(trace)
    if (hit === undefined) {
      hit = fn(trace)
      cache.set(trace, hit)
    }
    return hit
  }
}

export const traceHasLocalKind = (kind: HeapKind) =>
  memoByTrace((trace) => trace.steps.some((s) => localsReferencing(s, kind).length > 0))
