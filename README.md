# Navalis API

> Backend for a real-time online Battleship game built with Java 21 and Spring Boot 4, featuring JWT authentication, WebSocket/STOMP gameplay, turn-based state machine, and PostgreSQL persistence.

![Status](https://img.shields.io/badge/Status-completed-green)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-green)
![Database](https://img.shields.io/badge/Database-PostgreSQL%2016-blue)

## Table of Contents

- [Technologies](#technologies)
- [Architecture](#architecture)
- [Game Rules](#game-rules)
- [API Endpoints](#api-endpoints)
- [WebSocket Events](#websocket-events)
- [How to Run](#how-to-run)
- [Running Tests](#running-tests)

## Technologies

| Technology | Role in the Project |
|---|---|
| Java 21 | Main backend language |
| Spring Boot 4.0.7 | Web framework |
| Spring Security + JWT | Authentication and authorization |
| Spring Data JPA + Hibernate 7 | Database mapping |
| PostgreSQL 16 | Relational database |
| Flyway | Database migrations |
| WebSocket/STOMP | Real-time gameplay communication |
| Lombok | Boilerplate reduction |
| SpringDoc OpenAPI 3 | API documentation (Swagger) |
| JUnit 5 + Mockito | Testing framework |
| Docker | Containerization |

## Architecture

Domain-Driven Design (DDD) with clear layer separation:

```
io.navalis.api/
├── domain/
│   ├── model/          → Game, Board, Ship, Player, Coordinate, enums (pure POJOs)
│   ├── exception/      → DomainException, GameAlreadyFull, NotYourTurn, etc.
│   └── port/           → GameRepository (interface/contract)
├── application/
│   ├── service/        → AuthService, GameService
│   └── dto/
│       ├── request/    → RegisterRequest, LoginRequest, PlaceShipRequest, FireRequest
│       └── response/   → AuthResponse, GameResponse, ShotResponse
├── infrastructure/
│   ├── config/         → OpenApiConfig
│   ├── persistence/
│   │   ├── entity/     → UserEntity, GameEntity (JPA)
│   │   ├── repository/ → UserRepository, SpringDataGameRepository, JpaGameRepository
│   │   └── mapper/     → GameMapper
│   ├── security/       → JwtTokenProvider, JwtAuthenticationFilter, SecurityConfig
│   └── websocket/      → WebSocketConfig, WebSocketAuthInterceptor, StompPrincipal
└── interfaces/
    ├── rest/           → AuthController, GameController, HealthController, RankingController
    └── ws/             → GameWebSocketController
```

## Game Rules

- **Grid:** 10×10
- **Fleet:** Carrier (5), Battleship (4), Cruiser (3), Submarine (3), Destroyer (2)
- **Turns:** Hit → play again. Miss → opponent's turn.
- **State machine:** `WAITING_FOR_OPPONENT` → `PLACING_SHIPS` → `IN_PROGRESS` → `FINISHED`
- **Turn timer:** 20s per turn, auto-random shot on expiry
- **Reconnection:** 30s grace period before forfeit
- **W.O.:** Automatic victory if opponent disconnects or forfeits

## API Endpoints

Base URL: `http://localhost:5000/api`

### Authentication

| Method | Route | Description | Auth |
|---|---|---|---|
| POST | /auth/register | Register a new user | No |
| POST | /auth/login | Login | No |

### Games

| Method | Route | Description | Auth |
|---|---|---|---|
| POST | /games | Create a game | Yes |
| POST | /games/{id}/join | Join a game by ID | Yes |
| POST | /games/join/{roomCode} | Join a game by room code | Yes |
| GET | /games/available | List available games | Yes |
| GET | /games/{id} | Get game info | Yes |
| GET | /games/active | Get player's active game (reconnection) | Yes |
| DELETE | /games/{id} | Cancel game (before IN_PROGRESS) | Yes |
| POST | /games/{id}/forfeit | Forfeit (W.O.) | Yes |

### Other

| Method | Route | Description | Auth |
|---|---|---|---|
| GET | /players/ranking | Top 20 players by wins | Yes |
| GET | /health | Health check | No |

### Response Codes

| Code | Situation |
|---|---|
| 200 | Success |
| 201 | Resource created |
| 204 | No content |
| 400 | Validation error or invalid game action |
| 401 | Unauthorized (invalid/expired token) |
| 409 | Conflict (game full, not your turn) |

## WebSocket Events

- **Endpoint:** `/ws`
- **Auth:** JWT token in `Authorization` header during CONNECT
- **Topic:** `/topic/game/{gameId}`

### Client → Server

| Destination | Body | Description |
|---|---|---|
| `/app/game/{gameId}/place-ship` | `{ shipType, row, col, orientation }` | Place a ship |
| `/app/game/{gameId}/ready` | — | Confirm fleet placement |
| `/app/game/{gameId}/unready` | — | Cancel fleet confirmation |
| `/app/game/{gameId}/fire` | `{ row, col }` | Fire at a cell |

### Server → Client

| Event | Description |
|---|---|
| `OPPONENT_JOINED` | Player 2 joined the game |
| `SHIP_PLACED` | A ship was placed |
| `PLAYER_READY` / `PLAYER_UNREADY` | Player confirmed/cancelled fleet |
| `GAME_STARTED` | Both ready, game begins |
| `SHOT_FIRED` | Shot result (hit/miss/sunk/game over) |
| `TURN_TIMER_START` | Turn began, 20s countdown |
| `OPPONENT_DISCONNECTED_TEMP` | Opponent disconnected, 30s grace |
| `OPPONENT_RECONNECTED` | Opponent reconnected within grace period |
| `GAME_CANCELLED` | Game cancelled (pre-game abandonment) |

## How to Run

### Prerequisites

- Java 21+
- PostgreSQL 16 running
- Maven (or use the included wrapper `./mvnw`)

### 1) Clone and access the project

```bash
git clone https://github.com/navalis-studio/navalis-api.git
cd navalis-api
```

### 2) Set up environment variables

```bash
cp .env.example .env
```

Edit `.env` with your database credentials:

```env
CHAVE_POSTGRES_DB=navalis_db
CHAVE_POSTGRES_USER=postgres
CHAVE_POSTGRES_PASSWORD=postgres
CHAVE_POSTGRES_PORT=5432
JWT_SECRET=your-secret-key-at-least-32-characters-long
```

### 3) Create the database

```bash
createdb navalis_db
```

### 4) Run the application

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:5000`.  
Swagger UI: `http://localhost:5000/swagger-ui.html`

### Running with Docker

```bash
docker compose up -d
```

This starts both the API and PostgreSQL.

## Running Tests

```bash
./mvnw test
```

77 unit tests covering domain model and services (JUnit 5 + Mockito).
