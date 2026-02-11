import styles from "./LeaveGameModal.module.css"

type LeaveGameModalProps = {
  open: boolean
  isLoading: boolean
  error: string | null
  onCancel: () => void
  onConfirm: () => void
}

const LeaveGameModal = ({
  open,
  isLoading,
  error,
  onCancel,
  onConfirm,
}: LeaveGameModalProps) => {
  if (!open) {
    return null
  }

  return (
    <div className={styles.overlay} role="presentation">
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="leave-game-title"
      >
        <h3 id="leave-game-title" className={styles.title}>
          Leave game flow?
        </h3>
        <p className={styles.description}>
          If you have an active room, you will be removed from it.
        </p>
        {error ? <p className={styles.error}>{error}</p> : null}
        <div className={styles.actions}>
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={onCancel}
            disabled={isLoading}
          >
            Stay
          </button>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={onConfirm}
            disabled={isLoading}
          >
            {isLoading ? "Leaving..." : "Leave"}
          </button>
        </div>
      </div>
    </div>
  )
}

export default LeaveGameModal
