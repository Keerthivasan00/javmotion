import { describe, expect, it } from 'vitest'
import { applicableVisualizers, defaultVisualizer } from '@/features/visualizers'
import { findLoops } from '@/features/visualizers/loop/loopAnalysis'
import jdiException from '@/test/fixtures/traces/jdi-exception.json'
import jdiLoop from '@/test/fixtures/traces/jdi-loop.json'
import jdiObjects from '@/test/fixtures/traces/jdi-objects.json'
import jdiRecursion from '@/test/fixtures/traces/jdi-recursion.json'
import jdiStrings from '@/test/fixtures/traces/jdi-strings.json'
import { parseTrace } from './schema'

/**
 * Real traces recorded by the backend JDI spike (javmotion-backend/spikes/jdi-spike).
 * They prove the tracer output matches the contract and that the visualizers' heuristics
 * (loop detection, registry) work on real data, not just hand-made fixtures.
 */
describe('real JDI traces', () => {
  it.each([
    ['loop', jdiLoop],
    ['recursion', jdiRecursion],
    ['exception', jdiException],
    ['objects', jdiObjects],
    ['strings', jdiStrings],
  ])('%s passes schema validation', (_, json) => {
    const trace = parseTrace(json)
    expect(trace.source).toBe('JDI')
    expect(trace.steps.at(-1)?.event).toBe('END')
  })

  it('loop: same line shape as the hand-made for-loop fixture', () => {
    const trace = parseTrace(jdiLoop)
    expect(findLoops(trace)).toEqual([{ header: 4, end: 6 }])
    expect(trace.steps.at(-1)?.stdout).toBe('sum = 1\nsum = 3\nsum = 6\ndone\n')
    expect(defaultVisualizer(trace).id).toBe('loop')
  })

  it('recursion: frames get distinct ids and return values are captured', () => {
    const trace = parseTrace(jdiRecursion)
    const deepest = Math.max(...trace.steps.map((s) => s.stack.length))
    expect(deepest).toBe(5) // main + factorial(4..1)
    const ids = new Set(trace.steps.flatMap((s) => s.stack.map((f) => f.id)))
    expect(ids.size).toBe(5)
    const returns = trace.steps.filter((s) => s.event === 'RETURN' && s.returnValue)
    expect(returns.map((s) => (s.returnValue as { value: number }).value)).toEqual([1, 2, 6, 24])
    expect(defaultVisualizer(trace).id).toBe('callstack')
    expect(findLoops(trace)).toEqual([]) // returning to a caller line is not a loop
  })

  it('exception: uncaught exception step carries type and message', () => {
    const trace = parseTrace(jdiException)
    const ex = trace.steps.find((s) => s.event === 'EXCEPTION')
    expect(ex?.exception).toEqual({
      type: 'java.lang.ArrayIndexOutOfBoundsException',
      message: 'Index 3 out of bounds for length 3',
    })
  })

  it('strings: array tab applies, large arrays are previewed with length', () => {
    const trace = parseTrace(jdiStrings)
    expect(applicableVisualizers(trace).map((v) => v.id)).toEqual(
      expect.arrayContaining(['array', 'string', 'loop']),
    )
    const big = Object.values(trace.steps.at(-2)!.heap).find(
      (o) => o.kind === 'ARRAY' && o.length === 150,
    )
    expect(big && big.kind === 'ARRAY' && big.elements.length).toBe(100)
  })
})
