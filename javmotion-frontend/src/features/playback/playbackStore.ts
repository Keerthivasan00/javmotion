import { create } from 'zustand'
import type { Trace } from '@/lib/trace/types'

export const SPEEDS = [0.25, 0.5, 1, 2, 4] as const
export type Speed = (typeof SPEEDS)[number]

/** Base delay between steps at 1× speed. */
export const BASE_INTERVAL_MS = 800

interface PlaybackState {
  trace: Trace | null
  index: number
  playing: boolean
  speed: Speed
  /** Line numbers. Replaced (never mutated) so selectors notice changes. */
  breakpoints: ReadonlySet<number>

  load: (trace: Trace) => void
  clear: () => void
  play: () => void
  pause: () => void
  togglePlay: () => void
  next: () => void
  prev: () => void
  seek: (index: number) => void
  reset: () => void
  /** Jump to the next step whose line has a breakpoint (or the last step). */
  continueToBreakpoint: () => void
  toggleBreakpoint: (line: number) => void
  setSpeed: (speed: Speed) => void
}

const lastIndex = (trace: Trace | null) => (trace ? trace.steps.length - 1 : 0)
const clamp = (i: number, trace: Trace | null) => Math.min(Math.max(0, i), lastIndex(trace))

export const usePlaybackStore = create<PlaybackState>((set, get) => ({
  trace: null,
  index: 0,
  playing: false,
  speed: 1,
  breakpoints: new Set(),

  load: (trace) => set({ trace, index: 0, playing: false }),
  clear: () => set({ trace: null, index: 0, playing: false }),

  play: () => {
    const { trace, index } = get()
    if (!trace || trace.steps.length === 0) return
    // Pressing play at the end restarts from the beginning.
    set({ playing: true, index: index >= lastIndex(trace) ? 0 : index })
  },
  pause: () => set({ playing: false }),
  togglePlay: () => (get().playing ? get().pause() : get().play()),

  next: () => {
    const { trace, index } = get()
    if (!trace) return
    const nextIndex = clamp(index + 1, trace)
    set({ index: nextIndex, playing: get().playing && nextIndex < lastIndex(trace) })
  },
  prev: () => set((s) => ({ index: clamp(s.index - 1, s.trace), playing: false })),
  seek: (i) => set((s) => ({ index: clamp(i, s.trace) })),
  reset: () => set({ index: 0, playing: false }),

  continueToBreakpoint: () => {
    const { trace, index, breakpoints } = get()
    if (!trace) return
    const steps = trace.steps
    let target = steps.length - 1
    for (let i = index + 1; i < steps.length; i++) {
      if (breakpoints.has(steps[i].line)) {
        target = i
        break
      }
    }
    set({ index: target, playing: false })
  },

  toggleBreakpoint: (line) =>
    set((s) => {
      const breakpoints = new Set(s.breakpoints)
      if (breakpoints.has(line)) breakpoints.delete(line)
      else breakpoints.add(line)
      return { breakpoints }
    }),

  setSpeed: (speed) => set({ speed }),
}))
