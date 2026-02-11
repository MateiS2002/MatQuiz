import { useCallback, useMemo, useState } from "react"
import { useLocation } from "react-router-dom"
import { useAppSelector } from "@/app/hooks"
import { useLeaveRoomMutation } from "@/features/game/api/gameApiSlice"
import { ROUTES } from "@/routes/paths"

type ExitAction = () => void

const GAME_FLOW_PATHS = new Set<string>([
  ROUTES.gameControl,
  ROUTES.join,
  ROUTES.create,
  ROUTES.lobby,
])

const parseErrorMessage = (value: unknown) => {
  if (typeof value === "object" && value !== null) {
    const maybeError = value as { error?: string }
    if (typeof maybeError.error === "string") {
      return maybeError.error
    }
  }
  return "Failed to leave the game room. Please retry."
}

export const useLeaveGameGuard = () => {
  const location = useLocation()
  const activeRoomCode = useAppSelector(state => state.game.activeRoomCode)
  const activeRoomSnapshot = useAppSelector(
    state => state.game.activeRoomSnapshot,
  )
  const [leaveRoom, { isLoading: isLeaving }] = useLeaveRoomMutation()
  const [isOpen, setIsOpen] = useState(false)
  const [pendingAction, setPendingAction] = useState<ExitAction | null>(null)
  const [error, setError] = useState<string | null>(null)

  const isInGameFlow = useMemo(
    () => GAME_FLOW_PATHS.has(location.pathname),
    [location.pathname],
  )
  const roomCodeToLeave = activeRoomCode ?? activeRoomSnapshot?.roomCode ?? null

  const closeModal = useCallback(() => {
    setIsOpen(false)
    setPendingAction(null)
    setError(null)
  }, [])

  const requestExit = useCallback(
    (action: ExitAction) => {
      if (!isInGameFlow) {
        action()
        return
      }
      setError(null)
      setPendingAction(() => action)
      setIsOpen(true)
    },
    [isInGameFlow],
  )

  const confirmExit = useCallback(() => {
    const executePendingAction = () => {
      const action = pendingAction
      closeModal()
      action?.()
    }

    if (!roomCodeToLeave) {
      executePendingAction()
      return
    }

    setError(null)
    void leaveRoom({ roomCode: roomCodeToLeave })
      .unwrap()
      .then(() => {
        executePendingAction()
      })
      .catch((err: unknown) => {
        setError(parseErrorMessage(err))
      })
  }, [closeModal, leaveRoom, pendingAction, roomCodeToLeave])

  return {
    isLeaveModalOpen: isOpen,
    isLeaving,
    leaveError: error,
    requestExit,
    closeLeaveModal: closeModal,
    confirmLeave: confirmExit,
  }
}
