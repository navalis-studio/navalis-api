import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// Custom metrics
const rankingDuration = new Trend('ranking_duration');
const availableDuration = new Trend('available_games_duration');
const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:5000';

export const options = {
  scenarios: {
    // Stress test: many players polling simultaneously
    polling_stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 10 },
        { duration: '15s', target: 50 },
        { duration: '20s', target: 100 },
        { duration: '10s', target: 100 },
        { duration: '5s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<300', 'p(99)<1000'],
    ranking_duration: ['p(95)<200'],
    available_games_duration: ['p(95)<100'],
    errors: ['rate<0.01'],
  },
};

function register(vuId) {
  const username = `stress_${vuId}_${Date.now()}`;
  const res = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
    username: username,
    password: 'StressTest123!',
  }), { headers: { 'Content-Type': 'application/json' } });

  if (res.status === 200 || res.status === 201) {
    const body = JSON.parse(res.body);
    return body.token;
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

export default function () {
  const token = register(__VU);
  if (!token) {
    errorRate.add(1);
    sleep(1);
    return;
  }

  // Simulate lobby: poll available + ranking repeatedly
  for (let i = 0; i < 5; i++) {
    group('poll_available', () => {
      const start = Date.now();
      const res = http.get(`${BASE_URL}/api/games/available`, authHeaders(token));
      availableDuration.add(Date.now() - start);
      const ok = check(res, { 'available 200': (r) => r.status === 200 });
      if (!ok) errorRate.add(1);
    });

    group('get_ranking', () => {
      const start = Date.now();
      const res = http.get(`${BASE_URL}/api/players/ranking`, authHeaders(token));
      rankingDuration.add(Date.now() - start);
      const ok = check(res, { 'ranking 200': (r) => r.status === 200 });
      if (!ok) errorRate.add(1);
    });

    sleep(1); // 1s between polls (faster than real 5s to stress more)
  }

  // Logout
  http.post(`${BASE_URL}/api/auth/logout`, null, authHeaders(token));
}
