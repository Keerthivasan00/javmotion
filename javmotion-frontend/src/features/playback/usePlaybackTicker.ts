import { useEffect } from 'react'
import { BASE_INTERVAL_MS, usePlaybackStore } from './playbackStore'

/**
 * Advances one step every BASE_INTERVAL_MS / speed while playing.
 * Pauses before a step whose line has a breakpoint, and at the end.
 * Mount once (in the visualization panel).
 */
export function usePlaybackTicker() {
  const playing = usePlaybackStore((s) => s.playing)
  const speed = usePlaybackStore((s) => s.speed)
  const index = usePlaybackStore((s) => s.index)

  useEffect(() => {
    if (!playing) return
    const id = window.setTimeout(() => {
      const { trace, index: i, breakpoints, next, pause } = usePlaybackStore.getState()
      const upcoming = trace?.steps[i + 1]
      if (!upcoming) return pause()
      next()
      if (breakpoints.has(upcoming.line)) pause()
    }, BASE_INTERVAL_MS / speed)
    return () => window.clearTimeout(id)
  }, [playing, speed, index])
}
