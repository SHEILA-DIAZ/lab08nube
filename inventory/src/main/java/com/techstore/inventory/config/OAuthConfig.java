package com.techstore.inventory.config;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.env.Environment;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@Configuration
public class OAuthConfig {
    static boolean ready(Environment e,String provider) {
        return !e.getProperty(provider+"_CLIENT_ID","").isBlank() && !e.getProperty(provider+"_CLIENT_SECRET","").isBlank();
    }
    public static class CredentialsPresent implements Condition {
        @Override public boolean matches(ConditionContext c, AnnotatedTypeMetadata m) {
            return ready(c.getEnvironment(),"GOOGLE") || ready(c.getEnvironment(),"GITHUB");
        }
    }
    @Bean @Conditional(CredentialsPresent.class)
    public ClientRegistrationRepository registrations(Environment e) {
        List<ClientRegistration> list=new ArrayList<>();
        if(ready(e,"GOOGLE")) list.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
            .clientId(e.getProperty("GOOGLE_CLIENT_ID")).clientSecret(e.getProperty("GOOGLE_CLIENT_SECRET"))
            .redirectUri(e.getProperty("OAUTH_BASE_URL","http://localhost:8081")+"/login/oauth2/code/google").build());
        if(ready(e,"GITHUB")) list.add(CommonOAuth2Provider.GITHUB.getBuilder("github")
            .clientId(e.getProperty("GITHUB_CLIENT_ID")).clientSecret(e.getProperty("GITHUB_CLIENT_SECRET"))
            .scope("read:user","user:email")
            .redirectUri(e.getProperty("OAUTH_BASE_URL","http://localhost:8081")+"/login/oauth2/code/github").build());
        return new InMemoryClientRegistrationRepository(list);
    }
    @RestController
    public static class ProvidersController {
        private final Environment env;
        public ProvidersController(Environment env) { this.env=env; }
        @GetMapping("/api/auth/providers")
        public Map<String,Boolean> providers() {
            return Map.of("google",ready(env,"GOOGLE"),"github",ready(env,"GITHUB"));
        }
    }
}