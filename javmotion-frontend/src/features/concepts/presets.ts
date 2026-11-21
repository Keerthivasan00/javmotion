import type { ConceptKind, ConceptRequest } from './conceptApi'

/** Example request used when a concept tab opens (and by the Playground demo picker). */
export const DEFAULT_REQUEST: { [K in ConceptKind]: Extract<ConceptRequest, { kind: K }> } = {
  'string-reverse': { kind: 'string-reverse', body: { input: 'code' } },
  array: { kind: 'array', body: { operation: 'BUBBLE_SORT', values: [5, 1, 4, 2] } },
  loops: { kind: 'loops', body: { type: 'FOR', start: 1, end: 3 } },
}

export interface Demo {
  id: string
  title: string
  request: ConceptRequest
}

/** Small set of ready-made programs for the Playground's empty visualization panel. */
export const DEMOS: Demo[] = [
  { id: 'for-loop', title: 'For loop', request: DEFAULT_REQUEST.loops },
  { id: 'bubble-sort', title: 'Array — bubble sort', request: DEFAULT_REQUEST.array },
  {
    id: 'binary-search',
    title: 'Array — binary search',
    request: {
      kind: 'array',
      body: { operation: 'BINARY_SEARCH', values: [1, 3, 4, 7, 9, 11], target: 9 },
    },
  },
  { id: 'string-reverse', title: 'String — reverse', request: DEFAULT_REQUEST['string-reverse'] },
  {
    id: 'nested-loop',
    title: 'Nested loops',
    request: { kind: 'loops', body: { type: 'NESTED', start: 1, end: 2 } },
  },
]
