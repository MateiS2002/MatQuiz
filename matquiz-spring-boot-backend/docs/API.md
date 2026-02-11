
# MatQuiz API Documentation

## WebSocket Connection
All game-related communication happens via **STOMP over WebSockets**.

**Connection Details:**
- **Endpoint:** `ws://localhost:8080/ws` (SockJS enabled)
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **App Prefix:** `/app`
- **Broker Prefixes:** `/topic` (public broadcasts), `/queue` (private messages)

---

## WebSocket Endpoints

### 1. Create Room
**Host creates a new game lobby.**

- **Destination:** `/app/create`
- **Payload:** `{}` (empty)
- **Private Response:** `/user/queue/created` → `GameRoomDto`
- **Behavior:**
    - Creates a new lobby with a random 5-character room code.
    - Registers the host as the first player.
    - Removes host from any previous active rooms.

---

### 2. Join Room
**Player joins an existing lobby.**

- **Destination:** `/app/join`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12"
  }
  ```
- **Private Response:** `/user/queue/joined` → `GameRoomDto`
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Room code is normalized to uppercase.
    - Cannot join rooms with status `PLAYING` or `FINISHED`.
    - Max 5 players per room.
    - If player was previously in the room, reconnects them.
    - Removes player from any other active rooms.

---

### 3. Reconnect
**Player attempts to reconnect to an active game after disconnect/refresh.**

- **Destination:** `/app/reconnect`
- **Payload:** `{}` (empty)
- **Private Response:** `/user/queue/reconnected` → `GameRoomDto` (if active game found)
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Searches for the user's most recent active game (excludes `FINISHED`).
    - Updates session ID and sets `isConnected = true`.
    - If no active game, returns nothing.

---

### 4. Leave Room
**Player explicitly leaves a room.**

- **Destination:** `/app/leave`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12"
  }
  ```
- **Private Response:** `/user/queue/left` → `String` (room code)
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto` (if room still exists)
- **Behavior:**
    - Removes player from the room.
    - If the host leaves:
        - **Empty room:** Room is deleted.
        - **Players remain:** Host is transferred to the next player.
    - If last player leaves, room is deleted.

---

### 5. Generate Quiz
**Host requests AI to generate questions for the room.**

- **Destination:** `/app/generate`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12",
    "topic": "Mathematics",
    "difficulty": "EASY"
  }
  ```
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Only the host can generate the quiz.
    - Room status must be `WAITING`.
    - `topic` length must be at most 30 characters.
    - `difficulty` must be `EASY` or `ADVANCED`.
    - Room status changes to `GENERATING`, then to `READY` when complete.
    - 5 questions are generated and stored.

---

### 6. Start Game
**Host starts the game (transitions from READY to PLAYING).**

- **Destination:** `/app/startGame`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12"
  }
  ```
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Room status must be `READY`.
    - Status changes to `PLAYING`.
    - Host can now request questions.

---

### 7. Request Question
**Host requests the next question to be sent to all players.**

- **Destination:** `/app/requestQuestion`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12"
  }
  ```
- **Public Broadcast:** `/topic/room/{roomCode}` → `QuestionDto`
- **Behavior:**
    - Only the host can request questions.
    - Room status must be `PLAYING`.
    - Sends the current question based on `currentQuestionIndex`.
    - Increments the question index.
    - Records the question start time server-side.
    - Schedules an automatic reveal after 30 seconds (+2s buffer).
    - If all players answer early, reveal triggers immediately.
    - When the final question is revealed, the game is finished and a final `GameRoomDto` is broadcast.

---

### 8. Submit Answer
**Player submits their answer to the current question.**

