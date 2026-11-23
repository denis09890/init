/** A failed API call. Status 0 means the server could not be reached at all. */
export class ApiError extends Error {
  readonly status: number

  /** The `detail` of the server's RFC 9457 problem response, when it sent one. */
  readonly detail: string | null

  constructor(message: string, status: number, detail: string | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
  }
}

export async function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, { headers: { Accept: 'application/json' }, signal })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError('The server could not be reached.', 0)
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  try {
    return (await response.json()) as T
  } catch {
    throw new ApiError('The server sent a response that could not be read.', response.status)
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let detail: string | null = null
  try {
    const problem: unknown = await response.json()
    if (
      typeof problem === 'object' &&
      problem !== null &&
      'detail' in problem &&
      typeof problem.detail === 'string'
    ) {
      detail = problem.detail
    }
  } catch {
    // the body is not a problem document; the status alone has to do
  }
  return new ApiError(`The server answered with status ${response.status}.`, response.status, detail)
}
