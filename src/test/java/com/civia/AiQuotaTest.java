package com.civia;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.civia.service.AiQuota;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AiQuotaTest {

    @Test
    void allowsUpToTheLimitPerUserAndThenRejects() {
        AiQuota quota = new AiQuota(3);
        UUID ana = UUID.randomUUID();
        for (int i = 0; i < 3; i++) quota.acquire(ana);
        assertThatThrownBy(() -> quota.acquire(ana))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");
    }

    @Test
    void eachUserHasItsOwnQuota() {
        AiQuota quota = new AiQuota(1);
        quota.acquire(UUID.randomUUID());
        assertThatCode(() -> quota.acquire(UUID.randomUUID())).doesNotThrowAnyException();
    }
}
