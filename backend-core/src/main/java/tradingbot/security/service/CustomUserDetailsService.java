package tradingbot.security.service;

import static org.springframework.security.core.userdetails.User.*;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import tradingbot.security.entity.User;
import tradingbot.security.repository.UserRepository;

/**
 * Custom UserDetailsService implementation
 * 
 * Loads user-specific data for Spring Security authentication.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        if (!user.isEnabled()) {
            throw new UsernameNotFoundException("User account is disabled: " + username);
        }
        
        if (user.isAccountLocked()) {
            throw new UsernameNotFoundException("User account is locked: " + username);
        }
        
        // Convert roles Set to SimpleGrantedAuthority list
        var authorities = user.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        
        return builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(user.isAccountLocked())
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }
}
