import { useState } from "react"
import type { SyntheticEvent } from "react"
import { Link } from "react-router-dom"
import { useLocation, useNavigate } from "react-router-dom"
import { useLoginMutation } from "@/features/auth/api/authApiSlice"
import styles from "./Login.module.css"
import { ROUTES, type RoutePath } from "@/routes/paths"

type LocationState = {
  from?: RoutePath
}

const Login = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const [login, { isLoading, error }] = useLoginMutation()
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")

  const from = (location.state as LocationState | null)?.from ?? ROUTES.home

  const handleSubmit = async (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault()
    try {
      await login({ username, password }).unwrap()
      void navigate(from)
    } catch {
      // Error handled by RTK Query state.
    }
  }

  return (
    <div className={styles.login}>
      <div className={styles.darkContainer}>
        <div className={styles.formCard}>
          <form
            className={styles.form}
            id="login-form"
            onSubmit={event => {
              void handleSubmit(event)
            }}
          >
            <label className={styles.field}>
              Username
              <input
                className={styles.input}
                name="username"
                autoComplete="username"
                placeholder=""
                value={username}
                onChange={event => {
                  setUsername(event.target.value)
                }}
                required
              />
            </label>
            <label className={styles.field}>
              Password
              <input
                className={styles.input}
                type="password"
                name="password"
                autoComplete="current-password"
                placeholder="****************"
                value={password}
                onChange={event => {
                  setPassword(event.target.value)
                }}
                required
              />
            </label>
            {error ? (
              <p className={styles.error}>
                Login failed. Check your credentials.
              </p>
            ) : null}
          </form>
        </div>
        <div className={styles.actions}>
          <button
            className={styles.primaryButton}
            type="submit"
            form="login-form"
            disabled={isLoading}
          >
            {isLoading ? "Signing in..." : "Login"}
          </button>
          <Link className={styles.secondaryButton} to={ROUTES.register}>
            Register
          </Link>
        </div>
      </div>
    </div>
  )
}

export default Login
