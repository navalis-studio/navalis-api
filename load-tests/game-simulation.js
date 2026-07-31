import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep, group } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';

// Custom metrics
const gamesCreated = new Counter('games_created');
const gamesJoined = new Counter('games_joined');
const shotsFired = new Counter('shots_fired');
const loginDuration = new Trend('login_duration');
const fireDuration = new Trend('fire_duration');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:5000';

export const options = {
  scenarios: {
    // Simula players entrando e jogando
    game_simulation: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 20 },  // Ramp up: 20 players em 10s
        { duration: '30s', target: 50 },  // Sustain: 50 players por 30s
        { duration: '20s', target: 100 }, // Peak: 100 players por 20s
        { duration: '10s', target: 0 },   // Ramp down
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],     // 95% das requests < 500ms
    http_req_failed: ['rate<0.05'],       // Menos de 5% de falha
    login_duration: ['p(95)<1000'],       // Login < 1s
  },
};

// Register a unique user per VU
function registerUser(vuId) {
  const username = `loadtest_user_${vuId}_${Date.now()}`;
  const password = 'LoadTest123!';

  const res = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
    username: username,
    password: password,
  }), { headers: { 'Content-Type': 'application/json' } });

  if (res.status === 200) {
    const body = JSON.parse(res.body);
    return { token: body.token, userId: body.id, username: username };
  }

  // If register fails (user exists), try login
  return loginUser(username, password);
}

function loginUser(username, password) {
  const start = Date.now();
  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    username: username,
    password: password,
  }), { headers: { 'Content-Type': 'application/json' } });

  loginDuration.add(Date.now() - start);

  if (res.status === 200) {
    const body = JSON.parse(res.body);
    return { token: body.token, userId: body.id, username: username };
  }
  return null;
}

function authHeaders(token) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };
}

// Simulate lobby polling
function pollLobby(token) {
  const res = http.get(`${BASE_URL}/api/games/available`, authHeaders(token));
  check(res, { 'lobby poll 200': (r) => r.status === 200 });
  return res.status === 200 ? JSON.parse(res.body) : [];
}

// Simulate checking active game
function checkActiveGame(token) {
  const res = http.get(`${BASE_URL}/api/games/active`, authHeaders(token));
  return res.status === 200 ? JSON.parse(res.body) : null;
}

// Create a game
function createGame(token) {
  const res = http.post(`${BASE_URL}/api/games`, null, authHeaders(token));
  if (res.status === 200 || res.status === 201) {
    gamesCreated.add(1);
    return JSON.parse(res.body);
  }
  return null;
}

// Join a game
function joinGame(token, gameId) {
  const res = http.post(`${BASE_URL}/api/games/${gameId}/join`, null, authHeaders(token));
  if (res.status === 200) {
    gamesJoined.add(1);
    return JSON.parse(res.body);
  }
  return null;
}

// Get ranking
function getRanking(token) {
  const res = http.get(`${BASE_URL}/api/players/ranking`, authHeaders(token));
  check(res, { 'ranking 200': (r) => r.status === 200 });
}

export default function () {
  const vuId = __VU;

  // Register/Login
  const user = registerUser(vuId);
  if (!user) {
    sleep(1);
    return;
  }

  group('lobby_activity', () => {
    // Poll lobby multiple times (simulating player browsing)
    for (let i = 0; i < 3; i++) {
      pollLobby(user.token);
      sleep(1);
    }

    // Check ranking
    getRanking(user.token);

    // 50% chance to create a game, 50% chance to try joining
    if (Math.random() > 0.5) {
      const game = createGame(user.token);
      if (game) {
        // Wait for opponent (poll a few times)
        for (let i = 0; i < 3; i++) {
          sleep(2);
          pollLobby(user.token);
        }
      }
    } else {
      // Try to join an available game
      const games = pollLobby(user.token);
      if (games && games.length > 0) {
        const randomGame = games[Math.floor(Math.random() * games.length)];
        joinGame(user.token, randomGame.id);
      }
    }
  });

  // Simulate some idle time in lobby
  sleep(Math.random() * 3 + 1);

  // Logout
  http.post(`${BASE_URL}/api/auth/logout`, null, authHeaders(user.token));
}
