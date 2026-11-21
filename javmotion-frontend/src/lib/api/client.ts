import { ApiError, type ApiErrorBody } from './errors'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  /** Per-execution token for anonymous users (X-Execution-Token). */
  executionToken?: string
}

/**
 * Single entry point for all backend calls.
 * TODO (Step 9): attach Authorization header and handle 401 → /auth/refresh → retry.
 */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, executionToken, headers, ...init } = options

  const finalHeaders = new Headers(headers)
  finalHeaders.set('Accept', 'application/json')
  if (body !== undefined) finalHeaders.set('Content-Type', 'application/json')
  if (executionToken) finalHeaders.set('X-Execution-Token', executionToken)

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: finalHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError({ status: 0, title: 'Network error', detail: 'Could not reach the server.' })
  }

  if (!response.ok) {
    let errorBody: ApiErrorBody = { status: response.status }
    try {
      errorBody = { ...(await response.json()), status: response.status }
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(errorBody)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
