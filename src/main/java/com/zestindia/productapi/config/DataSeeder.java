package com.zestindia.productapi.config;

import com.zestindia.productapi.model.AppUser;
import com.zestindia.productapi.model.Role;
import com.zestindia.productapi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userRepository.save(new AppUser("admin", passwordEncoder.encode("admin123"), Role.ROLE_ADMIN));
            userRepository.save(new AppUser("user", passwordEncoder.encode("user123"), Role.ROLE_USER));
            log.info("Seeded default accounts -> admin/admin123 (ROLE_ADMIN), user/user123 (ROLE_USER)");
        }
    }
}
