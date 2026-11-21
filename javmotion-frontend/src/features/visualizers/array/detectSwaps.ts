import type { Value } from '@/lib/trace/types'
import { primitiveText } from '../shared/traceUtils'

/** Cell width + gap in px; swap animation slides a cell by this many px per index. */
export const CELL_STEP = 48

export const sameText = (a: Value | undefined, b: Value) =>
  !!a && primitiveText(a) === primitiveText(b)

/**
 * Finds pairwise swaps between two snapshots: index → pixel offset it moved from.
 */
export function detectSwaps(prev: Value[] | undefined, now: Value[]): Map<number, number> {
  const moves = new Map<number, number>()
  if (!prev || prev.length !== now.length) return moves
  for (let a = 0; a < now.length; a++) {
    if (moves.has(a) || sameText(prev[a], now[a])) continue
    for (let b = a + 1; b < now.length; b++) {
      if (sameText(prev[a], now[b]) && sameText(prev[b], now[a]) && !sameText(prev[b], now[b])) {
        moves.set(a, (b - a) * CELL_STEP)
        moves.set(b, (a - b) * CELL_STEP)
        break
      }
    }
  }
  return moves
}
