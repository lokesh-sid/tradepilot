package tradingbot.security.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * User entity for authentication and authorization
 * 
 * Stores user credentials, roles, and trading bot limits.
 * Supports both traditional username/password and OAuth2 authentication.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_oauth", columnList = "oauth_provider, oauth_id")
})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(unique = true, nullable = false, length = 100)
    private String email;
    
    @Column(nullable = false)
    private String password; // BCrypt hashed
    
    @Column(name = "full_name", length = 100)
    private String fullName;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 20)
    private Set<String> roles = new HashSet<>(); // ROLE_USER, ROLE_ADMIN, ROLE_PREMIUM
    
    @Column(name = "enabled")
    private boolean enabled = true;
    
    @Column(name = "account_locked")
    private boolean accountLocked = false;
    
    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "last_login")
    private Instant lastLogin;
    
    @Column(name = "last_password_change")
    private Instant lastPasswordChange;
    
    // OAuth2 fields (optional - for social login)
    @Column(name = "oauth_provider", length = 20)
    private String oauthProvider; // google, github, facebook
    
    @Column(name = "oauth_id")
    private String oauthId;
    
    // Trading Bot Limits
    @Column(name = "max_bots")
    private int maxBots = 10; // Maximum number of bots per user
    
    @Column(name = "max_api_calls_per_hour")
    private int maxApiCallsPerHour = 1000; // Rate limiting per user
    
    @Column(name = "max_positions_per_bot")
    private int maxPositionsPerBot = 5;
    
    @Column(name = "max_leverage")
    private int maxLeverage = 100; // Maximum leverage allowed
    
    // Account tier (for future subscription model)
    @Column(name = "account_tier", length = 20)
    private String accountTier = "FREE"; // FREE, BASIC, PREMIUM, ENTERPRISE
    
    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;
    
    // API Key for programmatic access (optional)
    @Column(name = "api_key", unique = true, length = 64)
    private String apiKey;
    
    @Column(name = "api_key_enabled")
    private boolean apiKeyEnabled = false;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        lastPasswordChange = Instant.now();
        
        // Default role
        if (roles.isEmpty()) {
            roles.add("ROLE_USER");
        }
    }
    
    // Constructors
    public User() {
    }
    
    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public Set<String> getRoles() {
        return roles;
    }
    
    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isAccountLocked() {
        return accountLocked;
    }
    
    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
    }
    
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
    
    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public Instant getLastPasswordChange() {
        return lastPasswordChange;
    }
    
    public void setLastPasswordChange(Instant lastPasswordChange) {
        this.lastPasswordChange = lastPasswordChange;
    }
    
    public String getOauthProvider() {
        return oauthProvider;
    }
    
    public void setOauthProvider(String oauthProvider) {
        this.oauthProvider = oauthProvider;
    }
    
    public String getOauthId() {
        return oauthId;
    }
    
    public void setOauthId(String oauthId) {
        this.oauthId = oauthId;
    }
    
    public int getMaxBots() {
        return maxBots;
    }
    
    public void setMaxBots(int maxBots) {
        this.maxBots = maxBots;
    }
    
    public int getMaxApiCallsPerHour() {
        return maxApiCallsPerHour;
    }
    
    public void setMaxApiCallsPerHour(int maxApiCallsPerHour) {
        this.maxApiCallsPerHour = maxApiCallsPerHour;
    }
    
    public int getMaxPositionsPerBot() {
        return maxPositionsPerBot;
    }
    
    public void setMaxPositionsPerBot(int maxPositionsPerBot) {
        this.maxPositionsPerBot = maxPositionsPerBot;
    }
    
    public int getMaxLeverage() {
        return maxLeverage;
    }
    
    public void setMaxLeverage(int maxLeverage) {
        this.maxLeverage = maxLeverage;
    }
    
    public String getAccountTier() {
        return accountTier;
    }
    
    public void setAccountTier(String accountTier) {
        this.accountTier = accountTier;
    }
    
    public Instant getSubscriptionExpiresAt() {
        return subscriptionExpiresAt;
    }
    
    public void setSubscriptionExpiresAt(Instant subscriptionExpiresAt) {
        this.subscriptionExpiresAt = subscriptionExpiresAt;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public boolean isApiKeyEnabled() {
        return apiKeyEnabled;
    }
    
    public void setApiKeyEnabled(boolean apiKeyEnabled) {
        this.apiKeyEnabled = apiKeyEnabled;
    }
    
    // Helper methods
    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.accountLocked = true;
        }
    }
    
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.accountLocked = false;
    }
    
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
    
    public void addRole(String role) {
        roles.add(role);
    }
    
    public void removeRole(String role) {
        roles.remove(role);
    }
}
