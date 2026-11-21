import { describe, expect, it } from 'vitest'
import { parseTrace } from '@/lib/trace/schema'
import bubbleJson from '@/test/fixtures/traces/array-bubble-sort.json'
import forLoopJson from '@/test/fixtures/traces/for-loop.json'
import stringJson from '@/test/fixtures/traces/string-reverse.json'
import { activeLoops, findLoops, parseLoopHeader } from './loopAnalysis'

const forLoop = parseTrace(forLoopJson)
const bubble = parseTrace(bubbleJson)
const strRev = parseTrace(stringJson)
const stepAt = (t: typeof forLoop, line: number, nth = 0) =>
  t.steps.map((s, i) => [s.line, i] as const).filter(([l]) => l === line)[nth][1]

describe('findLoops', () => {
  it('finds header and body range from back-edges', () => {
    expect(findLoops(forLoop)).toEqual([{ header: 4, end: 6 }])
    expect(findLoops(strRev)).toEqual([{ header: 5, end: 6 }])
    expect(findLoops(bubble)).toEqual([
      { header: 5, end: 9 },
      { header: 6, end: 9 },
    ])
  })

  it('is memoized per trace', () => {
    expect(findLoops(forLoop)).toBe(findLoops(forLoop))
  })
})

describe('activeLoops', () => {
  it('reports no loop before the loop starts or after it ends', () => {
    expect(activeLoops(forLoop, 0)).toEqual([])
    expect(activeLoops(forLoop, stepAt(forLoop, 8))).toEqual([])
  })

  it('counts iterations and condition results', () => {
    const firstBody = stepAt(forLoop, 5, 0)
    const [loop] = activeLoops(forLoop, firstBody)
    expect(loop).toMatchObject({ header: 4, iterations: 1, atHeader: false, lastCondition: true })

    const exitCheck = stepAt(forLoop, 4, 3)
    const [done] = activeLoops(forLoop, exitCheck)
    expect(done).toMatchObject({ iterations: 3, atHeader: true, lastCondition: false })
    expect(done.history.map((h) => h.condition)).toEqual([true, true, true, false])
    expect(done.loopVars).toContain('i')
  })

  it('tracks nested loops, resetting the inner count each outer iteration', () => {
    const secondOuterInner = stepAt(bubble, 6, 4) // first inner check of outer iteration 2
    const loops = activeLoops(bubble, secondOuterInner)
    expect(loops.map((l) => l.header)).toEqual([5, 6])
    expect(loops[0].iterations).toBe(2)
    expect(loops[1].iterations).toBe(1)
  })
})

describe('parseLoopHeader', () => {
  it('splits for / for-each / while headers', () => {
    expect(parseLoopHeader('  for (int i = 0; i < n; i++) {')).toEqual({
      kind: 'for',
      init: 'int i = 0',
      condition: 'i < n',
      update: 'i++',
    })
    expect(parseLoopHeader('for (int x : arr) {')).toMatchObject({
      kind: 'for-each',
      init: 'int x',
    })
    expect(parseLoopHeader('while (lo <= hi) {')).toEqual({ kind: 'while', condition: 'lo <= hi' })
    expect(parseLoopHeader('} while (x > 0);')).toEqual({ kind: 'while', condition: 'x > 0' })
    expect(parseLoopHeader(undefined)).toBeNull()
  })
})
