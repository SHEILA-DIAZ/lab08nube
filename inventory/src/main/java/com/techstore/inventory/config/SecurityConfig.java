package com.techstore.inventory.config;
import com.techstore.inventory.security.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.*;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    // Only OAuth handshake uses a session; API never accepts that session as authentication.
    @Bean @Order(1)
    public SecurityFilterChain oauthChain(HttpSecurity http, ObjectProvider<ClientRegistrationRepository> registrations,
                                          OAuthLoginSuccessHandler handler) throws Exception {
        http.securityMatcher("/oauth2/**", "/login/oauth2/**")
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        if (registrations.getIfAvailable()!=null) {
            http.oauth2Login(o -> o.successHandler(handler).failureHandler((req,res,ex) -> {
                if(req.getSession(false)!=null) req.getSession(false).invalidate();
                res.sendRedirect("/?oauthError=true");
            }));
        }
        return http.build();
    }
    @Bean @Order(2)
    public SecurityFilterChain apiChain(HttpSecurity http, JwtAuthFilter jwt) throws Exception {
        http.csrf(c -> c.disable())
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/index.html", "/app.js", "/style.css", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/mfa/verify",
                    "/api/auth/oauth2/exchange", "/api/auth/providers", "/h2-console/**").permitAll()
                .requestMatchers("/api/users/**").hasAnyRole("ADMIN","AUDITOR")
                .anyRequest().authenticated())
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}