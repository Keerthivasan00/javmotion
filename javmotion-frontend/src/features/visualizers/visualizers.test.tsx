import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { DEMO_TRACES, usePlaybackStore } from '@/features/playback'
import { parseTrace } from '@/lib/trace/schema'
import type { Trace } from '@/lib/trace/types'
import bubbleJson from '@/test/fixtures/traces/array-bubble-sort.json'
import forLoopJson from '@/test/fixtures/traces/for-loop.json'
import stringJson from '@/test/fixtures/traces/string-reverse.json'
import { ArrayVisualizer } from './array/ArrayVisualizer'
import { detectSwaps } from './array/detectSwaps'
import { CallStack } from './callstack/CallStack'
import { LoopVisualizer } from './loop/LoopVisualizer'
import { applicableVisualizers, defaultVisualizer } from './registry'
import { StringVisualizer } from './string/StringVisualizer'
import type { VisualizerProps } from './types'
import { VariableInspector } from './variables/VariableInspector'
import { VisualizationPanel } from './VisualizationPanel'

const forLoop = parseTrace(forLoopJson)
const bubble = parseTrace(bubbleJson)
const strRev = parseTrace(stringJson)
const codeOf = (id: string) => DEMO_TRACES.find((d) => d.id === id)!.code

const props = (trace: Trace, index: number, source?: string): VisualizerProps => ({
  trace,
  index,
  step: trace.steps[index],
  prev: index > 0 ? trace.steps[index - 1] : undefined,
  source,
})

const cellTexts = (name: string) =>
  within(screen.getByRole('list', { name }))
    .getAllByRole('listitem')
    .map((li) => li.firstElementChild!.textContent)

describe('registry', () => {
  it('shows tabs that apply to the trace and auto-selects the most relevant', () => {
    expect(applicableVisualizers(bubble).map((v) => v.id)).toEqual([
      'variables',
      'array',
      'loop',
      'callstack',
    ])
    expect(defaultVisualizer(bubble).id).toBe('array')
    expect(defaultVisualizer(strRev).id).toBe('string')
    expect(defaultVisualizer(forLoop).id).toBe('loop')
  })
})

describe('VariableInspector', () => {
  it('lists top-frame locals and marks changed ones', () => {
    render(<VariableInspector {...props(forLoop, 3)} />) // after line 5: sum += i
    const sumRow = screen.getByText('sum').closest('tr')!
    expect(sumRow).toHaveAttribute('data-changed', 'true')
    expect(within(sumRow).getByText('1')).toBeInTheDocument()
    expect(screen.getByText('i').closest('tr')).not.toHaveAttribute('data-changed')
  })

  it('shows an empty message at END', () => {
    render(<VariableInspector {...props(forLoop, forLoop.steps.length - 1)} />)
    expect(screen.getByText(/program finished/i)).toBeInTheDocument()
  })
})

describe('ArrayVisualizer', () => {
  it('renders cells with compare highlights and the j pointer', () => {
    render(<ArrayVisualizer {...props(bubble, 3)} />)
    expect(cellTexts('Array arr')).toEqual(['5', '1', '4', '2'])
    const cells = screen.getByRole('list', { name: 'Array arr' }).querySelectorAll('[data-role]')
    expect([...cells].map((c) => c.getAttribute('data-role'))).toEqual(['compare', 'compare'])
    expect(screen.getByTestId('pointer-j')).toBeInTheDocument()
  })

  it('shows swapped values with swap highlight', () => {
    render(<ArrayVisualizer {...props(bubble, 4)} />)
    expect(cellTexts('Array arr')).toEqual(['1', '5', '4', '2'])
    expect(screen.getAllByText(/^[15]$/)[0].closest('[data-role]')).toHaveAttribute(
      'data-role',
      'swap',
    )
  })

  it('detectSwaps finds pairwise moves only', () => {
    const v = (n: number) => ({ kind: 'PRIMITIVE' as const, type: 'int', value: n })
    expect(detectSwaps([v(5), v(1), v(4)], [v(1), v(5), v(4)])).toEqual(
      new Map([
        [0, 48],
        [1, -48],
      ]),
    )
    expect(detectSwaps([v(5), v(1)], [v(7), v(1)]).size).toBe(0)
    expect(detectSwaps(undefined, [v(1)]).size).toBe(0)
  })
})

describe('StringVisualizer', () => {
  it('renders char tiles, the i pointer and a new-object badge after concatenation', () => {
    render(<StringVisualizer {...props(strRev, 4)} />)
    expect(cellTexts('String s')).toEqual(['c', 'o', 'd', 'e'])
    expect(cellTexts('String rev')).toEqual(['e'])
    expect(screen.getByTestId('pointer-i')).toBeInTheDocument()
    expect(screen.getByText('new String object')).toBeInTheDocument()
  })

  it('shows the empty string', () => {
    render(<StringVisualizer {...props(strRev, 2)} />)
    expect(screen.getByText('"" (empty)')).toBeInTheDocument()
  })
})

describe('LoopVisualizer', () => {
  it('shows header parts, iteration count and a false condition at loop exit', () => {
    const exitCheck = 10 // last visit of line 4
    render(<LoopVisualizer {...props(forLoop, exitCheck, codeOf('for-loop'))} />)
    expect(screen.getByText('i <= 3')).toBeInTheDocument()
    expect(screen.getByText('int i = 1')).toBeInTheDocument()
    expect(screen.getByTestId('iteration-count')).toHaveTextContent('3')
    expect(screen.getByTestId('condition-badge')).toHaveTextContent('false')
  })

  it('shows nested loops for bubble sort', () => {
    render(<LoopVisualizer {...props(bubble, 13, codeOf('array-bubble-sort'))} />)
    expect(screen.getByLabelText('Loop at line 5')).toBeInTheDocument()
    expect(screen.getByLabelText('Loop at line 6')).toBeInTheDocument()
  })

  it('says so when not inside a loop', () => {
    render(<LoopVisualizer {...props(forLoop, 0)} />)
    expect(screen.getByText(/not inside a loop/i)).toBeInTheDocument()
  })
})

describe('CallStack', () => {
  it('lists frames with the running one marked', () => {
    render(<CallStack {...props(forLoop, 2)} />)
    expect(screen.getByRole('button', { name: /Main\.main\(\)/ })).toHaveAttribute(
      'aria-current',
      'true',
    )
    expect(screen.getByText('running')).toBeInTheDocument()
  })
})

describe('VisualizationPanel', () => {
  beforeEach(() => usePlaybackStore.getState().load(bubble))

  it('auto-selects the Array tab and switches tabs on click', () => {
    render(<VisualizationPanel />)
    expect(screen.getByRole('tab', { name: /array/i })).toHaveAttribute('aria-selected', 'true')
    fireEvent.click(screen.getByRole('tab', { name: /variables/i }))
    expect(screen.getByRole('tab', { name: /variables/i })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('toolbar', { name: /playback controls/i })).toBeInTheDocument()
  })
})
