import { z } from 'zod'
import { TRACE_SCHEMA_VERSION, type Trace } from './types'

/**
 * Runtime validation for traces coming from the API (untrusted input).
 * Mirrors types.ts; the compile-time check at the bottom keeps them in sync.
 * Backend omits null fields (Jackson NON_NULL), so optional fields accept missing or null.
 */

export const valueSchema = z.discriminatedUnion('kind', [
  z.object({
    kind: z.literal('PRIMITIVE'),
    type: z.string(),
    value: z.union([z.number(), z.boolean(), z.string()]),
  }),
  z.object({ kind: z.literal('REF'), ref: z.string() }),
  z.object({ kind: z.literal('NULL') }),
])

const optional = <T extends z.ZodType>(schema: T) =>
  schema.nullish().transform((v) => v ?? undefined)

export const heapObjectSchema = z.discriminatedUnion('kind', [
  z.object({
    kind: z.literal('STRING'),
    type: z.string(),
    value: z.string(),
    interned: optional(z.boolean()),
  }),
  z.object({
    kind: z.literal('ARRAY'),
    type: z.string(),
    elements: z.array(valueSchema),
    length: optional(z.number().int().nonnegative()),
  }),
  z.object({
    kind: z.literal('OBJECT'),
    type: z.string(),
    fields: z.record(z.string(), valueSchema),
  }),
  z.object({ kind: z.literal('LIST'), type: z.string(), elements: z.array(valueSchema) }),
  z.object({
    kind: z.literal('MAP'),
    type: z.string(),
    entries: z.array(z.object({ key: valueSchema, value: valueSchema })),
  }),
])

export const frameSchema = z.object({
  id: z.string(),
  method: z.string(),
  className: z.string(),
  line: z.number().int(),
  locals: z.record(z.string(), valueSchema),
})

export const highlightSchema = z.object({
  ref: z.string(),
  index: optional(z.number().int()),
  role: z.enum(['compare', 'swap', 'active', 'found', 'pointer']),
  label: optional(z.string()),
})

export const traceStepSchema = z.object({
  index: z.number().int().nonnegative(),
  event: z.enum(['LINE', 'CALL', 'RETURN', 'EXCEPTION', 'OUTPUT', 'END']),
  line: z.number().int(),
  stack: z.array(frameSchema),
  heap: z.record(z.string(), heapObjectSchema),
  stdout: z.string(),
  returnValue: valueSchema.nullish(),
  exception: z.object({ type: z.string(), message: z.string() }).nullish(),
  highlights: optional(z.array(highlightSchema)),
})

export const traceSchema = z.object({
  schemaVersion: z.literal(TRACE_SCHEMA_VERSION),
  source: z.enum(['JDI', 'CONCEPT']),
  language: z.literal('java'),
  truncated: z.boolean(),
  steps: z.array(traceStepSchema),
})

export class TraceVersionError extends Error {
  readonly received: unknown
  constructor(received: unknown) {
    super(`Trace schema version ${String(received)} is not supported. Please refresh the page.`)
    this.name = 'TraceVersionError'
    this.received = received
  }
}

/**
 * Validates unknown JSON as a Trace.
 * Throws TraceVersionError on schemaVersion mismatch (UI shows "please refresh"),
 * or ZodError for any other shape problem.
 */
export function parseTrace(json: unknown): Trace {
  if (
    typeof json === 'object' &&
    json !== null &&
    'schemaVersion' in json &&
    json.schemaVersion !== TRACE_SCHEMA_VERSION
  ) {
    throw new TraceVersionError(json.schemaVersion)
  }
  return traceSchema.parse(json)
}

// Compile-time guard: parsed output must be assignable to the hand-written Trace type.
type Assert<T extends true> = T
export type _SchemaMatchesTypes = Assert<z.output<typeof traceSchema> extends Trace ? true : false>
