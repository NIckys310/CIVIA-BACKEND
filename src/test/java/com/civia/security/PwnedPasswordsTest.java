package com.civia.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PwnedPasswordsTest {

    // SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
    private static final String PREFIX = "5BAA6";
    private static final String SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

    @Test
    void onlyTheFiveCharPrefixLeavesTheServerAndTheSuffixIsMatchedLocally() {
        List<String> sent = new ArrayList<>();
        PwnedPasswords p = new PwnedPasswords(true, prefix -> {
            sent.add(prefix);
            return "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" + SUFFIX.toLowerCase() + ":9545824\r\n";
        });
        assertThat(p.breachCount("password")).isEqualTo(9_545_824L);
        assertThat(sent).containsExactly(PREFIX);
    }

    @Test
    void paddingEntriesAndMissingSuffixesCountAsNotBreached() {
        PwnedPasswords p = new PwnedPasswords(true, prefix -> SUFFIX + ":0\n0018A45C4D1DEF81644B54AB7F969B88D65:3\n");
        assertThat(p.breachCount("password")).isZero();
        assertThat(new PwnedPasswords(true, prefix -> "0018A45C4D1DEF81644B54AB7F969B88D65:3").breachCount("password")).isZero();
    }

    @Test
    void failsOpenWhenTheServiceIsUnavailableAndDoesNothingWhenDisabled() {
        assertThat(new PwnedPasswords(true, prefix -> { throw new IOException("sin red"); }).breachCount("password")).isZero();
        List<String> sent = new ArrayList<>();
        assertThat(new PwnedPasswords(false, prefix -> { sent.add(prefix); return SUFFIX + ":5"; }).breachCount("password")).isZero();
        assertThat(sent).isEmpty();
    }
}
