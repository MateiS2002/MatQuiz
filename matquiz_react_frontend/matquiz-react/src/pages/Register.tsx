import { useState } from "react"
import type { SyntheticEvent } from "react"
import { Link, useNavigate } from "react-router-dom"
import { useRegisterMutation } from "@/features/auth/api/authApiSlice"
import styles from "./Register.module.css"
import { ROUTES } from "@/routes/paths"

const parseRegisterError = (value: unknown) => {
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
  return "Register failed. Please try again."
}

const Register = () => {
  const navigate = useNavigate()
  const [register, { isLoading, error }] = useRegisterMutation()
  const [username, setUsername] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")

  const handleSubmit = async (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (password !== confirmPassword) {
      return
    }
    try {
      await register({ username, email, password }).unwrap()
      void navigate(ROUTES.login)
    } catch {
      // Error handled by RTK Query state.
    }
  }

  const passwordsMatch =
    password === confirmPassword || confirmPassword.length === 0
  const registerErrorMessage = error ? parseRegisterError(error) : null

  return (
    <div className={styles.register}>
      <div className={styles.darkContainer}>
        <div className={styles.formCard}>
          <form
            className={styles.form}
            id="register-form"
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
                placeholder="name"
                value={username}
                onChange={event => {
                  setUsername(event.target.value)
                }}
                required
              />
            </label>
            <label className={styles.field}>
              E-mail
              <input
                className={styles.input}
                type="email"
                name="email"
                autoComplete="email"
                placeholder="example@matquiz.com"
                value={email}
                onChange={event => {
                  setEmail(event.target.value)
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
                autoComplete="new-password"
                placeholder="****************"
                value={password}
                onChange={event => {
                  setPassword(event.target.value)
                }}
                required
              />
            </label>
            <label className={styles.field}>
              Re-type password
              <input
                className={styles.input}
                type="password"
                name="confirmPassword"
                autoComplete="new-password"
                placeholder="****************"
                value={confirmPassword}
                onChange={event => {
                  setConfirmPassword(event.target.value)
                }}
                required
              />
            </label>
            {!passwordsMatch ? (
              <p className={styles.error}>Passwords do not match.</p>
            ) : null}
            {registerErrorMessage ? (
              <p className={styles.error}>{registerErrorMessage}</p>
            ) : null}
            <p className={styles.termsText}>
              By registering you agree to the{" "}
              <Link className={styles.termsLink} to={ROUTES.terms}>
                Terms and Conditions
              </Link>
              .
            </p>
          </form>
        </div>
        <div className={styles.actions}>
          <button
            className={styles.primaryButton}
            type="submit"
            form="register-form"
            disabled={isLoading}
          >
            {isLoading ? "Creating..." : "Register"}
          </button>
        </div>
      </div>
    </div>
  )
}

export default Register
