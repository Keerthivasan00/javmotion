import { describe, expect, it } from 'vitest'
import { defaultVisualizer } from '@/features/visualizers'
import { activeLoops, findLoops } from '@/features/visualizers/loop/loopAnalysis'
import binarySearch from '@/test/fixtures/concepts/binary-search.json'
import bubbleSort from '@/test/fixtures/concepts/bubble-sort.json'
import doWhileLoop from '@/test/fixtures/concepts/do-while-loop.json'
import forLoop from '@/test/fixtures/concepts/for-loop.json'
import linearSearch from '@/test/fixtures/concepts/linear-search.json'
import nestedLoop from '@/test/fixtures/concepts/nested-loop.json'
import selectionSort from '@/test/fixtures/concepts/selection-sort.json'
import stringReverse from '@/test/fixtures/concepts/string-reverse.json'
import whileLoop from '@/test/fixtures/concepts/while-loop.json'
import { parseTrace } from './schema'

/**
 * Real responses from the backend concept endpoints (POST /api/v1/visualizer/*),
 * shape { key, title, code, trace }. Recorded 2026-11-21 from a running javmotion-api.
 */
const responses = {
  'string-reverse': stringReverse,
  'bubble-sort': bubbleSort,
  'selection-sort': selectionSort,
  'linear-search': linearSearch,
  'binary-search': binarySearch,
  'for-loop': forLoop,
  'while-loop': whileLoop,
  'do-while-loop': doWhileLoop,
  'nested-loop': nestedLoop,
}

describe('backend concept responses', () => {
  it.each(Object.entries(responses))('%s: valid trace whose lines point at code', (_, res) => {
    const trace = parseTrace(res.trace)
    const lines = res.code.split('\n')
    expect(trace.source).toBe('CONCEPT')
    for (const step of trace.steps) {
      expect(step.line).toBeGreaterThanOrEqual(1)
      expect(lines[step.line - 1]?.trim()).not.toBe('')
    }
  })

  it('picks the expected default tab', () => {
    const tab = (res: { trace: unknown }) => defaultVisualizer(parseTrace(res.trace)).id
    expect(tab(stringReverse)).toBe('string')
    expect(tab(bubbleSort)).toBe('array')
    expect(tab(binarySearch)).toBe('array')
    expect(tab(forLoop)).toBe('loop')
  })

  it('loop detection finds the loops the code declares', () => {
    const loops = (res: { trace: unknown }) => findLoops(parseTrace(res.trace)).map((l) => l.header)
    expect(loops(stringReverse)).toEqual([5])
    expect(loops(bubbleSort)).toEqual([5, 6])
    expect(loops(selectionSort)).toEqual([5, 7])
    expect(loops(binarySearch)).toEqual([8])
    expect(loops(forLoop)).toEqual([3])
    expect(loops(whileLoop)).toEqual([4])
    expect(loops(nestedLoop)).toEqual([3, 4])
    expect(loops(doWhileLoop)).toEqual([5]) // first body line; `do {` has no bytecode
  })

  it('for loop: iteration count and false check at exit', () => {
    const trace = parseTrace(forLoop.trace)
    const lastHeader = trace.steps.map((s) => s.line).lastIndexOf(3)
    const [loop] = activeLoops(trace, lastHeader)
    expect(loop).toMatchObject({ iterations: 3, lastCondition: false })
  })
})
