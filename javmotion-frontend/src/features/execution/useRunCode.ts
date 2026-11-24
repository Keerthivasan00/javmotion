import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEditorStore } from '@/features/editor'
import { usePlaybackStore } from '@/features/playback'
import {
  cancelExecution,
  createExecution,
  getExecutionOutput,
  getExecutionStatus,
  isTerminal,
} from './executionApi'
import { useRunStore } from './runStore'

/** Poll interval while a run is queued/running (frontend doc §6.3). */
export const POLL_MS = 500

const statusKey = (id: string) => ['execution', id, 'status'] as const
const outputKey = (id: string) => ['execution', id, 'output'] as const

/**
 * Submits the editor's code. Progress and errors go to runStore (`submitting`, `submitError`),
 * so every component shows the same state. Ignored while a submit is already in flight.
 */
export async function submitRun() {
  const store = useRunStore.getState()
  if (store.submitting) return
  const { source, stdin } = useEditorStore.getState()
  // A run replaces whatever the visualization panel was playing.
  usePlaybackStore.getState().clear()
  store.setRun(null)
  store.setSubmitError(null)
  store.setSubmitting(true)
  try {
    const res = await createExecution({
      sourceCode: source,
      mode: 'RUN',
      stdin: stdin || undefined,
    })
    useRunStore.getState().setRun({ id: res.id, token: res.accessToken, mode: 'RUN' })
  } catch (error) {
    useRunStore.getState().setSubmitError(error)
  } finally {
    useRunStore.getState().setSubmitting(false)
  }
}

/** Status of the current run; polls every POLL_MS until it reaches a terminal status. */
export function useExecutionStatus() {
  const run = useRunStore((s) => s.run)
  return useQuery({
    queryKey: statusKey(run?.id ?? 'none'),
    queryFn: () => getExecutionStatus(run!.id, run!.token),
    enabled: !!run,
    refetchInterval: (q) => (q.state.data && isTerminal(q.state.data.status) ? false : POLL_MS),
    retry: 2,
  })
}

/** stdout / stderr / compile errors, fetched once the run has finished. */
export function useExecutionOutput() {
  const run = useRunStore((s) => s.run)
  const status = useExecutionStatus().data?.status
  return useQuery({
    queryKey: outputKey(run?.id ?? 'none'),
    queryFn: () => getExecutionOutput(run!.id, run!.token),
    enabled: !!run && !!status && isTerminal(status),
    staleTime: Infinity, // a finished run never changes
  })
}

export function useCancelRun() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => {
      const run = useRunStore.getState().run
      return run ? cancelExecution(run.id, run.token) : Promise.resolve()
    },
    onSettled: () => {
      const run = useRunStore.getState().run
      if (run) void client.invalidateQueries({ queryKey: statusKey(run.id) })
    },
  })
}

/** True from pressing Run until the run reaches a terminal status. */
export function useIsRunning() {
  const submitting = useRunStore((s) => s.submitting)
  const run = useRunStore((s) => s.run)
  const status = useExecutionStatus().data?.status
  return submitting || (!!run && (!status || !isTerminal(status)))
}
