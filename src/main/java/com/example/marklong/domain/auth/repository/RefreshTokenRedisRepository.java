package com.example.marklong.domain.auth.repository;

import com.example.marklong.domain.auth.dto.RotateResult;
import com.example.marklong.domain.auth.dto.TokenIssueResult;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private static final String RT_PREFIX = "rt:";
    private static final String FAMILY_PREFIX = "family:";
    private static final String FAMILIES_ZSET = "user:%d:families";
    private static final String AT_BLACKLIST = "bl:at:%s";


    private static final String ROTATE_SCRIPT = """
            local oldRt = redis.call('HGETALL', KEYS[1])
            if #oldRt == 0 then
                return {0, 0, ''}
            end
            
            local familyId = nil
            local userId = nil
            local rtStatus = nil
            
            for i = 1, #oldRt, 2 do
                if oldRt[i] == 'familyId' then
                    familyId = oldRt[i + 1]
                elseif oldRt[i] == 'userId' then
                    userId = tonumber(oldRt[i + 1])
                elseif oldRt[i] == 'status' then
                    rtStatus = oldRt[i + 1]
                end
            end
            
            if familyId == nil or userId == nil or rtStatus == nil then
                return {-2, userId or 0, familyId or ''}
            end
            
            if rtStatus ~= 'ACTIVE' and rtStatus ~= 'ROTATED' then
                return {-2, userId, familyId}
            end
            
            local familyKey = 'family:' .. familyId
            local familyStatus = redis.call('HGET', familyKey, 'status')
            
            if familyStatus ~= 'ACTIVE' then
                return {-2, userId, familyId}
            end
            
            if rtStatus == 'ROTATED' then
                redis.call('HSET', familyKey, 'status', 'REVOKED')
                return {-1, userId, familyId}
            end
            
            redis.call('HSET', KEYS[1], 'status', 'ROTATED')
            
            redis.call('HSET', KEYS[2],
                'familyId',  familyId,
                'userId',    userId,
                'status',    'ACTIVE',
                'issuedAt',  ARGV[1],
                'expiresAt', ARGV[2])
            
            redis.call('EXPIRE', familyKey, ARGV[3])
            
            local familiesKey = 'user:' .. userId .. ':families'
            redis.call('ZADD', familiesKey, ARGV[1], familyId)
            
            return {1, userId, familyId}
            
            """;

    private static final String SAVE_SCRIPT = """
            redis.call('HSET', KEYS[1],
                'familyId',  ARGV[2],
                'userId',    ARGV[1],
                'status',   'ACTIVE',
                'issuedAt',  ARGV[3],
                'expiresAt', ARGV[4])
            redis.call('EXPIRE', KEYS[1], ARGV[5])
            
            redis.call('HSET', KEYS[2],
                'userId',    ARGV[1],
                'status',    'ACTIVE',
                'createdAt', ARGV[3])
            redis.call('EXPIRE', KEYS[2], ARGV[5])
            
            redis.call('ZADD', KEYS[3], ARGV[3], ARGV[2])
            
            return 1
            """;


    private static final String REVOKE_ALL_SCRIPT = """
            local families = redis.call('ZRANGE', KEYS[1], 0, -1)
            for _, fid in ipairs(families) do
                redis.call('HSET', 'family:' .. fid, 'status', 'REVOKED')
            end
            return 0
            """;

    private final RedisScript<List> rotateScript = RedisScript.of(ROTATE_SCRIPT, List.class);
    private final RedisScript<Long> saveScript = RedisScript.of(SAVE_SCRIPT, Long.class);
    private final RedisScript<Long> revokeScript = RedisScript.of(REVOKE_ALL_SCRIPT, Long.class);

    public TokenIssueResult save(Long userId) {
        String rtId = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();

        long now = epochNow();
        long expiresAt = now + refreshExpirationMs / 1000;
        long ttl = refreshExpirationMs / 1000;

        Long result = stringRedisTemplate.execute(
                saveScript,
                List.of(rtKey(rtId), familyKey(familyId), familiesZSet(userId)),
                String.valueOf(userId),
                familyId,
                String.valueOf(now),
                String.valueOf(expiresAt),
                String.valueOf(ttl)
        );

        if (result == null || result != 1L) {
            throw new BusinessException(ErrorCode.TOKEN_PROVIDER_ERROR);
        }

        return new TokenIssueResult(rtId, familyId);
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
        long userId = toLong(result.get(1));
        String familyId = result.get(2) == null ? "" : result.get(2).toString();

        return switch ((int) code) {
            // 정상 요청, 성공
            case 1 -> new RotateResult(newRtId, userId, familyId);

            // 존재하지 않는 토큰
            case 0 -> throw new BusinessException(ErrorCode.INVALID_TOKEN);

            // rotate된 토큰에 접근, 탈취 감지
            case -1 -> {
                // family.status는 이미 ROTATE_SCRIPT 안에서 REVOKED 처리됨
                // 여기서는 ZSET 정리만 선택적으로 수행
                removeFamilyFromUserSet(userId, familyId);

                throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
            }

            // 이미 탈취 의심으로 폐기된 토큰
            case -2 -> throw new BusinessException(ErrorCode.INVALID_TOKEN);

            default -> throw new BusinessException(ErrorCode.TOKEN_ROTATION_FAILED);
        };
    }

    public void logout(Long userId, String familyId) {
        stringRedisTemplate.opsForHash().put(familyKey(familyId), "status", "REVOKED");
        stringRedisTemplate.opsForZSet().remove(familiesZSet(userId), familyId);
    }

    public void revokeAll(Long userId) {
        stringRedisTemplate.execute(
                revokeScript,
                List.of(familiesZSet(userId))
        );
    }

    public void removeFamilyFromUserSet(Long userId, String familyId) {
        stringRedisTemplate.opsForZSet().remove(familiesZSet(userId), familyId);
    }

    public void blacklistAt(String jti, long remainingMs) {
        if (remainingMs <= 0) {
            return;
        }

        stringRedisTemplate.opsForValue().set(
                atBlacklistKey(jti),
                "1",
                remainingMs,
                TimeUnit.MILLISECONDS
        );
    }

    public boolean isATBlacklisted(String jti) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(atBlacklistKey(jti)));
    }

    public void cleanExpiredFamilies(Long userId) {
        stringRedisTemplate.opsForZSet()
                .removeRangeByScore(familiesZSet(userId), 0, epochNow());
    }

    public void cleanStaleFamilies(Long userId) {
        String key = familiesZSet(userId);

        Set<String> familyIds = stringRedisTemplate.opsForZSet().range(key, 0, -1);
        if (familyIds == null || familyIds.isEmpty()) {
            return;
        }

        for (String familyId : familyIds) {
            Boolean exists = stringRedisTemplate.hasKey(familyKey(familyId));
            if (!Boolean.TRUE.equals(exists)) {
                stringRedisTemplate.opsForZSet().remove(key, familyId);
            }
        }
    }

    public void enforceMaxFamilies(Long userId, int maxCount) {
        String key = familiesZSet(userId);

        cleanStaleFamilies(userId);

        Long count = stringRedisTemplate.opsForZSet().zCard(key);
        if (count == null || count <= maxCount) {
            return;
        }

        long overflow = count - maxCount;

        Set<String> oldFamilyIds = stringRedisTemplate.opsForZSet()
                .range(key, 0, overflow - 1);

        if (oldFamilyIds == null || oldFamilyIds.isEmpty()) {
            return;
        }

        for (String familyId : oldFamilyIds) {
            stringRedisTemplate.opsForHash()
                    .put(familyKey(familyId), "status", "REVOKED");

            stringRedisTemplate.opsForZSet()
                    .remove(key, familyId);
        }
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
