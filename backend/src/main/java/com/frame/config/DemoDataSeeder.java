package com.frame.config;

import com.frame.domain.entity.User;
import com.frame.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a demo login (demo@frame.dev / password) on first startup so the app is usable the moment
 * `docker-compose up` finishes, with no registration step required. Runs through the real
 * PasswordEncoder bean rather than a hardcoded bcrypt hash in a SQL migration — simpler to read
 * and can't drift out of sync with whatever encoder/strength the app actually uses.
 *
 * There is deliberately no seeded *video* here: doing that convincingly would need either a real
 * sample video file checked into the repo or a synthesized one, and this project didn't want to
 * ship either without your say-so. Uploading a short clip after logging in exercises the full
 * pipeline in well under 10 seconds anyway.
 */
@Component
@ConditionalOnProperty(prefix = "frame.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_EMAIL = "demo@frame.dev";
    private static final String DEMO_PASSWORD = "password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(DEMO_EMAIL)) {
            return;
        }
        userRepository.save(User.builder()
                .email(DEMO_EMAIL)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .displayName("Demo User")
                .build());
        log.info("Seeded demo login: {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    }
}
