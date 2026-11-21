/** "5, 1 4,2" → [5, 1, 4, 2]; null if anything is not an integer. */
export function parseInts(text: string): number[] | null {
  const parts = text.split(/[\s,]+/).filter(Boolean)
  if (parts.length === 0 || parts.some((p) => !/^-?\d+$/.test(p))) return null
  return parts.map(Number)
}
