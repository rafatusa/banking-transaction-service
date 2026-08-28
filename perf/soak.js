// k6 full load benchmark: 200 concurrent users, 15 minutes.
//
// Runs as its own scheduled workflow (.github/workflows/soak.yml), NOT in the
// deploy pipeline — a 15-minute load test in front of every release would make
// routine deploys unbearable.
//
// Thresholds are declared and REPORTED but do not abort the run
// (abortOnFail is not set): the target instance is a t3.small sized for a demo,
// and 200 VUs against it will legitimately exceed p95 < 400 ms. The value here
// is the measurement and the trend over time, not a nightly red build. Raise
// the instance size before turning these into hard gates.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL;
const SMOKE_USER = __ENV.SMOKE_USER;
const SMOKE_PASSWORD = __ENV.SMOKE_PASSWORD;

const errorRate = new Rate('business_errors');
const loginDuration = new Trend('login_duration', true);
const readDuration = new Trend('read_duration', true);
const requestCount = new Counter('business_requests');

export const options = {
    scenarios: {
        soak: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '2m', target: 200 },  // ramp up
                { duration: '15m', target: 200 }, // 15 minutes at 200 concurrent users
                { duration: '1m', target: 0 },    // ramp down
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        'http_req_duration{expected_response:true}': ['p(95)<400'],
        http_req_failed: ['rate<0.01'],
        business_errors: ['rate<0.01'],
    },
    // Report throughput and percentiles in the summary.
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
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

    const health = http.get(`${BASE_URL}/actuator/health`, { tags: { name: 'health' } });
    readDuration.add(health.timings.duration);
    errorRate.add(!check(health, { 'health is 200': (r) => r.status === 200 }));
    requestCount.add(1);

    const accounts = http.get(`${BASE_URL}/api/accounts`, {
        ...authHeaders,
        tags: { name: 'list-accounts' },
    });
    readDuration.add(accounts.timings.duration);
    errorRate.add(!check(accounts, { 'accounts is 200': (r) => r.status === 200 }));
    requestCount.add(1);

    const history = http.get(`${BASE_URL}/api/transactions?page=0&size=20`, {
        ...authHeaders,
        tags: { name: 'transaction-history' },
    });
    readDuration.add(history.timings.duration);
    errorRate.add(!check(history, { 'history is 200': (r) => r.status === 200 }));
    requestCount.add(1);

    // One login per ten iterations: authentication is intentionally expensive
    // (BCrypt cost 12), and hammering it would measure BCrypt rather than the
    // service under realistic mixed load.
    if (__ITER % 10 === 0) {
        const login = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({ username: SMOKE_USER, password: SMOKE_PASSWORD }),
            { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
        );
        loginDuration.add(login.timings.duration);
        errorRate.add(!check(login, { 'login is 200': (r) => r.status === 200 }));
        requestCount.add(1);
    }

    sleep(1);
}
