package com.techstore.inventory.security;
import com.techstore.inventory.model.*;
import com.techstore.inventory.repository.UserRepository;
import com.techstore.inventory.service.AuthService;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;
import java.io.IOException;
import java.util.*;
@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final UserRepository users;
    private final AuthService auth;
    private final ObjectProvider<OAuth2AuthorizedClientService> clients;
    public OAuthLoginSuccessHandler(UserRepository users, AuthService auth, ObjectProvider<OAuth2AuthorizedClientService> clients) {
        this.users=users; this.auth=auth; this.clients=clients;
    }
    @Override
    public synchronized void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication authentication) throws IOException {
        try {
            OAuth2AuthenticationToken oauth=(OAuth2AuthenticationToken)authentication;
            String provider=oauth.getAuthorizedClientRegistrationId().toUpperCase(Locale.ROOT);
            OAuth2User profile=oauth.getPrincipal();
            String subject=profile.getName();
            User u=users.findByProviderAndProviderSubject(provider,subject).orElse(null);
            if(u==null) {
                String email=verifiedEmail(oauth,profile);
                if(email==null || email.isBlank()) throw new IllegalArgumentException("Email verificado requerido");
                email=AuthService.normalizeEmail(email);
                // Never automatically link a social identity to an existing local account.
                if(users.existsByEmail(email)) throw new IllegalArgumentException("Email registrado con otro proveedor");
                u=new User(); u.setEmail(email);
                String name=profile.getAttribute("name");
                u.setFullName(name==null || name.isBlank()?subject:name);
                u.setStore("Lima Centro"); u.setRole(Role.EMPLEADO);
                u.setProvider(provider); u.setProviderSubject(subject); u=users.save(u);
            }
            if(u.isAccountLocked()) throw new IllegalArgumentException("Cuenta bloqueada");
            String code=auth.newChallenge(u,"OAUTH");
            res.sendRedirect("/#oauthCode="+code);
        } catch(Exception ex) {
            res.sendRedirect("/?oauthError=true");
        } finally {
            SecurityContextHolder.clearContext();
            if(req.getSession(false)!=null) req.getSession(false).invalidate();
        }
    }
    private String verifiedEmail(OAuth2AuthenticationToken token, OAuth2User profile) {
        if("google".equals(token.getAuthorizedClientRegistrationId())) {
            return Boolean.TRUE.equals(profile.getAttribute("email_verified")) ? profile.getAttribute("email") : null;
        }
        OAuth2AuthorizedClient client=clients.getObject().loadAuthorizedClient("github",token.getName());
        List<Map<String,Object>> emails=RestClient.create("https://api.github.com").get().uri("/user/emails")
            .header("Authorization","Bearer "+client.getAccessToken().getTokenValue())
            .retrieve().body(new ParameterizedTypeReference<List<Map<String,Object>>>(){});
        if(emails==null) return null;
        return emails.stream().filter(e -> Boolean.TRUE.equals(e.get("verified")))
            .sorted(Comparator.comparing(e -> !Boolean.TRUE.equals(e.get("primary"))))
            .map(e -> (String)e.get("email")).findFirst().orElse(null);
    }
}
