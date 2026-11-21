import { useEffect, useState } from 'react'

/**
 * True once `active` has stayed true for `delayMs`. Used for the "Waking up server…" hint:
 * the free API host sleeps and the first request can take ~30 s.
 */
export function useDelayedFlag(active: boolean, delayMs = 3000): boolean {
  const [flag, setFlag] = useState(false)

  useEffect(() => {
    if (!active) return
    const id = window.setTimeout(() => setFlag(true), delayMs)
    return () => {
      window.clearTimeout(id)
      setFlag(false)
    }
  }, [active, delayMs])

  return active && flag
}
