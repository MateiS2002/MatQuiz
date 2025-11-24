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


## Excalidraw Diagrams Frontend


## DB Diagrams (Main Database Schema)


## DB Diagrams (Backup Database Schema)
