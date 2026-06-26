package com.example.marklong.domain.auth.repository;

import com.example.marklong.domain.auth.dto.RotateResult;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {
    private final StringRedisTemplate stringRedisTemplate;

    /*
     * redis 설계
     * rt:{token}, value = {userId, status, issuedAt, expiresAt}
     * user:{userId}:rtRevokedAfter , value = ttl or issuedAt or version
     *
     * */

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private static final String RT_KEY_PREFIX = "rt:";
    private static final String REVOKED_AFTER_KEY = "user:%d:rtRevokedAfter";

    // 반환값
    //   1  → 성공
    //   0  → oldToken 없음 (이미 만료되었거나 존재하지 않음)
    //  -1  → status가 ACTIVE가 아님 (재사용 감지 → 탈취 의심)

    private static final String ROTATE_SCRIPT = """
            local old = redis.call('HGETALL', KEYS[1])
            if #old == 0 then
                return {0, 0}
            end
            
            local status = nil
            local issuedAt = nil
            local userId = nil
            
            for i = 1, #old, 2 do
                if old[i] == 'status' then
                    status = old[i+1]
                elseif old[i] == 'issuedAt' then
                    issuedAt = tonumber(old[i+1])
                elseif old[i] == 'userId' then
                    userId = tonumber(old[i+1])
                end
            end
            
            local revokedAfterKey = 'user:' .. userId .. ':rtRevokedAfter'
            local revokedAfter = redis.call('GET', revokedAfterKey)
            
            if revokedAfter ~= false then
                revokedAfter = tonumber(revokedAfter)
            
                if issuedAt ~= nil and issuedAt < revokedAfter then
                    return {-2, userId}
                end
            end
            
            if status ~= 'ACTIVE' then
                return {-1, userId}
            end
            
            
            redis.call('HSET', KEYS[1], 'status', 'ROTATED')
            
            redis.call('HSET', KEYS[2],
                'userId',    userId,
                'status',    'ACTIVE',
                'issuedAt',  ARGV[1],
                'expiresAt', ARGV[2]
            )
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            
            return {1, userId}
            """;

    private static final String SAVE_SCRIPT = """
            redis.call('HSET', KEYS[1],
            'userId',    ARGV[1],
            'status',    'ACTIVE',
            'issuedAt',    ARGV[2],
            'expiresAt',    ARGV[3])
            
            return redis.call('EXPIRE', KEYS[1], ARGV[4])
            """;

    private final RedisScript<List> rotateScript = RedisScript.of(ROTATE_SCRIPT, List.class);
    private final RedisScript<Long> saveScript = RedisScript.of(SAVE_SCRIPT, Long.class);

    public String save(Long userId) {
        String token = UUID.randomUUID().toString();
        String key = rtKey(token);
        long now = epochNow();
        long expiresAt = now + refreshExpirationMs / 1000;
        long ttl = refreshExpirationMs / 1000;

//        stringRedisTemplate.opsForHash().putAll(key, Map.of(
//                "userId", userId.toString(),
//                "status", "ACTIVE",
//                "issuedAt", String.valueOf(now),
//                "expiresAt", String.valueOf(expiresAt)
//        ));
//        stringRedisTemplate.expire(key, ttl, TimeUnit.SECONDS);

        Long result = stringRedisTemplate.execute(
                saveScript,
                List.of(key),
                userId.toString(),
                String.valueOf(now),
                String.valueOf(expiresAt),
                String.valueOf(ttl)
        );

        if (result == null || result != 1) return null;

        return token;
    }

    public RotateResult rotate(String oldToken) {
        // token -> hashed and compare -> hit/miss
        // hit -> ACTIVE/ROTATE
        // ACTIVE -> redis search and change status, add new token
        // ROTATE -> reuse detection logic
        // miss -> throw new invalid token exception

        String oldKey = rtKey(oldToken);
        String newToken = UUID.randomUUID().toString();
        String newKey = rtKey(newToken);
        long now = epochNow();
        long expiresAt = now + refreshExpirationMs / 1000;
        long ttl = refreshExpirationMs / 1000;

//        Long userId = extractUserId(oldKey);
//        Long argvUserId = userId != null ? userId : -1L;

        List<?> result = stringRedisTemplate.execute(
                rotateScript,
                List.of(oldKey, newKey),
//                String.valueOf(argvUserId),
                String.valueOf(now),
                String.valueOf(expiresAt),
                String.valueOf(ttl)
        );

        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Unexpected Lua result");
        }

        Object codeObj = result.get(0);
        Object userIdObj = result.get(1);

        if (!(codeObj instanceof Number) || !(userIdObj instanceof Number)) {
            throw new IllegalStateException("Unexpected Lua result");
        }

        Long code = ((Number) result.get(0)).longValue();
        Long userId = ((Number) result.get(1)).longValue();

        if (code == 0L) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 공격자 reissue 후 정상 사용자 reissue
        // rotate, new, reissue -> rotate 접근 -> is ROTATED => kick

        // 정상 사용자 reissue 후 탈취
        // rotate 접근

        // 이미 무효화된 토큰 사용
        if (code == -2L) {
            throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
        }

        // rotate 접근
        if (code == -1L) {
            revokeAll(userId);
            throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
        }

        return new RotateResult(newToken, userId);
    }

    public void revokeAll(Long userId) {
        String key = revokedAfterKey(userId);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(epochNow()));
    }

    public Optional<Long> getRevokedAfter(Long userId) {
        String val = stringRedisTemplate.opsForValue().get(revokedAfterKey(userId));
        if (val == null) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(val));
    }

    private Map<Object, Object> findHashByToken(String rtKey) {
        return stringRedisTemplate.opsForHash().entries(rtKey);
    }

    private Long extractUserId(String rtKey) {
        Object userId = stringRedisTemplate.opsForHash().get(rtKey, "userId");
        if (userId == null) {
            return null;
        }
        return Long.parseLong(userId.toString());
    }

    private String rtKey(String token) {
        return RT_KEY_PREFIX + token;
    }

    private String revokedAfterKey(Long userId) {
        return String.format(REVOKED_AFTER_KEY, userId);
    }

    private long epochNow() {
        return Instant.now().getEpochSecond();
    }
}
