# MatQuiz

## Description

Online quiz game inspired by Kahoot, where questions are generated automatically by an LLM.

## Features

  * User-friendly interface
  * Account management (CRUD)
  * Real-time feedback
  * Leaderboard tracking
  * Customizable quiz settings with topic and difficulty level selection
  * Multiplayer mode with real-time synchronization
  * Simple anticheat with time validation on server-side


## Architecture and Design

MatQuiz is built as an **Event-Driven Microservices** platform deployed on a single Virtual Private Server (VPS) via Docker. This architecture separates the real-time gameplay logic from the time-consuming AI content generation, ensuring a responsive user experience.

### Key Architectural Decisions

  * **Asynchronous AI Generation:** The application uses **RabbitMQ** as a message broker to handle the asynchronous workflow for quiz creation. This prevents the main gameplay server from blocking while waiting for the external **Gemini API** response.
  * **Microservice Separation:** Dedicated, independent microservices (`ai-infer` and `ai-process`) handle the external API call and content validation.
  * **Real-Time Communication:** **WebSockets** (using the STOMP protocol) are used for instant state updates, delivering questions, scores, and countdown timers to all connected players simultaneously.

### The Content Generation Flow

1.  **Backend** sends a job to the **`ai.jobs`** queue with configuration (Topic, Difficulty).
2.  **`ai-infer`** (AI Inference Service) calls **Google Gemini 2.5 Flash API**.
3.  **`ai-infer`** publishes the raw, text-based JSON output to **`ai.raw`**.
4.  **`ai-process`** (Validation Service) cleans the JSON, enforces the required schema (e.g., 4 answers, valid index), and publishes the verified quiz to **`ai.validated`**.
5.  **Backend** consumes the validated quiz and broadcasts the "Quiz Ready" state via **WebSocket**.


## Technology Stack

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Backend Core** | **Spring Boot (Java)** | Manages game state, user sessions, security, and WebSocket. |
| **Frontend** | **React** | Single Page Application (SPA) for dynamic player and host interfaces. |
| **Database** | **PostgreSQL** | Persistent storage for users, game metadata, and results. Uses **JSONB** for efficient answer storage. |
| **Messaging** | **RabbitMQ** | Decouples the core application from the external AI services. |
| **AI Content** | **Google Gemini 2.5 Flash** | Provides fast, on-demand content generation via its REST API. |
| **Deployment** | **Docker / Nginx** | Ensures easy portability and reliable hosting on the VPS. |


## Stability and Reliability

The project incorporates patterns to handle the instability of external services:

  * **Fallback Mechanism:** If the Gemini API fails or times out, the `ai-infer` service retrieves a pre-generated quiz from a dedicated backup database table, ensuring games can still start.
  * **JSON Schema Validation:** The `ai-process` microservice guarantees data consistency. If Gemini generates malformed JSON, `ai-process` initiates an automatic **retry loop** to request a new quiz before failing the job.
  * **Server-Side Time Validation:** Anti-cheat logic is enforced by tracking the question start time and answer submission time strictly on the server, eliminating client-side cheating.


##  Project Structure

```text
/matquiz
├── /backend            # Spring Boot Core, WebSocket, API Gateway
├── /ai-services        # ai-infer & ai-process services (Java)
├── /frontend           # React SPA
└── docker-compose.yml  # Infrastructure definition (Postgres, RabbitMQ)
```

## Excalidraw Diagrams Backend

