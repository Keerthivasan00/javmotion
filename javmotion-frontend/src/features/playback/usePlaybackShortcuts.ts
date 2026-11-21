import { useEffect } from 'react'
import { usePlaybackStore } from './playbackStore'

/**
 * F10 next step · Shift+F10 previous step · F5 continue to breakpoint.
 * Active only while a trace is loaded (so F5 still reloads the page otherwise).
 */
export function usePlaybackShortcuts() {
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const s = usePlaybackStore.getState()
      if (!s.trace || e.ctrlKey || e.metaKey || e.altKey) return

      if (e.key === 'F10') {
        e.preventDefault()
        if (e.shiftKey) s.prev()
        else {
          s.pause()
          s.next()
        }
      } else if (e.key === 'F5' && !e.shiftKey) {
        e.preventDefault()
        s.continueToBreakpoint()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])
}
