import { useEffect, useState } from "react"
import {
  useChangeEmailMutation,
  useChangePasswordMutation,
  useChangeUsernameMutation,
  useGetSessionQuery,
  useSetProfilePictureMutation,
} from "@/features/auth/api/authApiSlice"
import profilePlaceholder from "@/assets/profile-placeholder.png"
import styles from "./Profile.module.css"

const AVATAR_OPTIONS = Array.from(
  { length: 13 },
  (_, index) => `https://media.mateistanescu.ro/${String(index + 1)}.png`,
)

const parseApiError = (value: unknown) => {
  if (typeof value === "object" && value !== null) {
    const maybeError = value as { data?: unknown; error?: string }
    if (typeof maybeError.data === "string") {
      return maybeError.data
    }
    if (typeof maybeError.data === "object" && maybeError.data !== null) {
      const maybeData = maybeError.data as { message?: string }
      if (typeof maybeData.message === "string") {
        return maybeData.message
      }
    }
    if (typeof maybeError.error === "string") {
      return maybeError.error
    }
  }
  return "Request failed. Please try again."
}

const Profile = () => {
  const { data: user } = useGetSessionQuery(undefined)
  const [changeUsername, { isLoading: isChangingUsername }] =
    useChangeUsernameMutation()
  const [changeEmail, { isLoading: isChangingEmail }] = useChangeEmailMutation()
  const [changePassword, { isLoading: isChangingPassword }] =
    useChangePasswordMutation()
  const [setProfilePicture, { isLoading: isSavingProfilePicture }] =
    useSetProfilePictureMutation()

  const [isAvatarModalOpen, setIsAvatarModalOpen] = useState(false)
  const [selectedAvatarUrl, setSelectedAvatarUrl] = useState(AVATAR_OPTIONS[0])
  const [avatarModalError, setAvatarModalError] = useState<string | null>(null)
  const [usernameDraft, setUsernameDraft] = useState("")
  const [emailDraft, setEmailDraft] = useState("")
  const [currentPassword, setCurrentPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmNewPassword, setConfirmNewPassword] = useState("")
  const [usernameError, setUsernameError] = useState<string | null>(null)
  const [emailError, setEmailError] = useState<string | null>(null)
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const [usernameSuccess, setUsernameSuccess] = useState<string | null>(null)
  const [emailSuccess, setEmailSuccess] = useState<string | null>(null)
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null)

  useEffect(() => {
    if (!user) {
      return
    }
    setUsernameDraft(user.username)
    setEmailDraft(user.email)
  }, [user])

  const openAvatarModal = () => {
    setSelectedAvatarUrl(user?.avatarUrl ?? AVATAR_OPTIONS[0])
    setAvatarModalError(null)
    setIsAvatarModalOpen(true)
  }

  const closeAvatarModal = () => {
    if (isSavingProfilePicture) {
      return
    }
    setIsAvatarModalOpen(false)
  }

  const handleSaveAvatar = () => {
    if (isSavingProfilePicture || !selectedAvatarUrl) {
      return
    }
    setAvatarModalError(null)
    void setProfilePicture({ avatarUrl: selectedAvatarUrl })
      .unwrap()
      .then(() => {
        setIsAvatarModalOpen(false)
      })
      .catch((error: unknown) => {
        setAvatarModalError(parseApiError(error))
      })
  }

  const handleSaveUsername = () => {
    if (!user || isChangingUsername) {
      return
    }
    const normalizedUsername = usernameDraft.trim()
    if (!normalizedUsername) {
      setUsernameSuccess(null)
      setUsernameError("Username cannot be empty.")
      return
    }
    if (normalizedUsername === user.username) {
      setUsernameSuccess(null)
      setUsernameError("Enter a new username first.")
      return
    }

    setUsernameError(null)
    setUsernameSuccess(null)
    void changeUsername({ newUsername: normalizedUsername })
      .unwrap()
      .then(response => {
        setUsernameDraft(response.user.username)
        setUsernameSuccess("Username updated successfully.")
      })
      .catch((error: unknown) => {
        setUsernameError(parseApiError(error))
      })
  }

  const handleSaveEmail = () => {
    if (!user || isChangingEmail) {
      return
    }
    const normalizedEmail = emailDraft.trim()
    if (!normalizedEmail) {
      setEmailSuccess(null)
      setEmailError("Email cannot be empty.")
      return
    }
    if (normalizedEmail.toLowerCase() === user.email.toLowerCase()) {
      setEmailSuccess(null)
      setEmailError("Enter a new email first.")
      return
    }

    setEmailError(null)
    setEmailSuccess(null)
    void changeEmail({ newEmail: normalizedEmail })
      .unwrap()
      .then(response => {
        setEmailDraft(response.user.email)
        setEmailSuccess("Email updated successfully.")
      })
      .catch((error: unknown) => {
        setEmailError(parseApiError(error))
      })
  }

  const handleSavePassword = () => {
    if (isChangingPassword) {
      return
    }
    if (!currentPassword || !newPassword || !confirmNewPassword) {
      setPasswordSuccess(null)
      setPasswordError("Please complete all password fields.")
      return
    }
    if (newPassword !== confirmNewPassword) {
      setPasswordSuccess(null)
      setPasswordError("New password and confirmation do not match.")
      return
    }

    setPasswordError(null)
    setPasswordSuccess(null)
    void changePassword({ currentPassword, newPassword })
      .unwrap()
      .then(() => {
        setCurrentPassword("")
        setNewPassword("")
        setConfirmNewPassword("")
        setPasswordSuccess("Password updated successfully.")
      })
      .catch((error: unknown) => {
        setPasswordError(parseApiError(error))
      })
  }

  return (
    <div className={styles.profile}>
      <div className={styles.darkContainer}>
        <div className={styles.content}>
          <div className={styles.formCard}>
            <div className={styles.scrollArea}>
              <div className={styles.field}>
                <span className={styles.label}>Change username:</span>
                <input
                  className={styles.input}
                  value={usernameDraft}
                  onChange={event => {
                    setUsernameDraft(event.target.value)
                    setUsernameError(null)
                    setUsernameSuccess(null)
                  }}
                  placeholder="Your username"
                />
                <button
                  className={styles.actionButton}
                  type="button"
                  onClick={handleSaveUsername}
                  disabled={
                    isChangingUsername ||
                    !user ||
                    !usernameDraft.trim() ||
                    usernameDraft.trim() === user.username
                  }
                >
                  {isChangingUsername ? "Saving..." : "Save username"}
                </button>
                {usernameError ? (
                  <p className={styles.fieldError}>{usernameError}</p>
                ) : null}
                {usernameSuccess ? (
                  <p className={styles.fieldSuccess}>{usernameSuccess}</p>
                ) : null}
              </div>
              <div className={styles.field}>
                <span className={styles.label}>Change email:</span>
                <input
                  className={styles.input}
                  value={emailDraft}
                  onChange={event => {
                    setEmailDraft(event.target.value)
                    setEmailError(null)
                    setEmailSuccess(null)
                  }}
                  placeholder="Your email"
                />
                <button
                  className={styles.actionButton}
                  type="button"
                  onClick={handleSaveEmail}
                  disabled={
                    isChangingEmail ||
                    !user ||
                    !emailDraft.trim() ||
                    emailDraft.trim().toLowerCase() === user.email.toLowerCase()
                  }
                >
                  {isChangingEmail ? "Saving..." : "Save email"}
                </button>
                {emailError ? (
                  <p className={styles.fieldError}>{emailError}</p>
                ) : null}
                {emailSuccess ? (
                  <p className={styles.fieldSuccess}>{emailSuccess}</p>
                ) : null}
              </div>
              <div className={styles.field}>
                <span className={styles.label}>Change password:</span>
                <input
                  className={styles.input}
                  type="password"
                  value={currentPassword}
                  onChange={event => {
                    setCurrentPassword(event.target.value)
                    setPasswordError(null)
                    setPasswordSuccess(null)
                  }}
                  placeholder="Current password"
                />
                <input
                  className={styles.input}
                  type="password"
                  value={newPassword}
                  onChange={event => {
                    setNewPassword(event.target.value)
                    setPasswordError(null)
                    setPasswordSuccess(null)
                  }}
                  placeholder="New password"
                />
                <input
                  className={styles.input}
                  type="password"
                  value={confirmNewPassword}
                  onChange={event => {
                    setConfirmNewPassword(event.target.value)
                    setPasswordError(null)
                    setPasswordSuccess(null)
                  }}
                  placeholder="Confirm new password"
                />
                <button
                  className={styles.actionButton}
                  type="button"
                  onClick={handleSavePassword}
                  disabled={
                    isChangingPassword ||
                    !currentPassword ||
                    !newPassword ||
                    !confirmNewPassword
                  }
                >
                  {isChangingPassword ? "Saving..." : "Save password"}
                </button>
                {passwordError ? (
                  <p className={styles.fieldError}>{passwordError}</p>
                ) : null}
                {passwordSuccess ? (
                  <p className={styles.fieldSuccess}>{passwordSuccess}</p>
                ) : null}
              </div>
              <div className={styles.field}>
                <span className={styles.label}>Change profile picture:</span>
                <button
                  className={styles.actionButton}
                  type="button"
                  onClick={openAvatarModal}
                >
                  Select from avatar gallery
                </button>
              </div>
              <div className={styles.field}>
                <span className={styles.label}>Delete your account:</span>
                <button className={styles.deleteButton} type="button">
                  Delete account
                </button>
              </div>
            </div>
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
              <p className={styles.cardTitle}>Last game points</p>
              <p className={styles.cardAccent}>{user?.lastGamePoints ?? "Error"}</p>
            </div>
          </div>
        </div>
      </div>
      {isAvatarModalOpen ? (
        <div className={styles.modalOverlay} role="presentation">
          <div
            className={styles.avatarModal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="profile-avatar-modal-title"
          >
            <div className={styles.modalHeader}>
              <h3 id="profile-avatar-modal-title" className={styles.modalTitle}>
                Change Profile Picture
              </h3>
              <button
                type="button"
                className={styles.modalCloseButton}
                onClick={closeAvatarModal}
                aria-label="Close avatar picker"
                disabled={isSavingProfilePicture}
              >
                x
              </button>
            </div>
            <div className={styles.modalContent}>
              <div className={styles.modalGridSection}>
                <p className={styles.modalSectionTitle}>Choose an avatar</p>
                <div className={styles.avatarGrid}>
                  {AVATAR_OPTIONS.map((avatarUrl, index) => (
                    <button
                      key={avatarUrl}
                      type="button"
                      className={`${styles.avatarOption} ${
                        avatarUrl === selectedAvatarUrl
                          ? styles.avatarOptionActive
                          : ""
                      }`}
                      onClick={() => {
                        setSelectedAvatarUrl(avatarUrl)
                      }}
                      disabled={isSavingProfilePicture}
                      aria-label={`Select avatar ${String(index + 1)}`}
                    >
                      <img
                        src={avatarUrl}
                        alt={`Avatar ${String(index + 1)}`}
                        className={styles.avatarOptionImage}
                      />
                    </button>
                  ))}
                </div>
                {avatarModalError ? (
                  <p className={styles.modalError}>{avatarModalError}</p>
                ) : null}
              </div>
              <div className={styles.modalPreviewSection}>
                <p className={styles.modalSectionTitle}>Preview</p>
                <div className={styles.previewCard}>
                  <p className={styles.previewCardTitle}>You</p>
                  <div className={styles.previewAvatar}>
                    <img
                      src={selectedAvatarUrl || profilePlaceholder}
                      alt={user?.username ?? "Preview avatar"}
                      className={styles.previewAvatarImage}
                    />
                  </div>
                  <p className={styles.previewCardSubtitle}>
                    #{user?.username ?? "John23"}
                  </p>
                </div>
                <button
                  type="button"
                  className={styles.saveAvatarButton}
                  onClick={handleSaveAvatar}
                  disabled={
                    isSavingProfilePicture ||
                    !selectedAvatarUrl ||
                    selectedAvatarUrl === (user?.avatarUrl ?? "")
                  }
                >
                  {isSavingProfilePicture ? "Saving..." : "Save avatar"}
                </button>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}

export default Profile
