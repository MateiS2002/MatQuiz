import { createSlice } from "@reduxjs/toolkit"

type AuthState = {
  token: string | null
}

const initialState: AuthState = {
  token:
    typeof window === "undefined"
      ? null
      : sessionStorage.getItem("matquiz_token"),
}

export const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setToken: (state, action: { payload: string }) => {
      state.token = action.payload
    },
    clearToken: state => {
      state.token = null
    },
  },
})

export const { setToken, clearToken } = authSlice.actions
