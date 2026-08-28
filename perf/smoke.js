// k6 deploy smoke performance check.
//
// Runs inside the deploy pipeline, so it is deliberately SHORT: it proves the
// deployment is not pathologically slow before the release is cut. The full
// 200-VU / 15-minute benchmark lives in perf/soak.js and runs as its own
// scheduled workflow, so routine deploys are not taxed by it.
//
// Thresholds here are ENFORCED: a breach fails the stage.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL;
const SMOKE_USER = __ENV.SMOKE_USER;
const SMOKE_PASSWORD = __ENV.SMOKE_PASSWORD;

const errorRate = new Rate('business_errors');
const loginDuration = new Trend('login_duration', true);

export const options = {
    scenarios: {
        smoke: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },
                { duration: '60s', target: 10 },
                { duration: '15s', target: 0 },
            ],
            gracefulRampDown: '15s',
        },
    },
    thresholds: {
        // Same targets as the full benchmark, applied at deploy-check scale.
        'http_req_duration{expected_response:true}': ['p(95)<400'],
        http_req_failed: ['rate<0.01'],
        business_errors: ['rate<0.01'],
    },
};

export function setup() {
    if (!BASE_URL) {
        throw new Error('BASE_URL must be set');
    }
    const res = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ username: SMOKE_USER, password: SMOKE_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } },
    );

    if (res.status !== 200) {
        throw new Error(`setup login failed with status ${res.status}`);
    }
    return { token: res.json('token') };
}

export default function (data) {
    const authHeaders = {
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${data.token}`,
        },
    };

    // Read paths: the endpoints real clients hit most.
    const health = http.get(`${BASE_URL}/actuator/health`, { tags: { name: 'health' } });
    errorRate.add(!check(health, { 'health is 200': (r) => r.status === 200 }));

    const accounts = http.get(`${BASE_URL}/api/accounts`, {
        ...authHeaders,
        tags: { name: 'list-accounts' },
    });
    errorRate.add(!check(accounts, { 'accounts is 200': (r) => r.status === 200 }));

    const history = http.get(`${BASE_URL}/api/transactions?page=0&size=20`, {
        ...authHeaders,
        tags: { name: 'transaction-history' },
    });
    errorRate.add(!check(history, { 'history is 200': (r) => r.status === 200 }));

    // Authentication is the heaviest endpoint (BCrypt by design), so it is
    // measured separately rather than skewing the read-path percentiles.
    const login = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ username: SMOKE_USER, password: SMOKE_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
    );
    loginDuration.add(login.timings.duration);
    errorRate.add(!check(login, { 'login is 200': (r) => r.status === 200 }));

    sleep(1);
}
