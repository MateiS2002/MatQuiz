import { useMemo, useState } from "react"
import type { SyntheticEvent } from "react"
import { Link } from "react-router-dom"
import { useGetSessionQuery } from "@/features/auth/api/authApiSlice"
import { useSendContactEmailMutation } from "@/features/contact/api/contactApiSlice"
import profilePlaceholder from "@/assets/profile-placeholder.png"
import { ROUTES } from "@/routes/paths"
import { parseApiErrorMessage } from "@/utils/apiError"
import styles from "./Contact.module.css"

const resolveClientVersion = () => {
  const normalized = __APP_VERSION__.trim()
  if (normalized.length === 0) {
    return "unknown"
  }

  const semverMatch = normalized.match(/\d+\.\d+\.\d+/)
  const candidate = semverMatch?.[0] ?? normalized
  return candidate.slice(0, 10)
}

const Contact = () => {
  const { data: user } = useGetSessionQuery(undefined)
  const [sendContactEmail, { isLoading, error }] = useSendContactEmailMutation()
  const [topic, setTopic] = useState("")
  const [message, setMessage] = useState("")
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const clientVersion = useMemo(resolveClientVersion, [])

  const errorMessage = useMemo(() => {
    if (!error) {
      return null
    }
    return parseApiErrorMessage(error, "Could not send your message. Please try again.")
  }, [error])

  const handleSubmit = async (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!user) {
      return
    }
    setSuccessMessage(null)

    try {
      const response = await sendContactEmail({
        email: user.email,
        nickname: user.username,
        topic,
        message,
        appVersion: clientVersion,
      }).unwrap()
      setSuccessMessage(response.message)
      setTopic("")
      setMessage("")
    } catch {
      setSuccessMessage(null)
      // Error handled by RTK Query state.
    }
  }

  return (
    <div className={styles.contact}>
      <div className={styles.darkContainer}>
        <div className={styles.content}>
          <div className={styles.mainCard}>
            <form
              className={styles.form}
              id="contact-form"
              onSubmit={event => {
                void handleSubmit(event)
              }}
            >
              <div className={styles.field}>
                <span className={styles.label}>Topic:</span>
                <input
                  className={styles.input}
                  placeholder="ex. contact, bug report"
                  value={topic}
                  minLength={3}
                  maxLength={100}
                  onChange={event => {
                    setTopic(event.target.value)
                    setSuccessMessage(null)
                  }}
                  required
                />
              </div>
              <div className={styles.field}>
                <span className={styles.label}>Message:</span>
                <textarea
                  className={styles.textarea}
                  placeholder="ex. I cannot see my score in leaderboard"
                  rows={6}
                  value={message}
                  minLength={10}
                  maxLength={1000}
                  onChange={event => {
                    setMessage(event.target.value)
                    setSuccessMessage(null)
                  }}
                  required
                />
              </div>
              <p className={styles.note}>
                *The message will include your username and email.
              </p>
              {errorMessage ? <p className={styles.error}>{errorMessage}</p> : null}
              {successMessage ? <p className={styles.success}>{successMessage}</p> : null}
              <button className={styles.sendButton} type="submit" disabled={isLoading || !user}>
                {isLoading ? "Sending..." : "Send"}
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
            <Link className={styles.profileButton} to={ROUTES.profile}>
              My profile
            </Link>
            <div className={styles.versionCard}>Version {__APP_VERSION__}</div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Contact
