import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { useAppSelector } from "@/app/hooks"
import InfoModal from "@/components/InfoModal"
import styles from "./Home.module.css"
import { ROUTES, type RoutePath } from "@/routes/paths"

const DEPLOYMENT_NOTICE_STORAGE_KEY = "matquiz_home_testing_notice_seen"

const Home = () => {
  const navigate = useNavigate()
  const token = useAppSelector(state => state.auth.token)
  const isLoggedIn = Boolean(token)
  const [isDeploymentNoticeOpen, setIsDeploymentNoticeOpen] = useState(false)

  useEffect(() => {
    const hasSeenNotice =
      window.localStorage.getItem(DEPLOYMENT_NOTICE_STORAGE_KEY) === "true"
    if (!hasSeenNotice) {
      setIsDeploymentNoticeOpen(true)
      window.localStorage.setItem(DEPLOYMENT_NOTICE_STORAGE_KEY, "true")
    }
  }, [])

  const requireLogin = (path: RoutePath) => {
    if (!isLoggedIn) {
      void navigate(ROUTES.login, { state: { from: ROUTES.home } })
      return
    }
    void navigate(path)
  }

  return (
    <>
      <div className={styles.home}>
        <h1 className={styles.title}>Welcome</h1>
        <p className={styles.subtitle}>
          Play a quick game of MatQuiz and have fun with your friends. Try out
          this AI Generated quizzes game right now!
        </p>
        <div className={styles.actions}>
          <button
            className={styles.primaryButton}
            type="button"
            onClick={() => {
              requireLogin(ROUTES.gameControl)
            }}
          >
            Join
          </button>
          <button
            className={styles.secondaryButton}
            type="button"
            onClick={() => {
              requireLogin(ROUTES.gameControl)
            }}
          >
            Create game
          </button>
        </div>
      </div>
      <InfoModal
        open={isDeploymentNoticeOpen}
        title="Testing Deployment Notice"
        message={
          "This is a testing deployment of MatQuiz.\n\nSome features are still under development and may not be available yet.\n\nIf you encounter issues, it is likely because the app is still in active development."
        }
        sizePreset="startup"
        onClose={() => {
          setIsDeploymentNoticeOpen(false)
        }}
      />
    </>
  )
}

export default Home
