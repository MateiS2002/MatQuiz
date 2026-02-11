import { useMemo, useState, type SyntheticEvent } from "react"
import { useNavigate } from "react-router-dom"
import profilePlaceholder from "@/assets/profile-placeholder.png"
import InfoModal from "@/components/InfoModal"
import { useGetSessionQuery } from "@/features/auth/api/authApiSlice"
import { useJoinRoomMutation } from "@/features/game/api/gameApiSlice"
import { ROUTES } from "@/routes/paths"
import styles from "./Join.module.css"

const ROOM_CODE_REGEX = /^[A-Z0-9]{5}$/

const parseJoinError = (value: unknown) => {
  if (typeof value === "object" && value !== null) {
    const maybeError = value as { error?: string; data?: unknown }
    if (typeof maybeError.error === "string") {
      if (maybeError.error.includes("Timed out waiting for WebSocket response")) {
        return "Could not join room. Verify the room pin and try again."
      }
      return maybeError.error
    }
    if (typeof maybeError.data === "string") {
      return maybeError.data
    }
  }
  return "Could not join this room. Please check the room pin and retry."
}

const Join = () => {
  const { data: user } = useGetSessionQuery(undefined)
  const [roomCode, setRoomCode] = useState("")
  const [modalMessage, setModalMessage] = useState<string | null>(null)
  const [joinRoom, { isLoading }] = useJoinRoomMutation()
  const navigate = useNavigate()

  const normalizedRoomCode = useMemo(
    () => roomCode.trim().toUpperCase(),
    [roomCode],
  )
  const isPinValid = ROOM_CODE_REGEX.test(normalizedRoomCode)
  const canJoin = isPinValid && !isLoading

  const lastGamePoints = user?.lastGamePoints
  const lastGameDisplay =
    typeof lastGamePoints === "number"
      ? `${lastGamePoints >= 0 ? "+" : ""}${String(lastGamePoints)}`
      : "+50"

  const handleSubmit = (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!isPinValid) {
      setModalMessage("Room pin must be exactly 5 letters or numbers.")
      return
    }
    if (isLoading) {
      return
    }
    void joinRoom({ roomCode: normalizedRoomCode })
      .unwrap()
      .then(() => {
        void navigate(ROUTES.lobby)
      })
      .catch((error: unknown) => {
        setModalMessage(parseJoinError(error))
      })
  }

  return (
    <div className={styles.join}>
      <div className={styles.darkContainer}>
        <div className={styles.content}>
          <div className={styles.formCard}>
            <h2 className={styles.title}>Room Selection</h2>
            <form className={styles.form} onSubmit={handleSubmit}>
              <label className={styles.label} htmlFor="roomCode">
                Enter a room pin:
              </label>
              <input
                id="roomCode"
                className={styles.input}
                value={roomCode}
                placeholder="ex. 12A45"
                maxLength={5}
                onChange={event => {
                  const sanitized = event.target.value
                    .toUpperCase()
                    .replace(/[^A-Z0-9]/g, "")
                    .slice(0, 5)
                  setRoomCode(sanitized)
                }}
              />
              <p className={styles.note}>
                Public rooms and quick match is coming soon!
              </p>
              <button
                className={styles.joinButton}
                type="submit"
                disabled={!canJoin}
              >
                Join
              </button>
            </form>
          </div>
          <div className={styles.sidebar}>
            <div className={styles.userCard}>
              <p className={styles.cardTitle}>You</p>
              <div className={styles.avatar}>
                <img
                  src={user?.avatarUrl ?? profilePlaceholder}
                  alt={user?.username ?? "Profile avatar"}
                  className={styles.avatarImage}
                />
              </div>
              <p className={styles.cardSubtitle}>#{user?.username ?? "John23"}</p>
            </div>
            <div className={styles.statsCard}>
              <p className={styles.cardTitle}>Current</p>
              <p className={styles.cardTitle}>Rating</p>
              <p className={styles.cardSubtitle}>
                {user?.eloRating ?? 1450} ELO
              </p>
              <p className={styles.cardTitle}>Last game</p>
              <p className={styles.cardAccent}>{lastGameDisplay}</p>
            </div>
          </div>
        </div>
      </div>
      <InfoModal
        open={modalMessage !== null}
        title="Message"
        message={modalMessage ?? ""}
        onClose={() => {
          setModalMessage(null)
        }}
      />
    </div>
  )
}

export default Join
