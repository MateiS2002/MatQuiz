import profilePlaceholder from "@/assets/profile-placeholder.png"
import LobbyPlayerSlotCard from "@/features/lobby/components/LobbyPlayerSlotCard"
import type { GamePlayerDto } from "@/types/api"
import styles from "@/pages/Lobby.module.css"

type LobbyPlayerWaitingViewProps = {
  displayUserName: string
  displayRoomCode: string
  hostName?: string
  hostPlayer: GamePlayerDto | null
  youPlayer: GamePlayerDto | null
  playerSlots: (GamePlayerDto | null)[]
}

const LobbyPlayerWaitingView = ({
  displayUserName,
  displayRoomCode,
  hostName,
  hostPlayer,
  youPlayer,
  playerSlots,
}: LobbyPlayerWaitingViewProps) => {
  return (
    <div className={styles.playerGrid}>
      <div className={styles.card}>
        <p className={styles.cardTitle}>You</p>
        <div className={styles.avatar}>
          <img
            src={youPlayer?.avatarUrl ?? profilePlaceholder}
            alt={youPlayer?.nickname ?? "Player avatar"}
            className={styles.avatarImage}
          />
        </div>
        <p className={styles.cardSubtitle}>#{displayUserName}</p>
      </div>
      <LobbyPlayerSlotCard player={playerSlots[0]} />
      <div className={`${styles.card} ${styles.roomCard}`}>
        <p className={styles.cardTitle}>Room:</p>
        <p className={styles.roomCode}>#{displayRoomCode}</p>
        <div className={styles.spinner} />
      </div>
      <LobbyPlayerSlotCard player={playerSlots[1]} />
      <LobbyPlayerSlotCard player={playerSlots[2]} />
      <div className={styles.card}>
        <p className={styles.cardTitle}>Host</p>
        <div className={styles.avatar}>
          <img
            src={hostPlayer?.avatarUrl ?? profilePlaceholder}
            alt={hostPlayer?.nickname ?? "Host avatar"}
            className={styles.avatarImage}
          />
        </div>
        <p className={styles.cardSubtitle}>
          #{hostName ?? hostPlayer?.nickname ?? "Host"}
        </p>
      </div>
    </div>
  )
}

export default LobbyPlayerWaitingView
