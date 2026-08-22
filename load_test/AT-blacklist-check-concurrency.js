import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://localhost:8080/api';

const TOKEN =
    '여기에_엑세스토큰_입력';

export const options = {
    vus: 50,
    duration: '30s',
};

const headers = {
    Authorization: `Bearer ${TOKEN}`,
};

export default function () {
    const res = http.get(`${BASE_URL}/users/me`, {
        headers,
    });

    check(res, {
        '200 조회 성공': (r) => r.status === 200,
    });
}