type ErrorWithData = {
  data?: unknown
  error?: string
}

type RateLimitedData = {
  message?: string
  retryAfterSeconds?: number
}

export const parseApiErrorMessage = (value: unknown, fallback: string) => {
  if (typeof value === "object" && value !== null) {
    const maybeError = value as ErrorWithData
    if (typeof maybeError.data === "string") {
      return maybeError.data
    }
    if (typeof maybeError.data === "object" && maybeError.data !== null) {
      const maybeData = maybeError.data as RateLimitedData
      if (typeof maybeData.message === "string") {
        return maybeData.message
      }
    }
    if (typeof maybeError.error === "string") {
      return maybeError.error
    }
  }
  return fallback
}
