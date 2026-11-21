/**
 * TypeScript mirror of the backend trace schema.
 * Source of truth: javmotion-backend docs/architecture/backend-architecture.md §6
 * and javmotion-common/.../trace. Bump TRACE_SCHEMA_VERSION on both sides together.
 */
export const TRACE_SCHEMA_VERSION = 1

export type Value =
  | { kind: 'PRIMITIVE'; type: string; value: number | boolean | string }
  | { kind: 'REF'; ref: string }
  | { kind: 'NULL' }

export type HeapObject =
  | { kind: 'STRING'; type: string; value: string; interned?: boolean }
  | { kind: 'ARRAY'; type: string; elements: Value[]; length?: number }
  | { kind: 'OBJECT'; type: string; fields: Record<string, Value> }
  | { kind: 'LIST'; type: string; elements: Value[] }
  | { kind: 'MAP'; type: string; entries: { key: Value; value: Value }[] }

export interface Frame {
  id: string
  method: string
  className: string
  line: number
  locals: Record<string, Value>
}

export type EventType = 'LINE' | 'CALL' | 'RETURN' | 'EXCEPTION' | 'OUTPUT' | 'END'

export interface Highlight {
  ref: string
  index?: number
  role: 'compare' | 'swap' | 'active' | 'found' | 'pointer'
  label?: string
}

export interface TraceStep {
  index: number
  event: EventType
  line: number
  /** Top of stack = last element. */
  stack: Frame[]
  heap: Record<string, HeapObject>
  /** Cumulative stdout up to and including this step. */
  stdout: string
  returnValue?: Value | null
  exception?: { type: string; message: string } | null
  highlights?: Highlight[]
}

export interface Trace {
  schemaVersion: typeof TRACE_SCHEMA_VERSION
  source: 'JDI' | 'CONCEPT'
  language: 'java'
  truncated: boolean
  steps: TraceStep[]
}
