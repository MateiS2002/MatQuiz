import { useNavigate } from "react-router-dom"
import { useAppSelector } from "@/app/hooks"
import styles from "./Home.module.css"
import { ROUTES, type RoutePath } from "@/routes/paths"

const Home = () => {
  const navigate = useNavigate()
  const token = useAppSelector(state => state.auth.token)
  const isLoggedIn = Boolean(token)

  const requireLogin = (path: RoutePath) => {
    if (!isLoggedIn) {
      void navigate(ROUTES.login, { state: { from: ROUTES.home } })
      return
    }
    void navigate(path)
  }

  return (
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
  )
}

export default Home
