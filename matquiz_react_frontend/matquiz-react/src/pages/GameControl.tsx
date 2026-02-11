import { useEffect, useMemo, useState } from "react"
import { motion, useReducedMotion } from "motion/react"
import { useNavigate } from "react-router-dom"
import { useAppSelector } from "@/app/hooks"
import {
  useGetActiveGameQuery,
  useGetSessionQuery,
} from "@/features/auth/api/authApiSlice"
import {
  useLeaveRoomMutation,
  useReconnectRoomMutation,
} from "@/features/game/api/gameApiSlice"
import LeaveGameModal from "@/features/game/components/LeaveGameModal"
import { useLeaveGameGuard } from "@/features/game/hooks/useLeaveGameGuard"
import { ROUTES } from "@/routes/paths"
import styles from "./GameControl.module.css"

const parseErrorMessage = (value: unknown) => {
  if (typeof value === "object" && value !== null) {
    const maybeError = value as { error?: string }
    if (typeof maybeError.error === "string") {
      return maybeError.error
    }
  }
  return "Failed to check active games. Please retry."
}

const GameControl = () => {
  const navigate = useNavigate()
  const shouldReduceMotion = useReducedMotion()
  const { data: user } = useGetSessionQuery(undefined)
  const {
    data: activeGameData,
    error: activeGameError,
    isLoading: isCheckingActiveGame,
    refetch: refetchActiveGame,
  } = useGetActiveGameQuery(undefined, {
    refetchOnMountOrArgChange: true,
  })
  const activeRoomCode = useAppSelector(state => state.game.activeRoomCode)
  const [reconnectRoom, { isLoading: isReconnecting }] =
    useReconnectRoomMutation()
  const [leaveRoom, { isLoading: isLeaving }] = useLeaveRoomMutation()
  const [error, setError] = useState<string | null>(null)
  const hasActiveGame = activeGameData?.hasActiveGame ?? false
  const {
    isLeaveModalOpen,
    isLeaving: isGuardLeaving,
    leaveError,
    requestExit,
    closeLeaveModal,
    confirmLeave,
  } = useLeaveGameGuard()

  useEffect(() => {
    if (!activeGameError) {
      return
    }
    setError(parseErrorMessage(activeGameError))
  }, [activeGameError])

  const view = useMemo(() => {
    if (isCheckingActiveGame) {
      return "checking"
    }
    if (hasActiveGame) {
      return "active"
    }
    return "idle"
  }, [hasActiveGame, isCheckingActiveGame])

  const handleReconnect = () => {
    setError(null)
    void reconnectRoom(undefined)
      .unwrap()
      .then(room => {
        if (!room) {
          void refetchActiveGame()
          return
        }
        void navigate(ROUTES.lobby)
      })
      .catch((err: unknown) => {
        setError(parseErrorMessage(err))
      })
  }

  const handleLeave = () => {
    setError(null)

    const leaveByRoomCode = (roomCode: string) => {
      return leaveRoom({ roomCode })
        .unwrap()
        .then(() => {
          void refetchActiveGame()
        })
        .catch((err: unknown) => {
          setError(parseErrorMessage(err))
        })
    }

    if (activeRoomCode) {
      void leaveByRoomCode(activeRoomCode)
      return
    }

    void reconnectRoom(undefined)
      .unwrap()
      .then(room => {
        if (!room) {
          void refetchActiveGame()
          return
        }
        void leaveByRoomCode(room.roomCode)
      })
      .catch((err: unknown) => {
        setError(parseErrorMessage(err))
      })
  }

  return (
    <div className={styles.gameControl}>
      <motion.div
        className={styles.darkContainer}
        initial={
          shouldReduceMotion
            ? false
            : { opacity: 0, y: 20, scale: 0.995, filter: "blur(5px)" }
        }
        animate={
          shouldReduceMotion
            ? { opacity: 1 }
            : { opacity: 1, y: 0, scale: 1, filter: "blur(0px)" }
        }
        transition={
          shouldReduceMotion
            ? { duration: 0 }
            : { duration: 0.28, ease: "easeOut" }
        }
      >
        <div className={styles.panel}>
          <button
            type="button"
            className={styles.backButton}
            onClick={() => {
              requestExit(() => {
                void navigate(ROUTES.home)
              })
            }}
          >
            Back
          </button>

          {view === "checking" ? (
            <div className={styles.centerContent}>
              <p className={styles.mainText}>Checking your active games...</p>
              <div className={styles.loadingBar} />
            </div>
          ) : null}

          {view === "idle" ? (
            <div className={styles.centerContent}>
              <p className={styles.mainText}>
                Welcome back, {user?.username ?? "player"}!
              </p>
              <div className={styles.actionsRow}>
                <button
                  type="button"
                  className={`${styles.actionButton} ${styles.primaryButton}`}
                  onClick={() => {
                    void navigate(ROUTES.join)
                  }}
                >
                  Join
                </button>
                <button
                  type="button"
                  className={styles.actionButton}
                  onClick={() => {
                    void navigate(ROUTES.create)
                  }}
                >
                  Create game
                </button>
              </div>
              {error ? <p className={styles.errorText}>{error}</p> : null}
            </div>
          ) : null}

          {view === "active" ? (
            <div className={styles.centerContent}>
              <p className={styles.mainText}>You have an active game room!</p>
              <button
                type="button"
                className={styles.reconnectButton}
                onClick={handleReconnect}
                disabled={isReconnecting}
              >
                Reconnect
              </button>
              <button
                type="button"
                className={styles.leaveButton}
                onClick={handleLeave}
                disabled={isLeaving || isReconnecting}
              >
                Leave
              </button>
              {activeRoomCode ? (
                <p className={styles.roomHint}>Room #{activeRoomCode}</p>
              ) : null}
              {error ? <p className={styles.errorText}>{error}</p> : null}
            </div>
          ) : null}
        </div>
      </motion.div>
      <LeaveGameModal
        open={isLeaveModalOpen}
        isLoading={isGuardLeaving}
        error={leaveError}
        onCancel={closeLeaveModal}
        onConfirm={confirmLeave}
      />
    </div>
  )
}

export default GameControl
