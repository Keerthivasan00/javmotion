import type { ComponentType } from 'react'
import type { LucideIcon } from 'lucide-react'
import type { Trace, TraceStep } from '@/lib/trace/types'

/** Every visualizer is a pure function of these props — no timers, no fetches. */
export interface VisualizerProps {
  trace: Trace
  index: number
  step: TraceStep
  prev?: TraceStep
  /** Program source, when known (used for loop headers). */
  source?: string
}

export type VisualizerId = 'variables' | 'array' | 'string' | 'loop' | 'callstack'

/**
 * `applies` and `priority` are evaluated per TRACE (not per step), so the tab set and the
 * auto-selected tab stay stable while stepping. A visualizer with nothing to show at the
 * current step renders its own small empty message.
 */
export interface VisualizerDef {
  id: VisualizerId
  label: string
  icon: LucideIcon
  applies: (trace: Trace) => boolean
  priority: (trace: Trace) => number
  component: ComponentType<VisualizerProps>
}
