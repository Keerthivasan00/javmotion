import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ConsolePanel } from '@/features/console'
import { toMarkers, useEditorStore } from '@/features/editor'
import { usePlaybackStore } from '@/features/playback'
import { ApiError } from '@/lib/api/errors'
import { jsonResponse, renderWithProviders } from '@/test/renderWithProviders'
import type { ExecutionStatus } from './executionApi'
import { ExecutionStatusBar } from './ExecutionStatusBar'
import { RunControls } from './RunControls'
import { requestErrorMessage, statusSummary } from './runMessages'
import { useRunStore } from './runStore'

const fetchMock = vi.fn<typeof fetch>()
const ID = 'e1'
const TOKEN = 'tok-123'

function status(s: ExecutionStatus, extra: Record<string, unknown> = {}) {
  return {
    id: ID,
    mode: 'RUN' as const,
    status: s,
    exitCode: null,
    executionTimeMs: null,
    errorMessage: null,
    createdAt: '2026-11-24T10:00:00Z',
    startedAt: null,
    finishedAt: null,
    ...extra,
  }
}

/** Fake backend: POST creates, GET status walks through `statuses`, GET output returns `output`. */
function backend(statuses: ReturnType<typeof status>[], output: unknown) {
  let polls = 0
  fetchMock.mockImplementation((input, init) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    if (method === 'POST' && url.endsWith('/executions')) {
      return jsonResponse({ id: ID, accessToken: TOKEN, status: 'QUEUED' }, 202)
    }
    if (method === 'DELETE') return Promise.resolve(new Response(null, { status: 204 }))
    if (url.endsWith(`/executions/${ID}/output`)) return jsonResponse(output)
    if (url.endsWith(`/executions/${ID}`)) {
      return jsonResponse(statuses[Math.min(polls++, statuses.length - 1)])
    }
    return jsonResponse({ status: 404 }, 404)
  })
}

const renderPlayground = () =>
  renderWithProviders(
    <>
      <RunControls />
      <ExecutionStatusBar />
      <ConsolePanel />
    </>,
  )

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  useRunStore.setState({ run: null, submitting: false, submitError: null })
  useEditorStore.getState().reset()
  usePlaybackStore.getState().clear()
})
afterEach(() => {
  vi.unstubAllGlobals()
  fetchMock.mockReset()
})

describe('run flow', () => {
  it('submits, polls with the token and shows output', async () => {
    backend([status('RUNNING'), status('COMPLETED', { exitCode: 0, executionTimeMs: 42 })], {
      id: ID,
      status: 'COMPLETED',
      stdout: 'Vanakkam\n',
      stderr: '',
      compileErrors: [],
    })
    renderPlayground()
    fireEvent.click(screen.getByRole('button', { name: /run/i }))

    expect(await screen.findByText('Finished · 42 ms', {}, { timeout: 3000 })).toBeInTheDocument()
    // Output is fetched once the status is terminal.
    expect(await screen.findByText(/Vanakkam/)).toBeInTheDocument()

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(String(post[1]?.body))).toMatchObject({ mode: 'RUN', language: 'java' })
    const poll = fetchMock.mock.calls.find(([url]) => String(url).endsWith(`/executions/${ID}`))!
    expect(new Headers(poll[1]?.headers).get('X-Execution-Token')).toBe(TOKEN)
  })

  it('opens the Errors tab with positions on a compile error', async () => {
    backend([status('COMPILE_ERROR')], {
      id: ID,
      status: 'COMPILE_ERROR',
      stdout: '',
      stderr: 'Main.java:3: error',
      compileErrors: [{ line: 3, column: 14, message: "';' expected" }],
    })
    renderPlayground()
    fireEvent.click(screen.getByRole('button', { name: /run/i }))

    expect(await screen.findByText('Compilation failed', {}, { timeout: 3000 })).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByRole('tab', { name: /errors/i })).toHaveAttribute('aria-selected', 'true'),
    )
    expect(screen.getByText('line 3:14')).toBeInTheDocument()
    expect(screen.getByText("';' expected")).toBeInTheDocument()
  })

  it('sends stdin from the Input tab', async () => {
    backend([status('COMPLETED', { exitCode: 0 })], {
      id: ID,
      status: 'COMPLETED',
      stdout: '42\n',
      stderr: '',
      compileErrors: [],
    })
    renderPlayground()
    fireEvent.click(screen.getByRole('tab', { name: /input/i }))
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '41' } })
    fireEvent.click(screen.getByRole('button', { name: /run/i }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(String(post[1]?.body)).stdin).toBe('41')
  })

  it('Stop cancels a running program', async () => {
    backend([status('RUNNING')], null)
    renderPlayground()
    fireEvent.click(screen.getByRole('button', { name: /run/i }))
    fireEvent.click(await screen.findByRole('button', { name: /^stop$/i }))

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true),
    )
  })

  it('shows a timeout note under the output', async () => {
    backend([status('TIMEOUT', { errorMessage: 'Time limit of 5 s exceeded.' })], {
      id: ID,
      status: 'TIMEOUT',
      stdout: 'partial\n',
      stderr: '',
      compileErrors: [],
    })
    renderPlayground()
    fireEvent.click(screen.getByRole('button', { name: /run/i }))
    expect(
      await screen.findByText(/⚠ Time limit of 5 s exceeded/, {}, { timeout: 3000 }),
    ).toBeInTheDocument()
    expect(await screen.findByText(/partial/)).toBeInTheDocument()
  })

  it('explains when the server is unreachable', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    renderPlayground()
    fireEvent.click(screen.getByRole('button', { name: /run/i }))
    expect(await screen.findByText(/could not reach the server/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /run/i })).toBeEnabled()
  })

  it('Ctrl+Enter runs from outside the editor', async () => {
    backend([status('COMPLETED', { exitCode: 0 })], {
      id: ID,
      status: 'COMPLETED',
      stdout: '',
      stderr: '',
      compileErrors: [],
    })
    renderPlayground()
    fireEvent.keyDown(window, { key: 'Enter', ctrlKey: true })
    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(true),
    )
  })
})

describe('helpers', () => {
  it('statusSummary covers every outcome', () => {
    expect(statusSummary(status('RUNTIME_ERROR', { exitCode: 1, executionTimeMs: 1500 }))).toEqual({
      text: 'Runtime error (exit code 1) · 1.5 s',
      tone: 'error',
    })
    expect(statusSummary(status('LIMIT_EXCEEDED', { errorMessage: 'Output limit' })).tone).toBe(
      'warning',
    )
    expect(statusSummary(status('CANCELLED')).text).toBe('Stopped')
  })

  it('requestErrorMessage explains rate limits and validation', () => {
    expect(requestErrorMessage(new ApiError({ status: 429 }))).toMatch(/too many runs/i)
    expect(
      requestErrorMessage(
        new ApiError({
          status: 400,
          code: 'VALIDATION_FAILED',
          errors: { sourceCode: 'must not be blank' },
        }),
      ),
    ).toBe('must not be blank')
  })

  it('toMarkers clamps positions into the document', () => {
    const monaco = { MarkerSeverity: { Error: 8 } }
    const [m] = toMarkers([{ line: 99, column: 0, message: 'x' }], 10, monaco)
    expect(m).toMatchObject({ severity: 8, startLineNumber: 10, startColumn: 1, endColumn: 2 })
  })
})
