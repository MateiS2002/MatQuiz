import LockOutlineIcon from "@mui/icons-material/LockOutline"
import type { SyntheticEvent } from "react"
import profilePlaceholder from "@/assets/profile-placeholder.png"
import type { Difficulty, GamePlayerDto } from "@/types/api"
import styles from "@/pages/Lobby.module.css"

type LobbyHostSetupViewProps = {
  hostSlots: (GamePlayerDto | null)[]
  isLockedGenerationState: boolean
  selectedTopicLabel: string
  selectedDifficulty: Difficulty
  consoleLines: string[]
  isRoomGenerating: boolean
  isRoomReady: boolean
  topic: string
  normalizedTopicLength: number
  maxTopicLength: number
  difficulty: Difficulty
  canStartGame: boolean
  canGenerate: boolean
  hostAvatar?: string
  hostName?: string
  displayUserName: string
  displayRoomCode: string
  onSubmit: (event: SyntheticEvent<HTMLFormElement>) => void
  onTopicChange: (value: string) => void
  onSelectDifficulty: (value: Difficulty) => void
}

const LobbyHostSetupView = ({
  hostSlots,
  isLockedGenerationState,
  selectedTopicLabel,
  selectedDifficulty,
  consoleLines,
  isRoomGenerating,
  isRoomReady,
  topic,
  normalizedTopicLength,
  maxTopicLength,
  difficulty,
  canStartGame,
  canGenerate,
  hostAvatar,
  hostName,
  displayUserName,
  displayRoomCode,
  onSubmit,
  onTopicChange,
  onSelectDifficulty,
}: LobbyHostSetupViewProps) => {
  return (
    <div className={styles.hostGrid}>
      <div className={styles.playersCard}>
        <h3 className={styles.sectionTitle}>Players</h3>
        <div className={styles.playersList}>
          {hostSlots.map((player, index) => (
            <div
              key={`${player?.nickname ?? "waiting"}-${String(index)}`}
              className={`${styles.playerPill} ${
                player ? "" : styles.playerPillEmpty
              }`}
            >
              {player ? `#${player.nickname}` : "Waiting..."}
            </div>
          ))}
        </div>
      </div>
      <div className={styles.generateCard}>
        <h3 className={styles.sectionTitle}>Generate a quiz</h3>
        <form className={styles.generateForm} onSubmit={onSubmit}>
          {isLockedGenerationState ? (
            <>
              <p className={styles.formLabel}>Selected topic</p>
              <div className={styles.lockedField}>
                <span className={styles.lockedFieldValue}>
                  {selectedTopicLabel || "No topic selected"}
                </span>
                <LockOutlineIcon className={styles.lockIcon} />
              </div>
              <p className={styles.formLabel}>Selected difficulty</p>
              <div className={`${styles.lockedField} ${styles.lockedDifficulty}`}>
                <span className={styles.lockedFieldValue}>
                  {selectedDifficulty === "ADVANCED" ? "Advanced" : "Easy"}
                </span>
                <LockOutlineIcon className={styles.lockIcon} />
              </div>
              <div className={styles.consoleBox}>
                {consoleLines.map(line => (
                  <p key={line} className={styles.consoleLine}>
                    {line}
                  </p>
                ))}
                {isRoomGenerating ? (
                  <span className={styles.consoleCursor} />
                ) : null}
              </div>
            </>
          ) : (
            <>
              <label className={styles.formLabel} htmlFor="topic">
                Enter a topic
              </label>
              <input
                id="topic"
                className={styles.formInput}
                placeholder="Ex. Eurovision"
                value={topic}
                maxLength={maxTopicLength}
                onChange={event => {
                  onTopicChange(event.target.value.slice(0, maxTopicLength))
                }}
              />
              <p className={styles.topicHint}>
                Topic max length: {String(maxTopicLength)} characters (
                {String(normalizedTopicLength)}/{String(maxTopicLength)}).
              </p>
              <p className={styles.formLabel}>Enter difficulty</p>
              <div className={styles.difficultyRow}>
                <button
                  type="button"
                  className={`${styles.difficultyButton} ${
                    difficulty === "EASY" ? styles.difficultyActive : ""
                  }`}
                  onClick={() => {
                    onSelectDifficulty("EASY")
                  }}
                >
                  Easy
                </button>
                <button
                  type="button"
                  className={`${styles.difficultyButton} ${
                    difficulty === "ADVANCED" ? styles.difficultyActive : ""
                  }`}
                  onClick={() => {
                    onSelectDifficulty("ADVANCED")
                  }}
                >
                  Advanced
                </button>
              </div>
            </>
          )}
          <button
            className={`${styles.generateButton} ${
              isLockedGenerationState ? styles.startGameButton : ""
            }`}
            type="submit"
            disabled={isLockedGenerationState ? !canStartGame : !canGenerate}
          >
            <span>{isLockedGenerationState ? "Start Game" : "Generate"}</span>
            {isLockedGenerationState && !canStartGame ? (
              <LockOutlineIcon className={`${styles.lockIcon} ${styles.lockIconInline}`} />
            ) : null}
          </button>
        </form>
        <p className={styles.formHint}>
          {isRoomGenerating
            ? "This may take 20 seconds"
            : isRoomReady
              ? "Quiz ready. Start when everyone is prepared."
              : "This may take 20 seconds"}
        </p>
      </div>
      <div className={styles.shareColumn}>
        <div className={styles.shareCard}>
          <p className={styles.shareTitle}>Share the room:</p>
          <p className={styles.shareCode}>#{displayRoomCode}</p>
          {/*<div className={styles.qrBox}>*/}
          {/*  <div className={styles.qrPattern} />*/}
          {/*</div>*/}
        </div>
        <div className={styles.hostCard}>
          <p className={styles.cardTitle}>Host (You)</p>
          <div className={styles.avatar}>
            <img
              src={hostAvatar ?? profilePlaceholder}
              alt={hostName ?? "Host avatar"}
              className={styles.avatarImage}
            />
          </div>
          <p className={styles.cardSubtitle}>#{hostName ?? displayUserName}</p>
        </div>
      </div>
    </div>
  )
}

export default LobbyHostSetupView
