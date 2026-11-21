import { ZodError } from 'zod'
import { ApiError } from '@/lib/api/errors'
import { TraceVersionError } from '@/lib/trace/schema'

/** User-facing text for any error from a concept request. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError && error.isNetworkError) {
    return 'Could not reach the server. Is javmotion-api running?'
  }
  if (error instanceof ApiError && error.status === 429) {
    return 'Too many requests — please wait a moment and try again.'
  }
  if (error instanceof TraceVersionError) return error.message
  if (error instanceof ZodError)
    return 'The server sent a trace this page cannot read. Please refresh.'
  if (error instanceof Error) return error.message
  return 'Something went wrong.'
}
