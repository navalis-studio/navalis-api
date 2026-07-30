package io.navalis.api.infrastructure.security;

import io.micrometer.observation.annotation.Observed;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class ObservedPasswordEncoder {

    private final PasswordEncoder passwordEncoder;

    public ObservedPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Observed(name = "crypto.bcrypt.encode")
    public String encode(CharSequence rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Observed(name = "crypto.bcrypt.matches")
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
