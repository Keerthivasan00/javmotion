import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { parseTrace } from '@/lib/trace/schema'
import type { Trace } from '@/lib/trace/types'

/** Mirrors backend `/visualizer/*` request records (backend doc §7.2). */
export type ArrayOperation = 'BUBBLE_SORT' | 'SELECTION_SORT' | 'LINEAR_SEARCH' | 'BINARY_SEARCH'
export type LoopType = 'FOR' | 'WHILE' | 'DO_WHILE' | 'NESTED'

export type ConceptRequest =
  | { kind: 'string-reverse'; body: { input: string } }
  | { kind: 'array'; body: { operation: ArrayOperation; values: number[]; target?: number } }
  | { kind: 'loops'; body: { type: LoopType; start: number; end: number; step?: number } }

export type ConceptKind = ConceptRequest['kind']

export interface ConceptResult {
  key: string
  title: string
  /** Java program the trace's line numbers refer to. */
  code: string
  trace: Trace
}

/** Input limits — same as the backend validation, used for client-side hints. */
export const LIMITS = {
  stringMax: 20,
  arrayMax: 12,
  valueMin: -999,
  valueMax: 999,
  loopMin: -100,
  loopMax: 100,
  stepMax: 10,
} as const

export async function fetchConcept(request: ConceptRequest): Promise<ConceptResult> {
  const res = await api<{ key: string; title: string; code: string; trace: unknown }>(
    `/visualizer/${request.kind}`,
    { method: 'POST', body: request.body },
  )
  // Response is validated like any trace: the frontend never trusts the shape blindly.
  return { key: res.key, title: res.title, code: res.code, trace: parseTrace(res.trace) }
}

export function useConceptTrace() {
  return useMutation({ mutationFn: fetchConcept, retry: false })
}
