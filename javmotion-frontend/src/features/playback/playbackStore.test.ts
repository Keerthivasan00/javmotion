import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { parseTrace } from '@/lib/trace/schema'
import forLoop from '@/test/fixtures/traces/for-loop.json'
import { BASE_INTERVAL_MS, usePlaybackStore } from './playbackStore'
import {
  changedLocals,
  selectCurrentStdout,
  selectCurrentStep,
  selectPreviousStep,
} from './selectors'
import { usePlaybackTicker } from './usePlaybackTicker'

const trace = parseTrace(forLoop)
const last = trace.steps.length - 1
const store = () => usePlaybackStore.getState()

beforeEach(() => {
  usePlaybackStore.setState({
    trace: null,
    index: 0,
    playing: false,
    speed: 1,
    breakpoints: new Set(),
  })
  store().load(trace)
})

describe('playbackStore navigation', () => {
  it('load resets index and playing', () => {
    usePlaybackStore.setState({ index: 5, playing: true })
    store().load(trace)
    expect(store()).toMatchObject({ index: 0, playing: false })
  })

  it('next / prev move one step and clamp at the ends', () => {
    store().prev()
    expect(store().index).toBe(0)
    store().next()
    store().next()
    expect(store().index).toBe(2)
    store().seek(last)
    store().next()
    expect(store().index).toBe(last)
    store().prev()
    expect(store().index).toBe(last - 1)
  })

  it('seek clamps out-of-range values', () => {
    store().seek(-3)
    expect(store().index).toBe(0)
    store().seek(9999)
    expect(store().index).toBe(last)
  })

  it('next pauses when reaching the last step', () => {
    usePlaybackStore.setState({ index: last - 1, playing: true })
    store().next()
    expect(store()).toMatchObject({ index: last, playing: false })
  })

  it('play at the end restarts from the beginning', () => {
    store().seek(last)
    store().play()
    expect(store()).toMatchObject({ index: 0, playing: true })
  })

  it('prev and reset pause playback', () => {
    usePlaybackStore.setState({ index: 3, playing: true })
    store().prev()
    expect(store().playing).toBe(false)
    usePlaybackStore.setState({ playing: true })
    store().reset()
    expect(store()).toMatchObject({ index: 0, playing: false })
  })

  it('does nothing without a trace', () => {
    store().clear()
    store().next()
    store().play()
    expect(store()).toMatchObject({ index: 0, playing: false })
  })
})

describe('breakpoints', () => {
  it('toggleBreakpoint adds and removes, replacing the Set', () => {
    const before = store().breakpoints
    store().toggleBreakpoint(6)
    expect(store().breakpoints).not.toBe(before)
    expect(store().breakpoints.has(6)).toBe(true)
    store().toggleBreakpoint(6)
    expect(store().breakpoints.has(6)).toBe(false)
  })

  it('continueToBreakpoint jumps to the next matching line, else to the end', () => {
    store().toggleBreakpoint(6)
    const lines = trace.steps.map((s) => s.line)
    const first = lines.indexOf(6)
    const second = lines.indexOf(6, first + 1)

    store().continueToBreakpoint()
    expect(store().index).toBe(first)
    store().continueToBreakpoint()
    expect(store().index).toBe(second)

    store().toggleBreakpoint(6)
    store().continueToBreakpoint()
    expect(store().index).toBe(last)
  })
})

describe('selectors', () => {
  it('current / previous step and stdout follow the index', () => {
    expect(selectPreviousStep(store())).toBeUndefined()
    store().seek(last)
    expect(selectCurrentStep(store())).toBe(trace.steps[last])
    expect(selectPreviousStep(store())).toBe(trace.steps[last - 1])
    expect(selectCurrentStdout(store())).toBe('sum = 1\nsum = 3\nsum = 6\ndone\n')
  })

  it('changedLocals reports new and changed variables only', () => {
    const [s0, s1, s2, s3] = trace.steps
    expect(changedLocals(s0, undefined)).toEqual(new Set(['args']))
    expect(changedLocals(s1, s0)).toEqual(new Set(['sum'])) // sum declared
    expect(changedLocals(s2, s1)).toEqual(new Set(['i'])) // loop var appears
    expect(changedLocals(s3, s2)).toEqual(new Set(['sum'])) // sum += i
  })

  it('changedLocals is empty at END (no frame)', () => {
    expect(changedLocals(trace.steps[last], trace.steps[last - 1]).size).toBe(0)
  })
})

describe('usePlaybackTicker', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('advances on each interval and respects speed', () => {
    renderHook(() => usePlaybackTicker())
    act(() => store().play())
    act(() => vi.advanceTimersByTime(BASE_INTERVAL_MS))
    expect(store().index).toBe(1)

    act(() => store().setSpeed(4))
    act(() => vi.advanceTimersByTime(BASE_INTERVAL_MS / 4))
    expect(store().index).toBe(2)
  })

  it('pauses on a breakpoint line', () => {
    store().toggleBreakpoint(5)
    const target = trace.steps.findIndex((s) => s.line === 5)
    renderHook(() => usePlaybackTicker())
    act(() => store().play())
    for (let i = 0; i < target; i++) act(() => vi.advanceTimersByTime(BASE_INTERVAL_MS))
    expect(store()).toMatchObject({ index: target, playing: false })
  })

  it('stops at the end', () => {
    renderHook(() => usePlaybackTicker())
    act(() => store().play())
    for (let i = 0; i < last + 3; i++) act(() => vi.advanceTimersByTime(BASE_INTERVAL_MS))
    expect(store()).toMatchObject({ index: last, playing: false })
  })
})
