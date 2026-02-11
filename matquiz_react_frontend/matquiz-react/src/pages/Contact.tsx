import { Link } from "react-router-dom"
import { useGetSessionQuery } from "@/features/auth/api/authApiSlice"
import profilePlaceholder from "@/assets/profile-placeholder.png"
import { ROUTES } from "@/routes/paths"
import styles from "./Contact.module.css"

const Contact = () => {
  const { data: user } = useGetSessionQuery(undefined)

  return (
    <div className={styles.contact}>
      <div className={styles.darkContainer}>
        <div className={styles.content}>
          <div className={styles.mainCard}>
            <div className={styles.field}>
              <span className={styles.label}>Topic:</span>
              <input
                className={styles.input}
                placeholder="ex. contact, bug report"
              />
            </div>
            <div className={styles.field}>
              <span className={styles.label}>Message:</span>
              <textarea
                className={styles.textarea}
                placeholder="ex. I cannot see my score in leaderboard"
                rows={6}
              />
            </div>
            <p className={styles.note}>
              *The message will include you username
            </p>
            <button className={styles.sendButton} type="button">
              Send
            </button>
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
