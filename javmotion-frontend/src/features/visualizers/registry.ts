import { Braces, Brackets, Layers, Repeat, Type } from 'lucide-react'
import type { Trace } from '@/lib/trace/types'
import { ArrayVisualizer } from './array/ArrayVisualizer'
import { CallStack } from './callstack/CallStack'
import { findLoops } from './loop/loopAnalysis'
import { LoopVisualizer } from './loop/LoopVisualizer'
import { memoByTrace, traceHasLocalKind } from './shared/traceUtils'
import { StringVisualizer } from './string/StringVisualizer'
import type { VisualizerDef } from './types'
import { VariableInspector } from './variables/VariableInspector'

const hasArrays = traceHasLocalKind('ARRAY')
const hasStrings = traceHasLocalKind('STRING')
const hasLoops = (t: Trace) => findLoops(t).length > 0
const maxDepth = memoByTrace((t) => Math.max(0, ...t.steps.map((s) => s.stack.length)))

/** Tab order = array order. Memory view joins in Step 8. */
export const VISUALIZERS: VisualizerDef[] = [
  {
    id: 'variables',
    label: 'Variables',
    icon: Braces,
    applies: () => true,
    priority: () => 20,
    component: VariableInspector,
  },
  {
    id: 'array',
    label: 'Array',
    icon: Brackets,
    applies: hasArrays,
    priority: () => 40,
    component: ArrayVisualizer,
  },
  {
    id: 'string',
    label: 'String',
    icon: Type,
    applies: hasStrings,
    priority: () => 35,
    component: StringVisualizer,
  },
  {
    id: 'loop',
    label: 'Loop',
    icon: Repeat,
    applies: hasLoops,
    priority: () => 30,
    component: LoopVisualizer,
  },
  {
    id: 'callstack',
    label: 'Call Stack',
    icon: Layers,
    applies: () => true,
    // Worth opening first only for programs with real method calls (e.g. recursion).
    priority: (t) => (maxDepth(t) > 1 ? 45 : 10),
    component: CallStack,
  },
]

export function applicableVisualizers(trace: Trace): VisualizerDef[] {
  return VISUALIZERS.filter((v) => v.applies(trace))
}

export function defaultVisualizer(trace: Trace): VisualizerDef {
  return applicableVisualizers(trace).reduce((best, v) =>
    v.priority(trace) > best.priority(trace) ? v : best,
  )
}