[Excalidraw File Link](https://excalidraw.com/#json=e323lmQXd7zlJJtqi9DpT,8Y8shsL2KvpSI3_dpJfklw)

## Figma Frontend Design


## DB Diagrams (Main Database Schema)

[DB Diagram Link Main Database](https://dbdiagram.io/d/Mat_Quiz_Diagram-691e128c228c5bbc1a9b6451)

```java
Project MatQuiz {
  database_type: 'PostgreSQL'
  Note: 'Backend database for MatQuiz (Spring Boot + RabbitMQ architecture)'
}

// 1. Users: Standard auth and profile stats
Table users {
  id bigserial [pk]

  username varchar(50) [unique, not null]
  email varchar(100) [unique, not null]
  password_hash varchar(255) [not null]
  avatar_url varchar(255) [note: 'Will provide to the users 10 default avatars that will be randomly selected']
  
  // Game Stats
  elo_rating int [default: 1000, note: 'Starts at 1000']
  total_games_played int [default: 0]
  last_game_points int [default: 0]
  
  created_at timestamp [default: `now()`]
  updated_at timestamp [default: `now()`]
}

// 2. Game Rooms: The session state
Table game_rooms {
  id bigserial [pk]
  room_code varchar(5) [unique, not null, note: 'The 5-digit PIN (ex. 56278)']
  host_id bigint [not null, ref: > users.id]
  
  // Configuration
  topic varchar(100) [not null]
  difficulty varchar(20) [not null, note: 'EASY, ADVANCED']
  question_count int [default: 5]
  
  // AI & Async Logic
  correlation_id uuid [unique, note: 'Links this room to the RabbitMQ job']
  status varchar(20) [default: 'WAITING', note: 'WAITING -> GENERATING -> READY -> PLAYING -> FINISHED']
  
  // Game Progress
  current_question_index int [default: 0]
  
  created_at timestamp [default: `now()`]
}

// 3. Questions: Generated by AI
Table questions {
  id bigserial [pk]
  
  game_room_id bigint [ref: > game_rooms.id]
  
  question_text text [not null]
  
  // Storing answers as JSONB is more efficient than a separate table for read-heavy quizzes
  // Format: ["Answer A", "Answer B", "Answer C", "Answer D"]
  answers jsonb [not null] 
  
  correct_index int [not null, note: '0-3']

  time_limit int [default: 30]
  
  order_index int [not null, note: '1, 2, 3... To ensure order']

  posted_at timestamp [note: 'Server time when question was revealed. For basic anticheat?']
}

// 4. Game Players: Who is in a specific room
Table game_players {
  id bigserial [pk]
  game_room_id bigint [ref: > game_rooms.id]
  user_id bigint [ref: > users.id]
  
  nickname varchar(50) [not null, note: 'What will be displayed on the front-end']
  socket_session_id varchar(100) [note: 'To map WebSocket messages to players']
  
  score int [default: 0]
  
  is_connected boolean [default: true, note: 'In case websocket connection drops']

  joined_at timestamp [default: `now()`, note: 'For logging']

  indexes {
    (game_room_id, user_id) [unique] // A user can only join a room once
  }
}

// 5. Player Answers:
Table player_answers {
  id bigserial [pk]
  game_player_id bigint [ref: > game_players.id]
  question_id bigint [ref: > questions.id]
  
  selected_index int
  is_correct boolean

  //Will need a network buffer TBD
  time_taken_ms int [note:'Calculated on the backend']
  points_awarded int [default: 0, note:'Points calculated based on speed on the backend']
  
  answered_at timestamp [default: `now()`]
}
```

## DB Diagrams (Backup Database Schema)

[DB Diagram Link Backup Database](https://dbdiagram.io/d/MatQuiz-AI-Backup-Database-6924341e228c5bbc1a37a76a)

```java
Project MatQuiz_Backup {
  database_type: 'PostgreSQL'
  Note: 'Backend database that stores as a cache the previously generated'
  Note: 'quizes and has a seed of 50 quizes if gemini servers are not working correctly'
}

Table backup_quizzes {
  id bigserial [pk]
  
  // Metadata for matching
  topic varchar(100) [not null, note: 'Lowercase, ex "eurovision"']
  difficulty varchar(20) [not null]
  
  // The Payload
  // I store the raw JSON string that Gemini WOULD have returned.
  // This allows ai-infer to just inject this into the pipeline 
  // without changing the logic.
  raw_json_response text [not null]
  
  last_used_at timestamp [default: null]
  created_at timestamp [default: `now()`]
  
  indexes {
    (topic, difficulty) // Fast lookup when gemini servers are down and backup mode is on
  }
}
```
