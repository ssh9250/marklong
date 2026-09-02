import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://localhost:8080/api';

const TOKEN =
    'eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwicm9sZSI6IlVTRVIiLCJmYW1pbHlJZCI6Ijc5OWUyNzM0LTYzOWItNGU2Yi05MjA5LWMzMGUzYTI5ODY3YiIsImlhdCI6MTc4ODM0MjEyNCwiZXhwIjoxNzg4MzQyNDI0fQ.VRDpRH5SjqnsKKEohNqhAhVTR_V-IYyf0-z72DGlyiHipfbhOcGZiB2u-bzhjgQHEnnc1cEoXoPSoVu9G7fwhA';

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