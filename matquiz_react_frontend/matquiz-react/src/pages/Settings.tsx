import { Link } from "react-router-dom"
import { useGetSessionQuery } from "@/features/auth/api/authApiSlice"
import profilePlaceholder from "@/assets/profile-placeholder.png"
import { ROUTES } from "@/routes/paths"
import styles from "./Settings.module.css"

const Settings = () => {
  const { data: user } = useGetSessionQuery(undefined)

  return (
    <div className={styles.settings}>
      <div className={styles.darkContainer}>
        <div className={styles.content}>
          <div className={styles.mainCard}>
            <div className={styles.topActions}>
              <button className={styles.iconButton} type="button">
                Mute Sound
              </button>
              <a
                className={styles.iconButton}
                href="https://github.com/MateiS2002/MatQuiz"
                target="_blank"
                rel="noreferrer"
              >
                Github
              </a>
            </div>
            <p className={styles.description}>
              This mini-game is my portfolio project and it is open-source, all
              the info about the architecture is on my Github profile so if you
              have any ideas or suggestions you can check my contact form. If
              you enjoyed the game please give me a follow on Github.
            </p>
            <Link className={styles.contactButton} to={ROUTES.contact}>
              Contact / Bug-report
            </Link>
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

export default Settings
