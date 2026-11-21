import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useEditorStore } from '@/features/editor'
import { usePlaybackStore } from '@/features/playback'
import { LoopVisualizer } from '@/features/visualizers/loop/LoopVisualizer'
import { parseTrace } from '@/lib/trace/schema'
import ConceptsPage from '@/pages/ConceptsPage'
import bubbleSort from '@/test/fixtures/concepts/bubble-sort.json'
import doWhile from '@/test/fixtures/concepts/do-while-loop.json'
import forLoop from '@/test/fixtures/concepts/for-loop.json'
import stringReverse from '@/test/fixtures/concepts/string-reverse.json'
import { jsonResponse, renderWithProviders } from '@/test/renderWithProviders'
import { CodeView } from './CodeView'
import { fetchConcept } from './conceptApi'
import { DemoPicker } from './DemoPicker'
import { parseInts } from './parseInts'

const fetchMock = vi.fn<typeof fetch>()

/** Answers each concept endpoint with its recorded backend response. */
function backend(overrides: Record<string, () => Promise<Response>> = {}) {
  fetchMock.mockImplementation((input) => {
    const url = String(input)
    for (const [path, respond] of Object.entries(overrides))
      if (url.endsWith(path)) return respond()
    if (url.endsWith('/visualizer/array')) return jsonResponse(bubbleSort)
    if (url.endsWith('/visualizer/loops')) return jsonResponse(forLoop)
    if (url.endsWith('/visualizer/string-reverse')) return jsonResponse(stringReverse)
    return jsonResponse({ status: 404 }, 404)
  })
}

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  usePlaybackStore.getState().clear()
})
afterEach(() => {
  vi.unstubAllGlobals()
  fetchMock.mockReset()
})

const renderPage = (path: string) =>
  renderWithProviders(<ConceptsPage />, { route: '/concepts/:kind?', path })

describe('fetchConcept', () => {
  it('POSTs the body and returns a Zod-validated trace', async () => {
    backend()
    const result = await fetchConcept({ kind: 'string-reverse', body: { input: 'code' } })
    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toMatch(/\/visualizer\/string-reverse$/)
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({ input: 'code' })
    expect(result.trace.steps.at(-1)?.stdout).toBe('edoc\n')
    expect(result.code).toContain('class StringReverse')
  })

  it('rejects a response whose trace breaks the schema', async () => {
    backend({ '/visualizer/loops': () => jsonResponse({ ...forLoop, trace: { steps: 'nope' } }) })
    await expect(
      fetchConcept({ kind: 'loops', body: { type: 'FOR', start: 1, end: 3 } }),
    ).rejects.toThrow()
  })
})

describe('ConceptsPage', () => {
  it('redirects /concepts to the String tab', async () => {
    backend()
    renderPage('/concepts')
    expect(await screen.findByText('Reverse "code"')).toBeInTheDocument()
  })

  it('loads the default example, shows code and plays it', async () => {
    backend()
    renderPage('/concepts/array')
    expect(await screen.findByText('Bubble sort')).toBeInTheDocument()
    expect(screen.getByLabelText('Program code')).toHaveTextContent('class BubbleSort')
    expect(screen.getByRole('tab', { name: /array/i })).toHaveAttribute('aria-selected', 'true')

    // Current playback line is highlighted in the code view and follows stepping.
    expect(document.querySelector('[aria-current="step"]')).toHaveAttribute('data-line', '3')
    fireEvent.click(screen.getByRole('button', { name: /next step/i }))
    expect(document.querySelector('[aria-current="step"]')).toHaveAttribute('data-line', '4')
  })

  it('submits the form with the entered values', async () => {
    backend()
    renderPage('/concepts/array')
    await screen.findByText('Bubble sort')
    fireEvent.change(screen.getByLabelText(/values/i), { target: { value: '9 8, 7' } })
    fireEvent.click(screen.getByRole('button', { name: /visualize/i }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(JSON.parse(String(fetchMock.mock.calls[1][1]?.body))).toEqual({
      operation: 'BUBBLE_SORT',
      values: [9, 8, 7],
    })
  })

  it('shows server field errors next to the field', async () => {
    backend()
    renderPage('/concepts/string-reverse')
    await screen.findByText('Reverse "code"')
    backend({
      '/visualizer/string-reverse': () =>
        jsonResponse(
          {
            status: 400,
            code: 'VALIDATION_FAILED',
            errors: { input: 'must contain printable ASCII characters only' },
          },
          400,
        ),
    })
    fireEvent.click(screen.getByRole('button', { name: /visualize/i }))
    expect(
      await screen.findByText('must contain printable ASCII characters only'),
    ).toBeInTheDocument()
  })

  it('explains when the server is unreachable', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    renderPage('/concepts/loops')
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach the server/i)
  })

  it('rejects unparsable array input without calling the server', async () => {
    backend()
    renderPage('/concepts/array')
    await screen.findByText('Bubble sort')
    fireEvent.change(screen.getByLabelText(/values/i), { target: { value: '1, two' } })
    fireEvent.click(screen.getByRole('button', { name: /visualize/i }))
    expect(screen.getByRole('alert')).toHaveTextContent(/whole numbers/i)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('DemoPicker', () => {
  it('loads demo code into the editor and its trace into playback', async () => {
    backend()
    renderWithProviders(<DemoPicker />)
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'bubble-sort' } })
    await waitFor(() => expect(usePlaybackStore.getState().trace).not.toBeNull())
    expect(useEditorStore.getState().source).toContain('class BubbleSort')
  })
})

describe('small pieces', () => {
  it('parseInts accepts commas/spaces and rejects non-integers', () => {
    expect(parseInts('5, 1 4,2')).toEqual([5, 1, 4, 2])
    expect(parseInts('-3')).toEqual([-3])
    expect(parseInts('1.5')).toBeNull()
    expect(parseInts('')).toBeNull()
  })

  it('CodeView marks the current line', () => {
    render(<CodeView code={'a\nb\nc\n'} currentLine={2} />)
    expect(document.querySelectorAll('[data-line]')).toHaveLength(3)
    expect(document.querySelector('[aria-current="step"]')).toHaveTextContent('b')
  })

  it('LoopVisualizer reads the do-while condition from the bottom line', () => {
    const trace = parseTrace(doWhile.trace)
    const lines = trace.steps.map((s) => s.line)
    const firstCheck = lines.indexOf(7)
    const lastCheck = lines.lastIndexOf(7)
    const props = (index: number) => ({
      trace,
      index,
      step: trace.steps[index],
      prev: trace.steps[index - 1],
      source: doWhile.code,
    })

    const first = render(<LoopVisualizer {...props(firstCheck)} />)
    expect(screen.getByText('do-while · line 5')).toBeInTheDocument()
    expect(screen.getByText('i <= 3')).toBeInTheDocument()
    expect(screen.getByTestId('condition-badge')).toHaveTextContent('true')

    // Fresh render: AnimatePresence keeps the old badge mounted during its exit animation.
    first.unmount()
    render(<LoopVisualizer {...props(lastCheck)} />)
    expect(screen.getByTestId('condition-badge')).toHaveTextContent('false')
    expect(screen.getByText(/loop ends here/i)).toBeInTheDocument()
  })
})
