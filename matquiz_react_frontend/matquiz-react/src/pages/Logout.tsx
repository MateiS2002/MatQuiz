import { useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { useAppDispatch } from "@/app/hooks"
import { baseApi } from "@/app/baseApi"
import { clearToken } from "@/features/auth/slice/authSlice"
import { ROUTES } from "@/routes/paths"

const Logout = () => {
  const navigate = useNavigate()
  const dispatch = useAppDispatch()

  useEffect(() => {
    sessionStorage.removeItem("matquiz_token")
    dispatch(baseApi.util.resetApiState())
    dispatch(clearToken())
    void navigate(ROUTES.login, { replace: true })
  }, [dispatch, navigate])

  return null
}

export default Logout
