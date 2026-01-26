
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
    - When all questions are answered, sets status to `FINISHED` and broadcasts final `GameRoomDto`.
    - Question's `postedAt` timestamp is recorded for anti-cheat validation.

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
- **Private Response:** `/user/queue/answerResult` → `AnswerResultDto`
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto` (updated scores)
- **Behavior:**
    - Room status must be `PLAYING`.
    - Player can only answer each question once.
    - Answer index must be valid (0-3).
    - Points awarded: 100 for correct, 0 for incorrect.
    - Player score is updated immediately.
    - Time taken is calculated from `question.postedAt` to `submissionTime`.

---

### 9. Request Correct Answer
**Host requests to reveal the correct answer for the current question.**

- **Destination:** `/app/correctAnswer`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12",
    "questionId": 123
  }
  ```
- **Public Broadcast:** `/topic/room/{roomCode}` → `CorrectAnswerDto`
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto` (updated scores)
- **Behavior:**
    - Only the host can request the correct answer.
    - Identifies players who haven't answered the question.
    - Automatically assigns 0 points to players who didn't answer.
    - Sends a "FAILED ANSWER" message to each player who missed the question via `/user/queue/failed_answer`.
    - Broadcasts the correct answer to all players.
    - Broadcasts updated room state with final scores for the question.

---

### 10. Request End Game Results
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

### 11. End Game Early
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

### 12. Automatic Events (Server-Initiated)

#### Disconnect Event
**When a player's WebSocket connection closes.**

- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Player's `isConnected` status is set to `false`.
    - Room state is broadcast to all remaining players.
    - Player data is retained (they can reconnect).

#### Failed Answer Notification
**When a player misses answering a question (host revealed correct answer).**

- **Private Response:** `/user/queue/failed_answer` → `String` ("FAILED ANSWER")
- **Behavior:**
    - Sent to each player who didn't answer a question when the host reveals the correct answer.
    - Indicates that 0 points were automatically assigned for that question.

#### Error Messages
**When an action fails.**

- **Private Response:** `/user/queue/errors` → `String` (error message)
- **Examples:**
    - "Room not found"
    - "Only the host can generate the quiz!"
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
    "email": "matei@example.com",
    "avatarUrl": "https://..."
  }
  ```
- **Response:** `Boolean` (true if successful)

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
    "role": "USER",
    "eloRating": 1000,
    "avatarUrl": "https://..."
  }
  ```

---

### 4. Get Leaderboard (All Players)
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

### 5. Get Leaderboard (Specific User)
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

### 6. Who Am I
- **Endpoint:** `GET /api/auth/whoami`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Response:** `String`
  ```
  You are: matei with authorities [ROLE_USER]
  ```

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
**Sent to a player after they submit an answer.**

```json
{
  "questionId": 42,
  "isCorrect": true,
  "correctAnswerIndex": 1,
  "pointsEarned": 100,
  "newTotalScore": 250
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
  "role": "admin",
  "eloRating": 1000,
  "avatarUrl": "https://..."
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

### `CorrectAnswerRequest`
```json
{
  "roomCode": "ABC12",
  "questionId": 123
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

---

## Game Flow Example

### Standard Game Flow
1. **Matei** creates a room → Receives `GameRoomDto` (status: `WAITING`)
2. **John** joins via `/app/join` → Both receive updated `GameRoomDto`
3. **Matei** generates quiz → Status changes to `GENERATING`, then `READY`
4. **Matei** starts game → Status changes to `PLAYING`
5. **Matei** requests question → All players receive `QuestionDto`
6. **John** submits answer → Receives `AnswerResultDto`, all players see updated scores
7. **Matei** requests correct answer → All players receive `CorrectAnswerDto`, players who missed get "FAILED ANSWER" notification
8. Repeat step 5-7 for all questions
9. After last question → Status changes to `FINISHED`, final `GameRoomDto` with leaderboard
10. **Matei** requests end results → All players receive `ResultsDto` with final leaderboard

### Early Termination Flow
1. **Matei** creates a room → Receives `GameRoomDto` (status: `WAITING`)
2. **John** joins via `/app/join` → Both receive updated `GameRoomDto`
3. **Matei** generates quiz → Status changes to `GENERATING`, then `READY`
4. **Matei** starts game → Status changes to `PLAYING`
5. **Matei** requests question → All players receive `QuestionDto`
6. **John** submits answer → Receives `AnswerResultDto`, all players see updated scores
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
- Subscribe to `/user/queue/answerResult` to receive answer results after submitting.
- Subscribe to `/user/queue/failed_answer` to receive notifications when missing a question.
- Subscribe to `/user/queue/errors` to receive error messages.

### Game Flow
- The `QuestionDto` does **not** include the correct answer—it's only revealed in `AnswerResultDto` after submission or in `CorrectAnswerDto` when the host requests it.
- Player `isConnected` status updates in real-time when users disconnect.
- Use `submissionTime` from the client to allow server-side anti-cheat validation (time-based scoring in the future).
- After the host requests the correct answer, players who missed the question will receive a "FAILED ANSWER" notification and 0 points are automatically assigned.
- The game automatically finishes after all questions have been answered (32-second timeout after the last question is posted).
- The host can end the game early at any time using `/app/endGame`, which will delete the room.

### Question Timing
- Questions have a 30-second time limit.
- Time taken is calculated from `question.postedAt` to `submissionTime`.
- If a player doesn't answer within the time limit, they receive 0 points when the host reveals the correct answer.

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
- Use `GET /api/auth/whoami` to verify authentication and get user details.
- Store the JWT token securely and include it in the WebSocket connection headers.

### Room Management
- Maximum 5 players per room.
- Players cannot join rooms with status `PLAYING` or `FINISHED`.
- When the host leaves, the host is transferred to the next player.
- When the last player leaves, the room is deleted.
- Players can reconnect to active games using `/app/reconnect`.

### Quiz Generation
- Only the host can generate the quiz.
- Quiz can only be generated when the room status is `WAITING`.
- Supported difficulties: `EASY`, `ADVANCED`.
- Room status transitions: `WAITING` → `GENERATING` → `READY` → `PLAYING` → `FINISHED`.

---

**Last Updated:** 2026-01-20
```