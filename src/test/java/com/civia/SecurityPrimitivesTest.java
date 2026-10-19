package com.civia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.civia.security.EncryptedStringConverter;
import com.civia.security.KeyMaterial;
import com.civia.security.Totp;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecurityPrimitivesTest {

    @Test
    void totpMatchesRfc6238Vector() {
        // RFC 6238, apéndice B: semilla ASCII "12345678901234567890", T = 59 s → 94287082 (8 dígitos).
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertThat(Totp.verify(secret, "287082", Instant.ofEpochSecond(59))).isTrue();
        assertThat(Totp.verify(secret, "287083", Instant.ofEpochSecond(59))).isFalse();
        assertThat(Totp.verify(secret, "abc", Instant.ofEpochSecond(59))).isFalse();
    }

    @Test
    void fieldEncryptionIsRandomizedAndTamperEvident() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        EncryptedStringConverter c = new EncryptedStringConverter(new KeyMaterial(key, key, "target/unused-keys"));
        String a = c.convertToDatabaseColumn("+57 300 000 0000");
        String b = c.convertToDatabaseColumn("+57 300 000 0000");
        assertThat(a).startsWith("v1:").isNotEqualTo(b).doesNotContain("300");
        assertThat(c.convertToEntityAttribute(a)).isEqualTo("+57 300 000 0000");
        String tampered = a.substring(0, a.length() - 4) + (a.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThatThrownBy(() -> c.convertToEntityAttribute(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
