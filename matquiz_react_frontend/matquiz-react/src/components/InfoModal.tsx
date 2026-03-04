import styles from "./InfoModal.module.css"

type InfoModalProps = {
  open: boolean
  title: string
  message: string
  onClose: () => void
  sizePreset?: "default" | "startup"
  primaryActionLabel?: string
  onPrimaryAction?: () => void
  hideCloseButton?: boolean
}

const InfoModal = ({
  open,
  title,
  message,
  onClose,
  sizePreset = "default",
  primaryActionLabel,
  onPrimaryAction,
  hideCloseButton = false,
}: InfoModalProps) => {
  const isStartupPreset =
    sizePreset === "startup" || title === "Testing Deployment Notice"

  if (!open) {
    return null
  }

  return (
    <div className={styles.overlay} role="presentation">
      <div
        className={`${styles.modal} ${isStartupPreset ? styles.modalStartup : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="info-modal-title"
      >
        <div className={styles.header}>
          {hideCloseButton ? (
            <div className={styles.closeButtonPlaceholder} />
          ) : (
            <button
              type="button"
              className={styles.closeButton}
              aria-label="Close message"
              onClick={onClose}
            >
              x
            </button>
          )}
          <h3 id="info-modal-title" className={styles.title}>
            {title}
          </h3>
        </div>
        <div className={styles.body}>
          <p className={styles.message}>{message}</p>
          {primaryActionLabel && onPrimaryAction ? (
            <button
              type="button"
              className={styles.primaryAction}
              onClick={onPrimaryAction}
            >
              {primaryActionLabel}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  )
}

export default InfoModal
