import { Route, Routes } from "react-router-dom"
import Home from "@/pages/Home"
import Login from "@/pages/Login"
import Logout from "@/pages/Logout"
import Register from "@/pages/Register"
import Terms from "@/pages/Terms"
import Profile from "@/pages/Profile"
import Settings from "@/pages/Settings"
import Contact from "@/pages/Contact"
import Leaderboard from "@/pages/Leaderboard"
import GameControl from "@/pages/GameControl"
import Join from "@/pages/Join"
import Create from "@/pages/Create"
import Lobby from "@/pages/Lobby"
import { ROUTES } from "@/routes/paths"
import RequireAuth from "@/routes/RequireAuth"

const AppRoutes = () => (
  <Routes>
    <Route
      path={ROUTES.home}
      element={<Home />}
    />
    <Route path={ROUTES.login} element={<Login />} />
    <Route path={ROUTES.register} element={<Register />} />
    <Route path={ROUTES.terms} element={<Terms />} />
    <Route
      path={ROUTES.profile}
      element={
        <RequireAuth>
          <Profile />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.settings}
      element={
        <RequireAuth>
          <Settings />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.contact}
      element={
        <RequireAuth>
          <Contact />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.leaderboard}
      element={
        <RequireAuth>
          <Leaderboard />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.gameControl}
      element={
        <RequireAuth>
          <GameControl />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.join}
      element={
        <RequireAuth>
          <Join />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.create}
      element={
        <RequireAuth>
          <Create />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.lobby}
      element={
        <RequireAuth>
          <Lobby />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.logout}
      element={
        <RequireAuth>
          <Logout />
        </RequireAuth>
      }
    />
  </Routes>
)

export default AppRoutes
