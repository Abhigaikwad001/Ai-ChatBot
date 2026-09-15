package com.chatbot.platform.core.repository;

import com.chatbot.platform.core.domain.entity.User;
import com.chatbot.platform.core.domain.enums.UserRole;
import com.chatbot.platform.core.domain.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should persist user and assign UUID and audit timestamps")
    void shouldPersistUserWithAuditTimestamps() {
        User user = new User("doctor@hospital.org", "hashedPassword123", "Dr. Jane Smith");
        user.setRole(UserRole.ROLE_USER);
        user.setStatus(UserStatus.ACTIVE);

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("doctor@hospital.org");
    }

    @Test
    @DisplayName("Should find user by email")
    void shouldFindUserByEmail() {
        User user = new User("nurse@hospital.org", "hashedPassword123", "Nurse John Doe");
        userRepository.saveAndFlush(user);

        Optional<User> found = userRepository.findByEmail("nurse@hospital.org");

        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Nurse John Doe");
        assertThat(userRepository.existsByEmail("nurse@hospital.org")).isTrue();
        assertThat(userRepository.existsByEmail("unknown@hospital.org")).isFalse();
    }
}
