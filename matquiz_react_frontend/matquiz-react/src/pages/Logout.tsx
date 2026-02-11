import { useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { useAppDispatch } from "@/app/hooks"
import { clearToken } from "@/features/auth/slice/authSlice"
import { ROUTES } from "@/routes/paths"

const Logout = () => {
  const navigate = useNavigate()
  const dispatch = useAppDispatch()

  useEffect(() => {
    sessionStorage.removeItem("matquiz_token")
    dispatch(clearToken())
    void navigate(ROUTES.login)
  }, [dispatch, navigate])

  return null
}

export default Logout
