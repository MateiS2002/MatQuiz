import {
  createApi,
  fetchBaseQuery,
  type BaseQueryFn,
  type FetchArgs,
  type FetchBaseQueryError,
} from "@reduxjs/toolkit/query/react"
import { clearToken } from "@/features/auth/slice/authSlice"

const DEFAULT_API_URL = "http://localhost:8080/api"

const resolveApiBaseUrl = () => {
  const env = import.meta.env as { VITE_API_URL?: string }
  const configuredUrl = env.VITE_API_URL?.trim()
  const baseUrl = configuredUrl && configuredUrl.length > 0
    ? configuredUrl
    : DEFAULT_API_URL
  return baseUrl.replace(/\/+$/, "")
}

const rawBaseQuery = fetchBaseQuery({
  baseUrl: resolveApiBaseUrl(),
  prepareHeaders: (headers, { getState }) => {
    const state = getState() as { auth?: { token?: string | null } }
    const token = state.auth?.token
    if (token) {
      headers.set("authorization", `Bearer ${token}`)
    }
    return headers
  },
})

const baseQueryWithAuth: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  const stateBefore = api.getState() as { auth?: { token?: string | null } }
  const tokenUsedForRequest = stateBefore.auth?.token ?? null
  const result = await rawBaseQuery(args, api, extraOptions)

  if (result.error?.status === 401) {
    const stateAfter = api.getState() as { auth?: { token?: string | null } }
    const currentToken = stateAfter.auth?.token ?? null

    // Avoid clearing a newly rotated token when an older in-flight request fails with 401.
    const failedCurrentToken =
      tokenUsedForRequest !== null && tokenUsedForRequest === currentToken

    if (failedCurrentToken) {
      sessionStorage.removeItem("matquiz_token")
      api.dispatch(clearToken())
    }
  }
  return result
}

export const baseApi = createApi({
  reducerPath: "api",
  baseQuery: baseQueryWithAuth,
  tagTypes: ["Auth", "Leaderboard", "Room", "Game"],
  endpoints: () => ({}),
})
