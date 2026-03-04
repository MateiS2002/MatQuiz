import "./App.css"
import { useLocation } from "react-router-dom"
import { useAppSelector } from "@/app/hooks"
import InfoModal from "@/components/InfoModal"
import Navbar from "@/components/Navbar.tsx"
import { useHoldConnectionQuery } from "@/features/game/api/gameApiSlice"
import AppRoutes from "@/routes/AppRoutes.tsx"
import { ROUTES } from "@/routes/paths"

export const App = () => {
  const location = useLocation()
  const token = useAppSelector(state => state.auth.token)
  const activeRoomCode = useAppSelector(state => state.game.activeRoomCode)
  const isRealtimeDisconnected = useAppSelector(
    state => state.game.isRealtimeDisconnected,
  )

  useHoldConnectionQuery(undefined, {
    skip: !token,
  })

  const canShowDisconnectModal =
    Boolean(token) &&
    isRealtimeDisconnected &&
    (Boolean(activeRoomCode) ||
      location.pathname === ROUTES.lobby ||
      location.pathname === ROUTES.gameControl)

  return (
    <div className="appShell">
      <div className="appContent">
        <Navbar />
        <AppRoutes />
      </div>
      <InfoModal
        open={canShowDisconnectModal}
        title="Connection lost"
        message="The game connection was interrupted. Refresh to reconnect and continue."
        onClose={() => undefined}
        hideCloseButton
        primaryActionLabel="Refresh"
        onPrimaryAction={() => {
          window.location.reload()
        }}
      />
    </div>
  )
}
