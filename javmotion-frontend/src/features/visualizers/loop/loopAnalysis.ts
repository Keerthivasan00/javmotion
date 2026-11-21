import type { Trace, TraceStep, Value } from '@/lib/trace/types'
import { sameValue, topFrame } from '@/features/playback'
import { memoByTrace } from '../shared/traceUtils'

/**
 * Loop detection for real (JDI) traces, which carry no loop metadata.
 *
 * A "back-edge" is two consecutive steps in the same frame where the line goes DOWN.
 * The target line of a back-edge is a loop header. The loop body spans from the header
 * to the highest line seen between two consecutive visits of that header.
 */
export interface LoopInfo {
  header: number
  end: number
}

const sameFrame = (a: TraceStep, b: TraceStep) => {
  const fa = topFrame(a)
  const fb = topFrame(b)
  return !!fa && !!fb && fa.id === fb.id && a.stack.length === b.stack.length
}

export const findLoops = memoByTrace((trace: Trace): LoopInfo[] => {
  const steps = trace.steps
  const headers = new Set<number>()
  for (let k = 1; k < steps.length; k++) {
    if (sameFrame(steps[k - 1], steps[k]) && steps[k].line < steps[k - 1].line) {
      headers.add(steps[k].line)
    }
  }

  return [...headers]
    .map((header) => {
      let end = header
      let lastVisit = -1
      for (let k = 0; k < steps.length; k++) {
        if (steps[k].line === header && topFrame(steps[k])) {
          if (lastVisit >= 0) {
            for (let m = lastVisit + 1; m < k; m++) {
              if (sameFrame(steps[m], steps[k])) end = Math.max(end, steps[m].line)
            }
          }
          lastVisit = k
        }
      }
      return { header, end }
    })
    .sort((a, b) => a.header - b.header)
})

export interface IterationRecord {
  /** 1-based iteration number. */
  n: number
  /** Loop-variable values at the header check that started this iteration. */
  vars: Record<string, Value>
  /** Result of the condition at that check. */
  condition: boolean
}

export interface ActiveLoop extends LoopInfo {
  /** Iterations entered so far in the current run of this loop. */
  iterations: number
  /** True while the current step is the header line itself. */
  atHeader: boolean
  /** Condition result of the most recent header check (undefined before the first check). */
  lastCondition?: boolean
  /** Locals that change between header visits (e.g. `i`). */
  loopVars: string[]
  history: IterationRecord[]
}

/** Condition at a header visit: true if the next same-frame step stays inside the body. */
function conditionAt(steps: TraceStep[], k: number, loop: LoopInfo): boolean {
  const next = steps[k + 1]
  if (!next || !sameFrame(steps[k], next)) return true // calls inside the condition: assume true
  return next.line > loop.header && next.line <= loop.end
}

/** Loops enclosing the step at `index`, outermost first. */
export function activeLoops(trace: Trace, index: number): ActiveLoop[] {
  const steps = trace.steps
  const step = steps[index]
  if (!step || !topFrame(step)) return []

  return findLoops(trace)
    .filter((l) => step.line >= l.header && step.line <= l.end)
    .map((loop) => {
      // Walk back to where this run of the loop started (same frame, inside the range).
      let start = index
      while (
        start > 0 &&
        sameFrame(steps[start - 1], step) &&
        steps[start - 1].line >= loop.header &&
        steps[start - 1].line <= loop.end
      ) {
        start--
      }

      const visits: number[] = []
      for (let k = start; k <= index; k++) {
        if (steps[k].line === loop.header && sameFrame(steps[k], step)) visits.push(k)
      }

      const loopVars = changingLocals(steps, visits)
      const history: IterationRecord[] = visits.map((k, i) => ({
        n: i + 1,
        vars: pick(topFrame(steps[k])!.locals, loopVars),
        condition: conditionAt(steps, k, loop),
      }))
      const lastCondition = history.at(-1)?.condition
      return {
        ...loop,
        iterations: history.filter((h) => h.condition).length,
        atHeader: step.line === loop.header,
        lastCondition,
        loopVars,
        history,
      }
    })
}

function changingLocals(steps: TraceStep[], visits: number[]): string[] {
  const names = new Set<string>()
  for (let v = 1; v < visits.length; v++) {
    const a = topFrame(steps[visits[v - 1]])!.locals
    const b = topFrame(steps[visits[v]])!.locals
    for (const name of Object.keys(b)) {
      if (!sameValue(a[name], b[name]) && b[name].kind === 'PRIMITIVE') names.add(name)
    }
  }
  // Before the second visit, fall back to index-like locals that exist at the header.
  if (names.size === 0 && visits.length > 0) {
    for (const [name, value] of Object.entries(topFrame(steps[visits[0]])!.locals)) {
      if (value.kind === 'PRIMITIVE' && /^(i|j|k|idx)$/.test(name)) names.add(name)
    }
  }
  return [...names]
}

function pick(locals: Record<string, Value>, names: string[]): Record<string, Value> {
  const out: Record<string, Value> = {}
  for (const n of names) if (locals[n]) out[n] = locals[n]
  return out
}

/** Parses a loop header line of source into parts for display. */
export function parseLoopHeader(text: string | undefined) {
  if (!text) return null
  const t = text.trim()
  const forMatch = /^for\s*\((.*);(.*);(.*)\)/.exec(t)
  if (forMatch) {
    return {
      kind: 'for' as const,
      init: forMatch[1].trim(),
      condition: forMatch[2].trim(),
      update: forMatch[3].trim(),
    }
  }
  const forEach = /^for\s*\((.*):(.*)\)/.exec(t)
  if (forEach)
    return {
      kind: 'for-each' as const,
      init: forEach[1].trim(),
      condition: `in ${forEach[2].trim()}`,
    }
  const whileMatch = /^(?:\}\s*)?while\s*\((.*)\)/.exec(t)
  if (whileMatch) return { kind: 'while' as const, condition: whileMatch[1].trim() }
  return { kind: 'loop' as const, condition: t.replace(/\{\s*$/, '').trim() }
}
