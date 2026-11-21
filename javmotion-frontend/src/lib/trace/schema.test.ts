import { describe, expect, it } from 'vitest'
import { ZodError } from 'zod'
import bubbleSort from '@/test/fixtures/traces/array-bubble-sort.json'
import forLoop from '@/test/fixtures/traces/for-loop.json'
import stringReverse from '@/test/fixtures/traces/string-reverse.json'
import { parseTrace, TraceVersionError } from './schema'

describe('parseTrace', () => {
  it.each([
    ['for-loop', forLoop],
    ['array-bubble-sort', bubbleSort],
    ['string-reverse', stringReverse],
  ])('accepts fixture %s', (_, json) => {
    const trace = parseTrace(json)
    expect(trace.steps.length).toBeGreaterThan(0)
    trace.steps.forEach((step, i) => expect(step.index).toBe(i))
    expect(trace.steps.at(-1)?.event).toBe('END')
  })

  it('accepts null optional fields (backend omits or nulls them)', () => {
    const json = structuredClone(forLoop) as Record<string, unknown> & { steps: object[] }
    json.steps[0] = { ...json.steps[0], returnValue: null, exception: null, highlights: null }
    const trace = parseTrace(json)
    expect(trace.steps[0].highlights).toBeUndefined()
  })

  it('rejects a schemaVersion mismatch with TraceVersionError', () => {
    expect(() => parseTrace({ ...forLoop, schemaVersion: 2 })).toThrow(TraceVersionError)
  })

  it('rejects unknown value kinds', () => {
    const json = structuredClone(forLoop)
    ;(json.steps[1].stack[0].locals as Record<string, unknown>).sum = { kind: 'WEIRD' }
    expect(() => parseTrace(json)).toThrow(ZodError)
  })

  it('rejects non-objects', () => {
    expect(() => parseTrace(null)).toThrow(ZodError)
    expect(() => parseTrace('trace')).toThrow(ZodError)
  })
})
