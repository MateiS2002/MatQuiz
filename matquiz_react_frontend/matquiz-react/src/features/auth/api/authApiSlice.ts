import { baseApi } from "@/app/baseApi"
import { setToken } from "@/features/auth/slice/authSlice"
import type {
  ActiveGameDto,
  ChangeEmailRequest,
  ChangePasswordRequest,
  ChangeUsernameRequest,
  LeaderboardDto,
  LeaderboardRequest,
  LoginRequest,
  RegisterRequest,
  SetProfilePictureRequest,
  SessionRefreshDto,
  UserSummaryDto,
} from "@/types/api"

const persistToken = (token: string, dispatch: (action: ReturnType<typeof setToken>) => void) => {
  sessionStorage.setItem("matquiz_token", token)
  dispatch(setToken(token))
}

const applySessionRefresh = (
  data: SessionRefreshDto,
  dispatch: (action: unknown) => void,
) => {
  persistToken(data.accessToken, dispatch as (action: ReturnType<typeof setToken>) => void)
  dispatch(
    authApiSlice.util.updateQueryData("getSession", undefined, draft => {
      Object.assign(draft, data.user)
    }),
  )
}

export const authApiSlice = baseApi.injectEndpoints({
  endpoints: build => ({
    login: build.mutation<string, LoginRequest>({
      query: body => ({
        url: "/auth/login",
        method: "POST",
        body,
        responseHandler: "text",
      }),
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          const { data: token } = await queryFulfilled
          persistToken(token, dispatch)
        } catch {
          // errors handled by RTK Query
        }
      },
    }),
    register: build.mutation<boolean, RegisterRequest>({
      query: body => ({
        url: "/auth/register",
        method: "POST",
        body,
      }),
    }),
    getSession: build.query<UserSummaryDto, undefined>({
      query: () => "/auth/session",
      providesTags: ["Auth"],
    }),
    setProfilePicture: build.mutation<UserSummaryDto, SetProfilePictureRequest>({
      query: body => ({
        url: "/auth/setProfilePicture",
        method: "PATCH",
        body,
      }),
      invalidatesTags: ["Auth"],
    }),
    changeUsername: build.mutation<SessionRefreshDto, ChangeUsernameRequest>({
      query: body => ({
        url: "/auth/changeUsername",
        method: "PATCH",
        body,
      }),
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled
          applySessionRefresh(data, dispatch)
        } catch {
          // errors handled by RTK Query
        }
      },
    }),
    changeEmail: build.mutation<SessionRefreshDto, ChangeEmailRequest>({
      query: body => ({
        url: "/auth/changeEmail",
        method: "PATCH",
        body,
      }),
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled
          applySessionRefresh(data, dispatch)
        } catch {
          // errors handled by RTK Query
        }
      },
    }),
    changePassword: build.mutation<SessionRefreshDto, ChangePasswordRequest>({
      query: body => ({
        url: "/auth/changePassword",
        method: "PATCH",
        body,
      }),
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled
          applySessionRefresh(data, dispatch)
        } catch {
          // errors handled by RTK Query
        }
      },
    }),
    getActiveGame: build.query<ActiveGameDto, undefined>({
      query: () => "/auth/active",
    }),
    getLeaderboard: build.query<LeaderboardDto[], undefined>({
      query: () => "/auth/leaderboard",
    }),
    getLeaderboardForUser: build.mutation<LeaderboardDto[], LeaderboardRequest>({
      query: body => ({
        url: "/auth/leaderboard",
        method: "POST",
        body,
      }),
    }),
  }),
})

export const {
  useLoginMutation,
  useRegisterMutation,
  useGetSessionQuery,
  useSetProfilePictureMutation,
  useChangeUsernameMutation,
  useChangeEmailMutation,
  useChangePasswordMutation,
  useGetActiveGameQuery,
  useGetLeaderboardQuery,
  useGetLeaderboardForUserMutation,
} = authApiSlice
