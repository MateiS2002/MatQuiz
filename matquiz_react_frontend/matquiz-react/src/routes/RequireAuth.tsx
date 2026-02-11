/**
 * Guards routes that require an authenticated session.
 * Redirects to login when no token is present or session validation fails.
 */

import type { ReactElement } from "react"
import { Navigate, useLocation } from "react-router-dom"
import { useAppSelector } from "@/app/hooks"
import { useSession } from "@/features/auth/hooks/useSession"
import { ROUTES } from "@/routes/paths"

type RequireAuthProps = {
  children: ReactElement
}

const RequireAuth = ({ children }: RequireAuthProps) => {
  const location = useLocation()
  const token = useAppSelector(state => state.auth.token)
  const { isLoading, isError } = useSession()

  if (!token) {
    return <Navigate to={ROUTES.login} state={{ from: location.pathname }} replace />
  }

  if (isLoading) {
    return null
  }

  if (isError) {
    return <Navigate to={ROUTES.login} state={{ from: location.pathname }} replace />
  }

  return children
}

export default RequireAuth
