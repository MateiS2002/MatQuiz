/**
 * Session lookup hook to validate the current JWT with the backend.
 * Use this on protected routes to verify the token and hydrate user data.
 */

import { useGetSessionQuery } from "@/features/auth/api/authApiSlice"

export const useSession = () => {
  const query = useGetSessionQuery(undefined)

  return {
    user: query.data,
    isLoading: query.isLoading,
    isError: query.isError,
    error: query.error,
    refetch: query.refetch,
  }
}
