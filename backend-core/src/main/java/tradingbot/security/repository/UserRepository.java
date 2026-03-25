package tradingbot.security.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tradingbot.security.entity.User;

/**
 * User Repository for database operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    /**
     * Find user by username
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find user by OAuth provider and OAuth ID
     */
    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);
    
    /**
     * Find user by API key
     */
    Optional<User> findByApiKey(String apiKey);
    
    /**
     * Check if username exists
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if API key exists
     */
    boolean existsByApiKey(String apiKey);
}
