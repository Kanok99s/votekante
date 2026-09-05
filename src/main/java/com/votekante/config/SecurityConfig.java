package com.votekante.config;

import com.votekante.entities.User;
import com.votekante.repositories.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Security configuration.
 *
 * <ul>
 *   <li>Passwords are hashed with BCrypt – nobody, including an ADMIN or a
 *       DBA, can read a plaintext password from the DB.</li>
 *   <li>ADMIN can manage parties and elections; VOTER can cast one ballot per
 *       election and view results. Results are visible to both roles.</li>
 *   <li>Login/logout use Spring Security's form login bound to Thymeleaf
 *       templates; registration is handled by {@code AuthController}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * BCrypt encoder. Strength 10 is Spring's default and adequate for
     * a voting app; the hashes are salted automatically by BCrypt.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Loads users from the {@code app_user} table. Authorities are derived
     * straight from the Role enum (ROLE_VOTER / ROLE_ADMIN) which is what
     * {@code hasRole(...)} in the filter chain matches on.
     */
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPasswordHash())
                    .roles(user.getRole().name())
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF stays ON (all mutations are form POSTs carrying the token).
            // NOTE: Spring Security 6's default XorCsrfTokenRequestAttributeHandler
            // defers token materialisation, which would leave Thymeleaf's
            // ${_csrf.parameterName}/${_csrf.token} empty on rendered forms.
            // CsrfTokenRequestAttributeHandler exposes the token as a request
            // attribute for every request, exactly what the templates expect.
            .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(auth -> auth
                // Bootstrap + open pages
                .requestMatchers("/", "/hello", "/css/**", "/js/**", "/images/**",
                        "/favicon.ico", "/error").permitAll()
                // Public authentication pages
                .requestMatchers("/login", "/perform-login", "/register", "/perform-register").permitAll()
                // Public browsing: anyone may view the dashboard of open polls,
                // jump to a shared poll by code, or watch results live. Voting,
                // creating polls and managing polls still require an account
                // (see the role rules below and anyRequest().authenticated()).
                .requestMatchers("/polls/browse", "/poll/**", "/polls/join", "/join/**",
                        "/results", "/results/**").permitAll()
                // Role-based areas
                .requestMatchers("/voter", "/voter/**").hasRole("VOTER")
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                // Everything else (create/manage polls, account pages…) needs sign-in
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")                 // GET renders the Thymeleaf form
                .loginProcessingUrl("/perform-login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }
}