- **Destination:** `/app/submitAnswer`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12",
    "questionId": 123,
    "selectedAnswerIndex": 2,
    "submissionTime": "2026-01-17T14:30:00Z"
  }
  ```
- **Private Response:** `/user/queue/submitAck` → `String` ("OK")
- **Public Broadcast:** `/topic/room/{roomCode}/progress` → `AnswerProgressDto`
- **Behavior:**
    - Room status must be `PLAYING`.
    - Player can only answer each question once.
    - Answer index must be valid (0-3).
    - Points awarded: 50-100 for correct based on speed, 0 for incorrect.
    - Player score is updated immediately.
    - Time taken is calculated server-side from `question.postedAt` to current server time.
    - `submissionTime` is ignored by the server (kept for client-side logging).

---

### 9. Request End Game Results
**Host requests the final results after the game is finished.**

- **Destination:** `/app/endResults`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12"
  }
  ```
- **Public Broadcast:** `/topic/room/{roomCode}` → `ResultsDto`
- **Behavior:**
    - Only the host can request end game results.
    - Returns the final leaderboard with all player scores.
    - Includes the end time of the game.

---

### 10. End Game Early
**Host terminates the game before all questions are answered.**

- **Destination:** `/app/endGame`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12"
  }
  ```
- **Public Broadcast:** `/topic/room/{roomCode}` → `String` ("END_GAME_EARLY")
- **Behavior:**
    - Only the host can end the game early.
    - Cannot end a game that is already `FINISHED`.
    - Broadcasts "END_GAME_EARLY" message to all players before deletion.
    - The room is deleted from the database.
    - All players are removed from the room.

---

### 11. Automatic Events (Server-Initiated)

#### Auto Reveal (Timed or All Answered)
**When a question times out (30s + 2s buffer) or all players have answered.**

- **Public Broadcast:** `/topic/room/{roomCode}/reveal` → `CorrectAnswerDto`
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto` (updated scores)
- **Behavior:**
    - Identifies players who haven't answered the question.
    - Automatically assigns 0 points to players who didn't answer.
    - Sends a "FAILED ANSWER" message to each player who missed the question via `/user/queue/failed_answer`.
    - Broadcasts the correct answer to all players.
    - Broadcasts updated room state with final scores for the question.
    - If this was the last question, the game is finished and a final `GameRoomDto` is broadcast.

#### Disconnect Event
**When a player's WebSocket connection closes.**

- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Player's `isConnected` status is set to `false`.
    - Room state is broadcast to all remaining players.
    - Player data is retained (they can reconnect).

#### Failed Answer Notification
**When a player misses answering a question (auto reveal).**

- **Private Response:** `/user/queue/failed_answer` → `String` ("FAILED ANSWER")
- **Behavior:**
    - Sent to each player who didn't answer a question when the auto reveal happens.
    - Indicates that 0 points were automatically assigned for that question.

#### Quiz Generation Timeout
**When quiz generation remains in `GENERATING` beyond the timeout window.**

- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto` (status reset to `WAITING`)
- **Private Response (Host):** `/user/queue/timeout` → `String` (room code)
- **Behavior:**
    - The room is reset from `GENERATING` back to `WAITING`.
    - The host is notified privately so the client can show a timeout popup.
    - Players receive updated room state through the room topic broadcast.

#### Error Messages
**When an action fails.**

- **Private Response:** `/user/queue/errors` → `String` (error message)
- **Examples:**
    - "Room not found"
    - "Only the host can generate the quiz!"
    - "Topic cannot be longer than 30 characters!"
    - "You have already answered this question!"
    - "Only the host can request correct answers!"
    - "Only the host can request room results!"
    - "Only the host can end the game early!"
    - "Game is already finished!"

---

## REST Authentication
**Base URL:** `/api/auth`

### 1. Register
- **Endpoint:** `POST /api/auth/register`
- **Payload:**
  ```json
  {
    "username": "matei",
    "password": "securePass123",
    "email": "matei@example.com"
  }
  ```
- **Success Response:** `201 Created`
  - Body: `true`
- **Error Responses:**
  - `409 Conflict` (duplicate username/email)
    ```json
    {
      "message": "This username is already taken."
    }
    ```
    ```json
    {
      "message": "This email is already in use."
    }
    ```
  - `400 Bad Request` (invalid payload / missing required fields)

---

### 2. Login
- **Endpoint:** `POST /api/auth/login`
- **Payload:**
  ```json
  {
    "username": "matei",
    "password": "securePass123"
  }
  ```
- **Response:** `String` (JWT Token)

---

### 3. Get Session
- **Endpoint:** `GET /api/auth/session`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Response:**
  ```json
  {
    "id": 1,
    "username": "matei",
    "email": "matei@example.com",
    "role": "ROLE_USER",
    "eloRating": 1000,
    "lastGamePoints": 0,
    "avatarUrl": "https://..."
  }
  ```

---

### 4. Set Profile Picture
- **Endpoint:** `PATCH /api/auth/setProfilePicture`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Payload:**
  ```json
  {
    "avatarUrl": "https://media.mateistanescu.ro/7.png"
  }
  ```
- **Response:** `UserSummaryDto`

---

### 5. Change Username
- **Endpoint:** `PATCH /api/auth/changeUsername`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Payload:**
  ```json
  {
    "newUsername": "matei_2026"
  }
  ```
- **Response:** `SessionRefreshDto`
  ```json
  {
    "accessToken": "<JWT_TOKEN>",
    "user": {
      "id": 1,
      "username": "matei_2026",
      "email": "matei@example.com",
      "role": "ROLE_USER",
      "eloRating": 1000,
      "lastGamePoints": 0,
      "avatarUrl": "https://media.mateistanescu.ro/7.png"
    }
  }
  ```

---

### 6. Change Email
- **Endpoint:** `PATCH /api/auth/changeEmail`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Payload:**
  ```json
  {
    "newEmail": "matei.2026@example.com"
  }
  ```
- **Response:** `SessionRefreshDto`

---

### 7. Change Password
- **Endpoint:** `PATCH /api/auth/changePassword`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Payload:**
  ```json
  {
    "currentPassword": "OldPassword123",
    "newPassword": "NewPassword123"
  }
  ```
- **Response:** `SessionRefreshDto`

**Validation Rules:**
- `newPassword` must be 8-72 chars and include uppercase, lowercase, and a digit.
- `newPassword` must be different from the current password.

---

### 8. Get Leaderboard (All Players)
- **Endpoint:** `GET /api/auth/leaderboard`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Response:**
  ```json
  [
    {
      "rank": 1,
      "username": "matei",
      "eloRating": 1500
    },
    {
      "rank": 2,
      "username": "john",
      "eloRating": 1200
    }
  ]
  ```

---

### 9. Get Leaderboard (Specific User)
- **Endpoint:** `POST /api/auth/leaderboard`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Payload:**
  ```json
  {
    "username": "matei"
  }
  ```
- **Response:**
  ```json
  [
    {
      "rank": 1,
      "username": "matei",
      "eloRating": 1500
    }
  ]
  ```

---

### 10. Who Am I
- **Endpoint:** `GET /api/auth/whoami`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Response:** `String`
  ```
  You are: matei with authorities [ROLE_USER]
  ```

---

### 11. Has Active Game
- **Endpoint:** `GET /api/auth/active`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Response:**
  ```json
  {
    "hasActiveGame": true
  }
  ```

**Notes:**
- Returns a lightweight boolean used by the frontend `Game Control` page.
- If `hasActiveGame` is `true`, the client should call `/app/reconnect` to restore full room state.

---

## Client Integration Notes

### Browser-Level Exit Protection (Recommended)
When a user is inside the game flow (`/game-control`, `/join`, `/create`, `/lobby`), the frontend should guard exits to avoid accidental disconnects that are not communicated as explicit room leaves.

- **Guarded actions:** logo click, navbar navigation, back buttons, route switches, browser refresh/tab close.
- **UX recommendation:** show a confirmation modal before exiting the flow.
- **If user confirms and room code is known:**
  1. Send `LeaveRoomRequest` to `/app/leave`.
  2. Wait for `/user/queue/left` confirmation.
  3. Navigate away only after leave confirmation (or a short timeout fallback).
- **If browser closes/reloads abruptly:** an explicit `/app/leave` may not complete; rely on:
  - `SessionDisconnectEvent` handling on backend (marks player disconnected), and
  - reconnect flow on next load (`GET /api/auth/active` then `/app/reconnect` if active).

This pattern keeps room membership consistent and prevents "ghost connected" states from pure client-side navigation.

---

## Data Structures (DTOs)

### `GameRoomDto`
**Represents the full state of a game room.**

```json
{
  "roomCode": "ABC12",
  "topic": "Mathematics",
  "difficulty": "EASY",
  "status": "PLAYING",
  "host": {
    "username": "matei",
    "avatarUrl": "https://..."
  },
  "players": [
    {
      "nickname": "matei",
      "score": 100,
      "isConnected": true,
      "avatarUrl": "https://..."
    },
    {
      "nickname": "john",
      "score": 50,
      "isConnected": false,
      "avatarUrl": "https://..."
    }
  ]
}
```

**Status Values:**
- `WAITING` – Room created, waiting for quiz generation
- `GENERATING` – AI is generating questions
- `READY` – Quiz ready, waiting for host to start
- `PLAYING` – Game in progress
- `FINISHED` – All questions answered

---

### `QuestionDto`
**Represents a single quiz question (without the correct answer).**

```json
{
  "questionId": 42,
  "question_text": "What is 2 + 2?",
  "answers": ["3", "4", "5", "6"],
  "order_index": 1
}
```

**Notes:**
- `correctIndex` is **not included** to prevent cheating.
- `answers` is a 4-element array of strings.
- `order_index` indicates the question number in the quiz.

---

### `AnswerResultDto`
**(Deprecated)** Not currently sent by the server.

---

### `AnswerProgressDto`
**Broadcast when a player submits an answer.**

```json
{
  "nickname": "matei",
  "answered": true
}
```

---

### `CorrectAnswerDto`
**Sent when the host reveals the correct answer for a question.**

```json
{
  "questionId": 42,
  "correctAnswer": 1
}
```

**Notes:**
- Contains the correct answer index for the specified question.
- Broadcast to all players in the room when the host requests the correct answer.

---

### `ResultsDto`
**Sent when the host requests the final game results.**

```json
{
  "endTime": "2026-01-17T15:30:00",
  "players": [
    {
      "nickname": "matei",
      "score": 500,
      "isConnected": true,
      "avatarUrl": "https://..."
    },
    {
      "nickname": "john",
      "score": 300,
      "isConnected": true,
      "avatarUrl": "https://..."
    }
  ]
}
```

**Notes:**
- `endTime` is the timestamp when the results were requested.
- `players` is an array of all players with their final scores.

---

### `LeaderboardDto`
**Represents a single entry in the global leaderboard.**

```json
{
  "rank": 1,
  "username": "matei",
  "eloRating": 1500
}
```

**Notes:**
- `rank` is the player's position in the leaderboard.
- `eloRating` is the player's current ELO rating.

---

### `ActiveGameDto`
**Represents whether the authenticated user currently has an active game.**

```json
{
  "hasActiveGame": true
}
```

**Notes:**
- `true` means the user has at least one game where room status is not `FINISHED`.
- Intended for REST pre-checks before attempting WebSocket reconnect.

---

### `GamePlayerDto`
**Represents a player in the room.**

```json
{
  "nickname": "matei",
  "score": 150,
  "isConnected": true,
  "avatarUrl": "https://..."
}
```

---

### `UserSummaryDto`
**Minimal user info (used for host display).**

```json
{ 
  "id": 1,
  "username": "matei",
  "email": "example@gmail.com",
  "role": "ROLE_USER",
  "eloRating": 1000,
  "lastGamePoints": 0,
  "avatarUrl": "https://..."
}
```

---

### `SessionRefreshDto`
**Returned after account updates that re-issue a JWT.**

```json
{
  "accessToken": "<JWT_TOKEN>",
  "user": {
    "id": 1,
    "username": "matei",
    "email": "matei@example.com",
    "role": "ROLE_USER",
    "eloRating": 1000,
    "lastGamePoints": 0,
    "avatarUrl": "https://media.mateistanescu.ro/4.png"
  }
}
```

---

## Request DTOs

### `JoinRoomRequest`
```json
{
  "roomCode": "ABC12"
}
```

### `LeaveRoomRequest`
```json
{
  "roomCode": "ABC12"
}
```

### `GenerateQuizRequest`
```json
{
  "roomCode": "ABC12",
  "topic": "Science",
  "difficulty": "ADVANCED"
}
```

**Difficulty Options:** `EASY`, `ADVANCED`

### `StartGameRequest`
```json
{
  "roomCode": "ABC12"
}
```

### `QuestionRequest`
```json
{
  "roomCode": "ABC12"
}
```

### `AnswerSubmissionRequest`
```json
{
  "roomCode": "ABC12",
  "questionId": 123,
  "selectedAnswerIndex": 2,
  "submissionTime": "2026-01-17T14:30:00Z"
}
```

---

### `ResultsRequest`
```json
{
  "roomCode": "ABC12"
}
```

---

### `EndGameEarlyRequest`
```json
{
  "roomCode": "ABC12"
}
```

---

### `LeaderboardRequestDto`
```json
{
  "username": "matei"
}
```

**Notes:**
- Used when requesting a specific user's leaderboard entry.
- If `username` is `null`, the full leaderboard is returned (use GET endpoint instead).

### `SetProfilePictureRequestDto`
```json
{
  "avatarUrl": "https://media.mateistanescu.ro/9.png"
}
```

### `ChangeUsernameRequestDto`
```json
{
  "newUsername": "matei_2026"
}
```

### `ChangeEmailRequestDto`
```json
{
  "newEmail": "matei.2026@example.com"
}
```

### `ChangePasswordRequestDto`
```json
{
  "currentPassword": "OldPassword123",
  "newPassword": "NewPassword123"
}
```

---

## Game Flow Example

### Standard Game Flow
1. **Matei** creates a room → Receives `GameRoomDto` (status: `WAITING`)
2. **John** joins via `/app/join` → Both receive updated `GameRoomDto`
3. **Matei** generates quiz → Status changes to `GENERATING`, then `READY`
4. **Matei** starts game → Status changes to `PLAYING`
5. **Matei** requests question → All players receive `QuestionDto`
6. **John** submits answer → Receives `submitAck`, all players see `AnswerProgressDto`
7. Auto reveal triggers → All players receive `CorrectAnswerDto`, players who missed get "FAILED ANSWER"
8. Repeat step 5-7 for all questions
9. After last reveal → Status changes to `FINISHED`, final `GameRoomDto` with leaderboard
10. **Matei** requests end results → All players receive `ResultsDto` with final leaderboard

### Early Termination Flow
1. **Matei** creates a room → Receives `GameRoomDto` (status: `WAITING`)
2. **John** joins via `/app/join` → Both receive updated `GameRoomDto`
3. **Matei** generates quiz → Status changes to `GENERATING`, then `READY`
4. **Matei** starts game → Status changes to `PLAYING`
5. **Matei** requests question → All players receive `QuestionDto`
6. **John** submits answer → Receives `submitAck`, all players see `AnswerProgressDto`
7. **Matei** ends game early → All players receive "END_GAME_EARLY" message, room is deleted

### Reconnection Flow
1. **Matei** is in an active game and disconnects
2. **Matei** reconnects via `/app/reconnect` → Receives `GameRoomDto` of active game
3. All players in the room receive updated `GameRoomDto` with `isConnected: true` for Matei

---

## Error Handling

All errors are sent to `/user/queue/errors` as plain `String` messages.

**Common Errors:**
- `"Room not found"`
- `"Only the host can generate the quiz!"`
- `"You cannot join a game that is already PLAYING"`
- `"Invalid question index: 5"`
- `"You have already answered this question!"`
- `"Only the host can request correct answers!"`
- `"Only the host can request room results!"`
- `"Only the host can end the game early!"`
- `"Game is already finished!"`
- `"Game cannot be started because it is not in READY state!"`
- `"Questions cannot be fetched because the room is not in PLAYING state!"`
- `"Cannot submit answers. Game is not in PLAYING state!"`
- `"Player not found in this room!"`
- `"Topic cannot be empty!"`
- `"Difficulty cannot be empty!"`
- `"Only EASY and ADVANCED difficulties are supported!"`
- `"The quiz can be only generated when the room is waiting! Another quiz might have been generated already."`
- `"Room is full! (Max 5 players)"`
- `"Player is not in this room!"`
- `"User not found"`

**Automatic Notifications:**
- `"FAILED ANSWER"` → Sent to `/user/queue/failed_answer` when a player misses answering a question
- `"END_GAME_EARLY"` → Sent to `/topic/room/{roomCode}` when the host terminates the game early

---

## Notes for Frontend

### WebSocket Connection
- Always normalize room codes to **uppercase** before sending.
- Subscribe to `/topic/room/{roomCode}` to receive real-time updates.
- Subscribe to `/user/queue/created` to receive confirmation when creating a room.
- Subscribe to `/user/queue/joined` to receive confirmation when joining a room.
- Subscribe to `/user/queue/reconnected` to receive confirmation when reconnecting.
- Subscribe to `/user/queue/left` to receive confirmation when leaving a room.
- Subscribe to `/user/queue/submitAck` to receive confirmation after submitting.
- Subscribe to `/user/queue/failed_answer` to receive notifications when missing a question.
- Subscribe to `/user/queue/errors` to receive error messages.
- Subscribe to `/topic/room/{roomCode}/progress` to show who has answered.
- Subscribe to `/topic/room/{roomCode}/reveal` to show the correct answer.

### Game Flow
- The `QuestionDto` does **not** include the correct answer—it's only revealed in `CorrectAnswerDto` during auto reveal.
- Player `isConnected` status updates in real-time when users disconnect.
- `submissionTime` is currently ignored by the server.
- After auto reveal, players who missed the question will receive a "FAILED ANSWER" notification and 0 points are automatically assigned.
- The game automatically finishes after the final reveal (or after the 35-second last-question timeout).
- The host can end the game early at any time using `/app/endGame`, which will delete the room.

### Question Timing
- Questions have a 30-second time limit (auto reveal at ~32s).
- Time taken is calculated server-side from `question.postedAt`.
- If a player doesn't answer within the time limit, they receive 0 points when the auto reveal happens.

### Leaderboard
- Use `GET /api/auth/leaderboard` to retrieve the full global leaderboard.
- Use `POST /api/auth/leaderboard` with a username to retrieve a specific user's ranking.
- Leaderboard is based on ELO ratings.

### Error Handling
- Always subscribe to `/user/queue/errors` to handle error cases gracefully.
- Display user-friendly error messages based on the received error strings.
- Handle the "END_GAME_EARLY" message by redirecting users to the lobby or home screen.

### Session Management
- Use `GET /api/auth/session` to retrieve the current user's session information.
- Use `GET /api/auth/active` to quickly check if the user has an active room before reconnecting.
- Use `GET /api/auth/whoami` to verify authentication and get user details.
- Use `PATCH /api/auth/changeUsername`, `PATCH /api/auth/changeEmail`, and `PATCH /api/auth/changePassword` for account updates.
- Replace the stored JWT with `accessToken` returned by `SessionRefreshDto` after account updates.
- Store the JWT token securely and include it in the WebSocket connection headers.

### Room Management
- Maximum 5 players per room.
- Players cannot join rooms with status `PLAYING` or `FINISHED`.
- When the host leaves, the host is transferred to the next player.
- When the last player leaves, the room is deleted.
- Use REST `GET /api/auth/active` for active-room presence checks.
- Players can reconnect to active games using `/app/reconnect`.

### Quiz Generation
- Only the host can generate the quiz.
- Quiz can only be generated when the room status is `WAITING`.
- Supported difficulties: `EASY`, `ADVANCED`.
- Room status transitions: `WAITING` → `GENERATING` → `READY` → `PLAYING` → `FINISHED`.

---

## TODO (Planned)

### User & Profile
- Add endpoint to fetch user stats (games played, win rate, average score, streaks).
- Add endpoint for match history / recent games.

---

**Last Updated:** 2026-02-11
