import { useState, type ReactNode } from 'react'
import { useEditorStore } from '@/features/editor'
import {
  useExecutionOutput,
  useExecutionStatus,
  useRunStore,
  type ExecutionOutputResponse,
  type ExecutionStatusResponse,
} from '@/features/execution'
import { useCurrentStdout, useHasTrace } from '@/features/playback'
import { cn } from '@/lib/utils/cn'

type Tab = 'output' | 'errors' | 'input'

/**
 * Output: stdout of the last run — or, while a trace is loaded, stdout up to the current step.
 * Errors: compile errors / stderr of the last run (opens by itself when the run failed).
 * Input: stdin sent with the next run.
 */
export function ConsolePanel({
  runs = true,
}: {
  /** Show Run output and the Input tab. */ runs?: boolean
}) {
  const hasTrace = useHasTrace()
  const traceStdout = useCurrentStdout()
  const storeRun = useRunStore((s) => s.run)
  const run = runs ? storeRun : null
  const status = useExecutionStatus().data
  const output = useExecutionOutput().data
  const stdin = useEditorStore((s) => s.stdin)

  // The tab follows the run (Errors when it failed) until the user picks one for this run.
  // Input stays open across runs. Derived during render — no effect needed.
  const runId = run?.id ?? null
  const failed = status?.status === 'COMPILE_ERROR' || status?.status === 'RUNTIME_ERROR'
  const [choice, setChoice] = useState<{ tab: Tab; runId: string | null } | null>(null)
  const autoTab: Tab = failed && output ? 'errors' : 'output'
  const tab: Tab =
    choice && (choice.runId === runId || choice.tab === 'input') ? choice.tab : autoTab
  const setTab = (t: Tab) => setChoice({ tab: t, runId })

  const errorCount =
    !hasTrace && output ? output.compileErrors.length || (output.stderr ? 1 : 0) : 0

  return (
    <section className="flex h-full flex-col bg-surface" aria-label="Console">
      <div className="flex h-9 shrink-0 items-center gap-1 border-b px-2" role="tablist">
        {(runs ? (['output', 'errors', 'input'] as const) : (['output', 'errors'] as const)).map(
          (t) => (
            <button
              key={t}
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={cn(
                'flex items-center gap-1.5 rounded px-3 py-1 text-xs font-medium capitalize',
                tab === t ? 'bg-surface-2 text-fg' : 'text-muted hover:text-fg',
              )}
            >
              {t}
              {t === 'errors' && errorCount > 0 && (
                <span className="rounded-full bg-error/20 px-1.5 text-[10px] text-error">
                  {errorCount}
                </span>
              )}
              {t === 'input' && stdin && <span className="h-1.5 w-1.5 rounded-full bg-accent" />}
            </button>
          ),
        )}
      </div>

      {tab === 'input' ? (
        <InputTab />
      ) : hasTrace ? (
        <Pre muted={!traceStdout}>
          {tab === 'output' ? traceStdout || '(no output yet at this step)' : 'No errors.'}
        </Pre>
      ) : tab === 'output' ? (
        <OutputTab hasRun={!!run} status={status} output={output} />
      ) : (
        <ErrorsTab output={output} />
      )}
    </section>
  )
}

function Pre({ children, muted }: { children: ReactNode; muted?: boolean }) {
  return (
    <pre
      className={cn(
        'min-h-0 flex-1 overflow-auto p-3 font-mono text-sm whitespace-pre-wrap',
        muted ? 'text-muted' : 'text-fg',
      )}
    >
      {children}
    </pre>
  )
}

function OutputTab({
  hasRun,
  status,
  output,
}: {
  hasRun: boolean
  status?: ExecutionStatusResponse
  output?: ExecutionOutputResponse
}) {
  if (!hasRun) return <Pre muted>Program output will appear here.</Pre>
  if (!output) return <Pre muted>{status ? 'Running…' : 'Starting…'}</Pre>
  const note =
    status?.status === 'TIMEOUT' ||
    status?.status === 'LIMIT_EXCEEDED' ||
    status?.status === 'FAILED'
      ? status.errorMessage
      : status?.status === 'CANCELLED'
        ? 'Stopped.'
        : null
  return (
    <Pre muted={!output.stdout && !note}>
      {output.stdout || (note ? '' : '(no output)')}
      {note && <span className="mt-2 block text-warning">⚠ {note}</span>}
    </Pre>
  )
}

function ErrorsTab({ output }: { output?: ExecutionOutputResponse }) {
  if (!output || (output.compileErrors.length === 0 && !output.stderr)) {
    return <Pre muted>No errors.</Pre>
  }
  if (output.compileErrors.length > 0) {
    return (
      <ul
        className="min-h-0 flex-1 space-y-1 overflow-auto p-3 font-mono text-sm"
        aria-label="Compile errors"
      >
        {output.compileErrors.map((e, i) => (
          <li key={i} className="text-error">
            <span className="text-muted">
              line {e.line}:{e.column}
            </span>{' '}
            {e.message}
          </li>
        ))}
      </ul>
    )
  }
  return (
    <pre className="min-h-0 flex-1 overflow-auto p-3 font-mono text-sm whitespace-pre-wrap text-error">
      {output.stderr}
    </pre>
  )
}

function InputTab() {
  const stdin = useEditorStore((s) => s.stdin)
  const setStdin = useEditorStore((s) => s.setStdin)
  return (
    <label className="flex min-h-0 flex-1 flex-col gap-1 p-3 text-xs text-muted">
      Input sent to System.in on the next Run (one value per line)
      <textarea
        value={stdin}
        onChange={(e) => setStdin(e.target.value)}
        maxLength={8192}
        spellCheck={false}
        className="min-h-0 flex-1 resize-none rounded-md border bg-surface-2 p-2 font-mono text-sm text-fg"
        placeholder={'5\nhello'}
      />
    </label>
  )
}
