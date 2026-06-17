package com.example.marklong.domain.stock.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class StockPriceRedisRepository {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String PRICE_KEY_PREFIX = "stock:price:";
    private static final Duration PRICE_TTL = Duration.ofMinutes(5);

    public void saveCurrentPrice(String stockCode, BigDecimal price) {
        stringRedisTemplate.opsForValue()
                .set(PRICE_KEY_PREFIX + stockCode, price.toPlainString(), PRICE_TTL);
    }

    public Optional<BigDecimal> findCurrentPrice(String stockCode) {
        String value = stringRedisTemplate.opsForValue().get(PRICE_KEY_PREFIX + stockCode);
        if (value == null){
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(value));
    }

    public Map<String, BigDecimal> findAllCurrentPrice(Collection<String> stockCodes) {
        List<String> keys = stockCodes.stream()
                .map(code -> PRICE_KEY_PREFIX + code)
                .toList();

        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        if (values == null || values.isEmpty()){
            return Map.of();
        }

        Map<String, BigDecimal> result = new HashMap<>();
        List<String> codeList = new ArrayList<>(stockCodes);
        for (int i = 0; i < codeList.size(); i++) {
            String v = values.get(i);
            if (v != null) {
                result.put(codeList.get(i), new BigDecimal(v));
            }
        }
        return result;
    }

    private static final String SUBSCRIBED_KEY = "stock:ws:subscribed";

    public void addSubscription(String stockCode) {
        stringRedisTemplate.opsForSet().add(SUBSCRIBED_KEY, stockCode);
    }

    public void removeSubscription(String stockCode) {
        stringRedisTemplate.opsForSet().remove(SUBSCRIBED_KEY, stockCode);
    }

    public Set<String> getSubscribedCodes() {
        Set<String> members = stringRedisTemplate.opsForSet().members(SUBSCRIBED_KEY);
        return members != null ? members : Set.of();
    }

    public boolean isSubscribed(String stockCode) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(SUBSCRIBED_KEY, stockCode));
    }
}
