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

    private static final String RT_PREFIX = "rt:";
    private static final String FAMILY_PREFIX = "family:";
    private static final String FAMILIES_ZSET = "user:%d:families";
    private static final String AT_BLACKLIST = "bl:at:%s";

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
    private final RedisScript<Long> revokeScript = RedisScript.of(SAVE_SCRIPT, Long.class);

    public String save(Long userId) {
        String rtId = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();

        String key = rtKey(rtId);

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
                List.of(rtKey(rtId), familyKey(familyId), familiesZSet(userId)),
                String.valueOf(userId),
                familyId,
                String.valueOf(now),
                String.valueOf(expiresAt),
                String.valueOf(ttl)
        );

        return rtId;
    }

    public RotateResult rotate(String oldRtId) {
        String newRtId = UUID.randomUUID().toString();
        long now = epochNow();
        long expiresAt = now + refreshExpirationMs / 1000;
        long ttl = refreshExpirationMs / 1000;

        List<?> result = stringRedisTemplate.execute(
                rotateScript,
                List.of(rtKey(oldRtId), rtKey(newRtId)),
                String.valueOf(now),
                String.valueOf(expiresAt),
                String.valueOf(ttl)
        );

        if (result == null || result.size() < 3) {
            throw new BusinessException(ErrorCode.TOKEN_ROTATION_FAILED);
        }

        long code = toLong(result.get(0));
        long userId =  toLong(result.get(1));
        String familyId = result.get(2) == null ? "" : result.get(2).toString();

        return switch ((int) code) {
            case 0 -> throw new BusinessException(ErrorCode.INVALID_TOKEN);
            case -1 -> throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
            case -2 -> {
                revokeAll(userId);
                throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
            }
            default -> new RotateResult(newRtId, userId, familyId);
        };
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
        return RT_PREFIX + token;
    }

    private String familyKey(String familyId) {
        return FAMILY_PREFIX + familyId;
    }

    private String familiesZSet(Long userId) {
        return String.format(FAMILIES_ZSET, userId);
    }

    private String atBlacklistKey(String jti) {
        return String.format(AT_BLACKLIST, jti);
    }

    private long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalStateException("Expected Number but got: " + o);
    }

    private long epochNow() {
        return Instant.now().getEpochSecond();
    }
}
