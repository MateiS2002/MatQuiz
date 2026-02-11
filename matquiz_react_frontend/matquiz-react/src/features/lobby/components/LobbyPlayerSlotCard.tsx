import profilePlaceholder from "@/assets/profile-placeholder.png"
import type { GamePlayerDto } from "@/types/api"
import styles from "./LobbyPlayerSlotCard.module.css"

type LobbyPlayerSlotCardProps = {
  player: GamePlayerDto | null
}

const LobbyPlayerSlotCard = ({ player }: LobbyPlayerSlotCardProps) => {
  if (!player) {
    return (
      <div className={`${styles.card} ${styles.emptyCard}`}>
        <div className={`${styles.avatar} ${styles.emptyAvatar}`}>
          <img
            src={profilePlaceholder}
            alt="No player"
            className={styles.avatarImage}
          />
        </div>
        <p className={styles.emptyPlayerLabel}>No player</p>
      </div>
    )
  }

  return (
    <div className={styles.card}>
      <div className={styles.avatar}>
        <img
          src={player.avatarUrl ?? profilePlaceholder}
          alt={player.nickname}
          className={styles.avatarImage}
        />
      </div>
      <p className={styles.cardSubtitle}>#{player.nickname}</p>
    </div>
  )
}

export default LobbyPlayerSlotCard
