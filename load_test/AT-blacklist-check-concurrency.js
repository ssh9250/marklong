import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// ========== 설정값 ==========
const BASE_URL = 'http://localhost:8080/api';
const TOKEN    = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdHJpbmciLCJpYXQiOjE3Nzk3NTY5ODAsImV4cCI6MTc3OTc1ODc4MH0.fy-q20TsOH1v5f20YFktemBnOtp_qkYvGVmXSVI3jIw';
const USER_ID  = 1;
// ============================

// 커스텀 카운터
const successCount          = new Counter('me_success');
const failCount             = new Counter('me_fail');

export const options = {
    vus: 50,
    duration: '10s',
};

const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${TOKEN}`,
};

// payload 필요한가?
export default function () {
    const res = http.get(`${BASE_URL}/users/me`, { headers });

    const is200 = res.status === 200;
    const is400 = res.status === 400;
    const is401 = res.status === 401;
    const is404 = res.status === 404;

    check(res, {
        '200 조회 성공':           (r) => r.status === 200,
        '401 토큰 오류':           (r) => r.status === 401,
        '404 회원 없음':           (r) => r.status === 404,
        '500 서버 오류':           (r) => r.status === 500,
    });

    if (is200) successCount.add(1);
    else failCount.add(1);

    // 상태별 로그 (너무 많으면 주석 처리)
    if (!is200) {
        console.log(`[${res.status}] ${res.body}`);
    }
}