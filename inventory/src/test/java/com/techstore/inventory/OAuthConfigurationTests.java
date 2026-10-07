package com.techstore.inventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.techstore.inventory.dto.RegisterRequest;
import com.techstore.inventory.model.Role;
import com.techstore.inventory.repository.*;
import com.techstore.inventory.service.AuthService;
import com.techstore.inventory.security.OAuthLoginSuccessHandler;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"GOOGLE_CLIENT_ID=test-client","GOOGLE_CLIENT_SECRET=test-secret",
    "GITHUB_CLIENT_ID=test-client","GITHUB_CLIENT_SECRET=test-secret",
    "OAUTH_BASE_URL=http://localhost:8081", "BOOTSTRAP_ADMIN_EMAIL=",
    "spring.datasource.url=jdbc:h2:mem:oauth-tests;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class OAuthConfigurationTests {
    @Autowired ClientRegistrationRepository registrations;
    @Autowired MockMvc mvc;
    @Autowired OAuthLoginSuccessHandler handler;
    @Autowired UserRepository users;
    @Autowired AuthChallengeRepository challenges;
    @Autowired AuthService auth;
    @BeforeEach void clean(){challenges.deleteAll();users.deleteAll();}
    private MockHttpServletResponse callback(String subject,String email,boolean verified) throws Exception {
        var profile=new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("sub",subject,"email",email,"email_verified",verified,"name","Social User"),"sub");
        var oauth=new OAuth2AuthenticationToken(profile,profile.getAuthorities(),"google");
        var request=new MockHttpServletRequest();var session=request.getSession();
        var response=new MockHttpServletResponse();handler.onAuthenticationSuccess(request,response,oauth);
        assertTrue(((org.springframework.mock.web.MockHttpSession)session).isInvalid());
        return response;
    }
    @Test void bothProvidersArePreparedWithCorrectCallbacksAndScopes() throws Exception {
        assertEquals("http://localhost:8081/login/oauth2/code/google",registrations.findByRegistrationId("google").getRedirectUri());
        assertTrue(registrations.findByRegistrationId("google").getScopes().contains("openid"));
        assertTrue(registrations.findByRegistrationId("github").getScopes().contains("user:email"));
        assertEquals("http://localhost:8081/login/oauth2/code/github",registrations.findByRegistrationId("github").getRedirectUri());
        mvc.perform(get("/api/auth/providers")).andExpect(status().isOk()).andExpect(jsonPath("$.google").value(true));
    }
    @Test void oauthHandshakeRedirectsButSessionCannotAuthenticateApi() throws Exception {
        var r=mvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection()).andReturn();
        assertTrue(r.getResponse().getRedirectedUrl().startsWith("https://accounts.google.com"));
        var session=(org.springframework.mock.web.MockHttpSession)r.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
    }
    @Test void verifiedSocialIdentityCreatesEmployeeAndExchangesSingleUseCode() throws Exception {
        String redirect=callback("google-subject","social@example.com",true).getRedirectedUrl();
        assertTrue(redirect.startsWith("/#oauthCode="));
        var user=users.findByEmail("social@example.com").orElseThrow();
        assertEquals(Role.EMPLEADO,user.getRole());assertNull(user.getPassword());
        assertEquals("GOOGLE",user.getProvider());assertEquals("google-subject",user.getProviderSubject());
        String code=redirect.substring("/#oauthCode=".length());
        mvc.perform(post("/api/auth/oauth2/exchange").contentType("application/json").content("{\"code\":\""+code+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
        mvc.perform(post("/api/auth/oauth2/exchange").contentType("application/json").content("{\"code\":\""+code+"\"}"))
            .andExpect(status().isUnauthorized());
        callback("google-subject","social@example.com",true);assertEquals(1,users.count());
    }
    @Test void socialCallbackHonorsMfaBeforeIssuingJwt() throws Exception {
        callback("mfa-subject","social@example.com",true);
        auth.setupMfa("social@example.com");var user=users.findByEmail("social@example.com").orElseThrow();
        String totp=new dev.samstevens.totp.code.DefaultCodeGenerator().generate(user.getMfaSecret(),System.currentTimeMillis()/1000/30);
        auth.enableMfa(user.getEmail(),totp);
        String redirect=callback("mfa-subject","social@example.com",true).getRedirectedUrl();
        String code=redirect.substring("/#oauthCode=".length());
        mvc.perform(post("/api/auth/oauth2/exchange").contentType("application/json").content("{\"code\":\""+code+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(true))
            .andExpect(jsonPath("$.challengeToken").exists()).andExpect(jsonPath("$.token").doesNotExist());
    }
    @Test void socialCallbackRejectsUnverifiedEmailAndDoesNotLinkLocalAccount() throws Exception {
        assertEquals("/?oauthError=true",callback("unverified","social@example.com",false).getRedirectedUrl());
        assertEquals(0,users.count());
        var r=new RegisterRequest();r.setEmail("local@example.com");r.setPassword("TestPass1!");r.setFullName("Local User");r.setStore("Central");
        auth.register(r);
        assertEquals("/?oauthError=true",callback("other-subject","local@example.com",true).getRedirectedUrl());
        assertEquals("LOCAL",users.findByEmail("local@example.com").orElseThrow().getProvider());
        assertEquals(0,challenges.count());
    }
    @Test void lockedSocialAccountCannotReceiveExchangeCode() throws Exception {
        callback("locked-subject","social@example.com",true);challenges.deleteAll();
        var user=users.findByEmail("social@example.com").orElseThrow();user.setAccountLocked(true);users.save(user);
        assertEquals("/?oauthError=true",callback("locked-subject","social@example.com",true).getRedirectedUrl());
        assertEquals(0,challenges.count());
    }
}
