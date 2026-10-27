const API_BASE = ''

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function readErrorDetail(res: Response): Promise<string> {
  try {
    const body: unknown = await res.json()
    if (body && typeof body === 'object') {
      const record = body as Record<string, unknown>
      const message = record.message ?? record.error ?? record.detail
      if (typeof message === 'string' && message.trim()) return message
    }
  } catch {
    /* non-JSON error body */
  }
  return ''
}

export async function fetchConversationId(signal?: AbortSignal): Promise<string> {
  let res: Response
  try {
    res = await fetch(`${API_BASE}/v1/api/conversationId`, {
      headers: { Accept: 'application/json' },
      signal,
    })
  } catch {
    throw new ApiError(0, 'network')
  }
  if (!res.ok) {
    throw new ApiError(res.status, (await readErrorDetail(res)) || res.statusText)
  }
  const body = (await res.json()) as { conversationId?: unknown }
  if (typeof body.conversationId !== 'string' || !body.conversationId.trim()) {
    throw new ApiError(0, 'The backend returned a malformed conversation id.')
  }
  return body.conversationId
}

export interface ChatResult {
  response: string
  semanticMemorySummary?: string | null
  conversationMemorySummary?: string | null
}

export interface SendOptions {
  signal?: AbortSignal
  timeoutMs?: number
}

export async function sendChat(
  conversationId: string,
  query: string,
  options: SendOptions = {},
): Promise<ChatResult> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), options.timeoutMs ?? 120_000)
  const forwardAbort = () => controller.abort()
  options.signal?.addEventListener('abort', forwardAbort)

  try {
    let res: Response
    try {
      res = await fetch(`${API_BASE}/v1/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ conversationId, query, stream: false }),
        signal: controller.signal,
      })
    } catch (error) {
      if (options.signal?.aborted) throw error
      throw new ApiError(0, 'network')
    }
    if (!res.ok) {
      throw new ApiError(res.status, (await readErrorDetail(res)) || res.statusText)
    }
    return (await res.json()) as ChatResult
  } finally {
    window.clearTimeout(timeout)
    options.signal?.removeEventListener('abort', forwardAbort)
  }
}

export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        return "I can't reach the assistant backend right now. Make sure the Spring Boot service is running on port 8080, then retry."
      case 400:
        return `The request was rejected as invalid${error.message ? `: ${error.message}` : '.'}`
      case 429:
        return 'The language model is rate-limiting requests right now. Wait a few seconds and retry.'
      case 500:
        return 'The assistant hit a temporary server error — usually an upstream LLM rate limit. Please retry in a few seconds.'
      default:
        return `The assistant returned an error (HTTP ${error.status})${error.message ? `: ${error.message}` : '.'}`
    }
  }
  if (error instanceof DOMException && error.name === 'AbortError') {
    return 'The request timed out before the assistant finished answering. Please retry.'
  }
  return 'Something went wrong while contacting the assistant. Please retry.'
}
