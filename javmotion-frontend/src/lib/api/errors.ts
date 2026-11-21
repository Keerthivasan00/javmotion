/** Mirrors the backend error body (RFC 7807 style) — backend doc §7. */
export interface ApiErrorBody {
  type?: string
  title?: string
  status: number
  code?: string
  detail?: string
  traceId?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly detail?: string
  readonly traceId?: string

  constructor(body: ApiErrorBody) {
    super(body.detail || body.title || `Request failed (${body.status})`)
    this.name = 'ApiError'
    this.status = body.status
    this.code = body.code
    this.detail = body.detail
    this.traceId = body.traceId
  }

  /** Status 0 = request never reached the server (offline, CORS, server asleep). */
  get isNetworkError() {
    return this.status === 0
  }
}
