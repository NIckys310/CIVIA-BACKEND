package com.civia.security;

import com.civia.domain.PasswordHistory;
import com.civia.domain.User;
import com.civia.repo.PasswordHistoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Contraseñas: hash Argon2id (ganador del Password Hashing Competition, recomendado por OWASP).
 *
 * <p>No hay longitud máxima: Argon2id procesa cualquier longitud (a diferencia de bcrypt, que
 * trunca en 72 bytes), así que se admiten frases de contraseña largas.
 */
@Service
public class PasswordService {

    public static final int MIN_LENGTH = 8;

    private static final Set<String> COMMON = Set.of(
            "12345678", "123456789", "1234567890", "password", "password1", "contraseña", "contrasena",
            "qwerty123", "qwertyuiop", "11111111", "00000000", "abc12345", "iloveyou", "admin123",
            "civia123", "civiaai1", "arquitectura", "12341234", "87654321", "passw0rd", "welcome1");

    /** Argon2id: 16 B de sal, 32 B de hash, 1 hilo, 19 MiB, 2 iteraciones (perfil OWASP). */
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    private final PasswordHistoryRepository history;
    private final PwnedPasswords pwned;

    public PasswordService(PasswordHistoryRepository history, PwnedPasswords pwned) {
        this.history = history;
        this.pwned = pwned;
    }

    public String hash(String raw) {
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String hash) {
        return raw != null && hash != null && encoder.matches(raw, hash);
    }

    /**
     * Valida la política; devuelve los problemas encontrados (vacío = válida). Si pasa las reglas
     * locales, se consulta Have I Been Pwned (k-anonymity, fail-open).
     */
    public List<String> problems(String raw, String email, String fullName) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.codePointCount(0, raw.length()) < MIN_LENGTH) {
            out.add("Debe tener al menos " + MIN_LENGTH + " caracteres.");
            return out;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) out.add("Es una contraseña demasiado común.");
        if (email != null && !email.isBlank() && lower.contains(email.toLowerCase(Locale.ROOT).split("@")[0]) && email.split("@")[0].length() >= 4) {
            out.add("No debe contener tu correo.");
        }
        if (lower.chars().distinct().count() < 4) out.add("Usa más variedad de caracteres.");
        boolean letters = raw.codePoints().anyMatch(Character::isLetter);
        boolean others = raw.codePoints().anyMatch(c -> !Character.isLetter(c));
        if (raw.length() < 12 && !(letters && others)) {
            out.add("Combina letras con números o símbolos (o usa una frase de 12+ caracteres).");
        }
        if (out.isEmpty() && pwned.breachCount(raw) > 0) {
            out.add("Esta contraseña aparece en filtraciones de datos conocidas (Have I Been Pwned); elige otra.");
        }
        return out;
    }

    public void requireStrong(String raw, String email, String fullName) {
        List<String> problems = problems(raw, email, fullName);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contraseña débil: " + String.join(" ", problems));
        }
    }

    /** Impide reutilizar la contraseña actual o las 5 anteriores. */
    public void requireNotReused(User user, String raw) {
        if (matches(raw, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser distinta de la actual.");
        }
        for (PasswordHistory h : history.findTop5ByUserIdOrderByCreatedAtDesc(user.getId())) {
            if (matches(raw, h.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No reutilices una de tus últimas contraseñas.");
            }
        }
    }

    public void remember(User user) {
        PasswordHistory h = new PasswordHistory();
        h.setUser(user);
        h.setPasswordHash(user.getPasswordHash());
        history.save(h);
    }
}
