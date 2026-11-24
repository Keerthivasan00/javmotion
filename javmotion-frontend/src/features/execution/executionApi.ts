import { api } from '@/lib/api/client'

/** Mirrors backend `ExecutionStatus` (backend doc §4.2). */
export type ExecutionStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIMEOUT'
  | 'LIMIT_EXCEEDED'
  | 'FAILED'
  | 'CANCELLED'

export type ExecutionMode = 'RUN' | 'VISUALIZE'

export const isTerminal = (s: ExecutionStatus) => s !== 'QUEUED' && s !== 'RUNNING'

export interface CompileError {
  line: number
  column: number
  message: string
}

export interface CreateExecutionResponse {
  id: string
  accessToken: string
  status: ExecutionStatus
}

export interface ExecutionStatusResponse {
  id: string
  mode: ExecutionMode
  status: ExecutionStatus
  exitCode: number | null
  executionTimeMs: number | null
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface ExecutionOutputResponse {
  id: string
  status: ExecutionStatus
  stdout: string | null
  stderr: string | null
  compileErrors: CompileError[]
}

export function createExecution(body: {
  sourceCode: string
  mode: ExecutionMode
  stdin?: string
}): Promise<CreateExecutionResponse> {
  return api('/executions', { method: 'POST', body: { ...body, language: 'java' } })
}

export function getExecutionStatus(id: string, token: string) {
  return api<ExecutionStatusResponse>(`/executions/${id}`, { executionToken: token })
}

export function getExecutionOutput(id: string, token: string) {
  return api<ExecutionOutputResponse>(`/executions/${id}/output`, { executionToken: token })
}

/** Cancels a queued/running execution (deletes a finished one). */
export function cancelExecution(id: string, token: string) {
  return api<void>(`/executions/${id}`, { method: 'DELETE', executionToken: token })
}
