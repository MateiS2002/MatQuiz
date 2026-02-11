import { useEffect, useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { useAppDispatch } from "@/app/hooks"
import { useCreateRoomMutation } from "@/features/game/api/gameApiSlice"
import LeaveGameModal from "@/features/game/components/LeaveGameModal"
import { useLeaveGameGuard } from "@/features/game/hooks/useLeaveGameGuard"
import {
  clearLobbyHeader,
  setLobbyHeader,
} from "@/features/game/slice/gameSlice"
import { ROUTES } from "@/routes/paths"
import styles from "./Create.module.css"

let sharedCreateRoomPromise: Promise<void> | null = null

const Create = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [createRoom] = useCreateRoomMutation()
  const {
    isLeaveModalOpen,
    isLeaving,
    leaveError,
    requestExit,
    closeLeaveModal,
    confirmLeave,
  } = useLeaveGameGuard()
  const [logLines, setLogLines] = useState<string[]>([])
  const [minDelayDone, setMinDelayDone] = useState(false)
  const [roomReady, setRoomReady] = useState(false)

  const consoleLines = useMemo(
    () => [
      "Generating room number...",
      "Room generated.",
      "Generating qr code to be shareable...Done.",
      "Checking connection to game servers...Done. Have fun!",
    ],
    [],
  )

  useEffect(() => {
    dispatch(setLobbyHeader("Creating server room"))
    return () => {
      dispatch(clearLobbyHeader())
    }
  }, [dispatch])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setMinDelayDone(true)
    }, 3000)
    return () => {
      window.clearTimeout(timer)
    }
  }, [])

  useEffect(() => {
    let isMounted = true
    const ensureSingleCreateRequest = () => {
      sharedCreateRoomPromise ??= createRoom(undefined)
        .unwrap()
        .then(() => undefined)
        .finally(() => {
          sharedCreateRoomPromise = null
        })
      return sharedCreateRoomPromise
    }

    void ensureSingleCreateRequest()
      .then(() => {
        if (!isMounted) {
          return
        }
        setRoomReady(true)
      })
      .catch(() => {
        // handled by RTK Query
      })
    return () => {
      isMounted = false
    }
  }, [createRoom])

  useEffect(() => {
    let index = 0
    const interval = window.setInterval(() => {
      setLogLines(prev => {
        if (index >= consoleLines.length) {
          return prev
        }
        const next = [...prev, consoleLines[index]]
        index += 1
        return next
      })
      if (index >= consoleLines.length) {
        window.clearInterval(interval)
      }
    }, 650)
    return () => {
      window.clearInterval(interval)
    }
  }, [consoleLines])

  useEffect(() => {
    if (roomReady && minDelayDone) {
      void navigate(ROUTES.lobby)
    }
  }, [roomReady, minDelayDone, navigate])

  return (
    <div className={styles.create}>
      <div className={styles.darkContainer}>
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
          <p className={styles.title}>
            Just a sec, we are preparing the room for your game
          </p>
          <div className={styles.console}>
            {logLines.map(line => (
              <p key={line} className={styles.consoleLine}>
                {line}
              </p>
            ))}
            <span className={styles.cursor} />
          </div>
        </div>
      </div>
      <LeaveGameModal
        open={isLeaveModalOpen}
        isLoading={isLeaving}
        error={leaveError}
        onCancel={closeLeaveModal}
        onConfirm={confirmLeave}
      />
    </div>
  )
}

export default Create
