import "./App.css"
import { useAppSelector } from "@/app/hooks"
import Navbar from "@/components/Navbar.tsx"
import { useHoldConnectionQuery } from "@/features/game/api/gameApiSlice"
import AppRoutes from "@/routes/AppRoutes.tsx"

export const App = () => {
  const token = useAppSelector(state => state.auth.token)

  useHoldConnectionQuery(undefined, {
    skip: !token,
  })

  return (
    <div className="appShell">
      <div className="appContent">
        <Navbar />
        <AppRoutes />
      </div>
    </div>
  )
}
