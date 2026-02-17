/**
 * Session lookup hook to validate the current JWT with the backend.
 * Use this on protected routes to verify the token and hydrate user data.
 */

import { useAppSelector } from "@/app/hooks"
import { useGetSessionQuery } from "@/features/auth/api/authApiSlice"

export const useSession = () => {
  const token = useAppSelector(state => state.auth.token)
  const query = useGetSessionQuery(undefined, {
    skip: !token,
    refetchOnMountOrArgChange: true,
  })

  return {
    user: query.data,
    isLoading: query.isLoading,
    isError: query.isError,
    error: query.error,
    refetch: query.refetch,
  }
}
