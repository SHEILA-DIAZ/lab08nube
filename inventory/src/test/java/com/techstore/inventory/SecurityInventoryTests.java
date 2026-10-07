package com.techstore.inventory;
import com.techstore.inventory.dto.*;
import com.techstore.inventory.model.*;
import com.techstore.inventory.repository.*;
import com.techstore.inventory.security.JwtService;
import com.techstore.inventory.service.AuthService;
import com.techstore.inventory.service.MfaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:security-tests;DB_CLOSE_DELAY=-1",
    "BOOTSTRAP_ADMIN_EMAIL=", "GOOGLE_CLIENT_ID=", "GOOGLE_CLIENT_SECRET=",
    "GITHUB_CLIENT_ID=", "GITHUB_CLIENT_SECRET="}) @AutoConfigureMockMvc
class SecurityInventoryTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired AuthChallengeRepository challenges;
    @Autowired AuthService auth;
    @Autowired MfaService mfa;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("bootstrap")
    org.springframework.boot.CommandLineRunner bootstrap;
    @Autowired JwtService jwt;
    final ObjectMapper json=new ObjectMapper();
    @BeforeEach void clean() { challenges.deleteAll(); products.deleteAll(); users.deleteAll(); }
    @Test void demoProductsAreLoadedOnceAndExistingStockIsPreserved() throws Exception {
        bootstrap.run();
        var demo=products.findByStoreOrderByIdAsc("Lima Centro");assertEquals(5,demo.size());
        var laptop=demo.stream().filter(p -> p.getName().equals("Laptop Lenovo")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("2500.00"),laptop.getPrice());assertEquals(10,laptop.getStock());
        assertEquals(70,demo.stream().mapToInt(Product::getStock).sum());
        laptop.setStock(7);products.save(laptop);bootstrap.run();
        assertEquals(5,products.count());assertEquals(7,products.findById(laptop.getId()).orElseThrow().getStock());
        User manager=user(Role.GERENTE,"Lima Centro"),employee=user(Role.EMPLEADO,"Lima Centro"),auditor=user(Role.AUDITOR,"Central");
        for(var u:List.of(manager,employee,auditor))mvc.perform(get("/api/products").header("Authorization",bearer(u)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
    }
    User user(Role role,String store) {
        RegisterRequest r=new RegisterRequest();r.setEmail(role.name().toLowerCase()+"@test.com");
        r.setPassword("TestPass1!");r.setFullName("Test "+role);r.setStore(store);
        User u=auth.register(r);u.setRole(role);return users.save(u);
    }
    Product product(String store) {
        Product p=new Product();p.setName("Laptop");p.setPrice(new BigDecimal("99.90"));p.setStock(10);p.setStore(store);return products.save(p);
    }
    String bearer(User u) { return "Bearer "+jwt.generateToken(u); }
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder r,Object body) throws Exception {
        return r.contentType("application/json").content(json.writeValueAsString(body));
    }
    Map<String,Object> result(MvcResult r) throws Exception { return json.readValue(r.getResponse().getContentAsString(),Map.class); }
    Map<String,String> registration(String email,String password) {
        return Map.of("email",email,"password",password,"fullName","Nombre Completo","store","Central");
    }
    Map<String,String> login(User u,String password) { return Map.of("email",u.getEmail(),"password",password); }
    String code(User u) throws Exception {
        return new DefaultCodeGenerator().generate(u.getMfaSecret(),System.currentTimeMillis()/1000/30);
    }
    User mfaUser() {
        User u=user(Role.EMPLEADO,"Central");auth.setupMfa(u.getEmail());u=users.findById(u.getId()).orElseThrow();
        u.setMfaEnabled(true);return users.save(u);
    }
    @Test void registrationPreservesValidationBCryptAndEmailUniqueness() throws Exception {
        mvc.perform(body(post("/api/auth/register"),registration("EMP@test.com","TestPass1!"))).andExpect(status().isOk());
        User u=users.findByEmail("emp@test.com").orElseThrow();
        assertTrue(u.getPassword().startsWith("$2"));assertNotEquals("TestPass1!",u.getPassword());
        assertEquals(Role.EMPLEADO,u.getRole());
        mvc.perform(body(post("/api/auth/register"),registration("emp@TEST.COM","TestPass1!"))).andExpect(status().isBadRequest());
        for(String password:List.of("short","testpass1!","TestPass!","TestPass1")) {
            mvc.perform(body(post("/api/auth/register"),registration("other@test.com",password))).andExpect(status().isBadRequest());
        }
        mvc.perform(body(post("/api/auth/register"),Map.of("email","bad-email","password","TestPass1!","fullName","","store",""))).andExpect(status().isBadRequest());
    }
    @Test void protectedEndpointAndInvalidTokenReturn401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/setup")).andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/auth/mfa/enable"),Map.of("code","123456"))).andExpect(status().isUnauthorized());
    }
    @Test void loginIssuesJwtAndMeNeverExposesSecrets() throws Exception {
        User u=user(Role.EMPLEADO,"Central");
        var r=mvc.perform(body(post("/api/auth/login"),login(u,"TestPass1!"))).andExpect(status().isOk()).andReturn();
        String token=(String)result(r).get("token");assertNotNull(token);
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("EMPLEADO")).andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.mfaSecret").doesNotExist());
    }
    @Test void fiveBadPasswordsLockAccountAndRevokeToken() throws Exception {
        User u=user(Role.EMPLEADO,"Central");String token=bearer(u);
        for(int i=0;i<4;i++)mvc.perform(body(post("/api/auth/login"),login(u,"bad"))).andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/auth/login"),login(u,"bad"))).andExpect(status().isLocked());
        mvc.perform(body(post("/api/auth/login"),login(u,"TestPass1!"))).andExpect(status().isLocked());
        mvc.perform(get("/api/me").header("Authorization",token)).andExpect(status().isUnauthorized());
        assertEquals(5,users.findById(u.getId()).orElseThrow().getFailedAttempts());
    }
    @Test void successfulLoginResetsPasswordAttempts() throws Exception {
        User u=user(Role.EMPLEADO,"Central");
        mvc.perform(body(post("/api/auth/login"),login(u,"bad"))).andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/auth/login"),login(u,"TestPass1!"))).andExpect(status().isOk());
        assertEquals(0,users.findById(u.getId()).orElseThrow().getFailedAttempts());
    }
    @Test void setupEnableAndMfaLoginRequireSecondFactor() throws Exception {
        User u=user(Role.EMPLEADO,"Central");String old=bearer(u);
        mvc.perform(post("/api/auth/mfa/setup").header("Authorization",old)).andExpect(status().isOk())
            .andExpect(jsonPath("$.secret").exists()).andExpect(jsonPath("$.otpauthUrl").exists());
        u=users.findById(u.getId()).orElseThrow();
        var enabled=mvc.perform(body(post("/api/auth/mfa/enable").header("Authorization",old),Map.of("code",code(u))))
            .andExpect(status().isOk()).andReturn();
        assertNotNull(result(enabled).get("token"));
        mvc.perform(get("/api/me").header("Authorization",old)).andExpect(status().isUnauthorized());
        String fresh="Bearer "+result(enabled).get("token");
        mvc.perform(post("/api/auth/mfa/setup").header("Authorization",fresh)).andExpect(status().isBadRequest());
        var first=result(mvc.perform(body(post("/api/auth/login"),login(u,"TestPass1!"))).andExpect(status().isOk()).andReturn());
        assertEquals(true,first.get("mfaRequired"));assertFalse(first.containsKey("token"));
        String challenge=(String)first.get("challengeToken");
        mvc.perform(get("/api/me").header("Authorization","Bearer "+challenge)).andExpect(status().isUnauthorized());
        var verified=result(mvc.perform(body(post("/api/auth/mfa/verify"),Map.of("challengeToken",challenge,"code",code(u))))
            .andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+verified.get("token"))).andExpect(status().isOk());
        mvc.perform(body(post("/api/auth/mfa/verify"),Map.of("challengeToken",challenge,"code",code(u))))
            .andExpect(status().isUnauthorized());
    }
    @Test void threeMfaErrorsAcrossChallengesLockAccount() throws Exception {
        User u=mfaUser();
        String wrongCode="000000";
        while(mfa.verifyCode(u.getMfaSecret(),wrongCode))wrongCode=String.format("%06d",Integer.parseInt(wrongCode)+1);
        for(int i=0;i<3;i++) {
            String c=(String)result(mvc.perform(body(post("/api/auth/login"),login(u,"TestPass1!"))).andReturn()).get("challengeToken");
            mvc.perform(body(post("/api/auth/mfa/verify"),Map.of("challengeToken",c,"code",wrongCode)))
                .andExpect(i==2?status().isLocked():status().isUnauthorized());
        }
        mvc.perform(body(post("/api/auth/login"),login(u,"TestPass1!"))).andExpect(status().isLocked());
        assertEquals(3,users.findById(u.getId()).orElseThrow().getMfaFailedAttempts());
    }
    @Test void expiredAndMissingMfaChallengesAreRejected() throws Exception {
        User u=mfaUser();String c=auth.newChallenge(u,"MFA");AuthChallenge challenge=challenges.findById(c).orElseThrow();
        challenge.setExpiresAt(Instant.now().minusSeconds(1));challenges.save(challenge);
        mvc.perform(body(post("/api/auth/mfa/verify"),Map.of("challengeToken",c,"code",code(u)))).andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/auth/mfa/verify"),Map.of("email",u.getEmail(),"code",code(u)))).andExpect(status().isUnauthorized());
    }
    @Test void employeeCanUpdateOwnStockButNotPricesOrOtherStore() throws Exception {
        User u=user(Role.EMPLEADO,"Central");Product own=product("Central"),other=product("Norte");String t=bearer(u);
        mvc.perform(get("/api/products").header("Authorization",t)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(body(patch("/api/products/"+own.getId()+"/stock").header("Authorization",t),Map.of("stock",20))).andExpect(status().isOk());
        mvc.perform(body(put("/api/products/"+own.getId()).header("Authorization",t),Map.of("name","x","price",1,"stock",20,"store","Central"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/"+own.getId()).header("Authorization",t)).andExpect(status().isForbidden());
        mvc.perform(body(patch("/api/products/"+other.getId()+"/stock").header("Authorization",t),Map.of("stock",20))).andExpect(status().isForbidden());
        assertEquals(new BigDecimal("99.90"),products.findById(own.getId()).orElseThrow().getPrice());
    }
    @Test void managerCrudIsRestrictedToOwnStoreIncludingTransfers() throws Exception {
        User u=user(Role.GERENTE,"Central");Product own=product("Central"),other=product("Norte");String t=bearer(u);
        mvc.perform(body(post("/api/products").header("Authorization",t),Map.of("name","Teclado","price",25,"stock",5,"store","Central"))).andExpect(status().isCreated());
        mvc.perform(body(post("/api/products").header("Authorization",t),Map.of("name","Teclado","price",25,"stock",5,"store","Norte"))).andExpect(status().isForbidden());
        mvc.perform(body(put("/api/products/"+own.getId()).header("Authorization",t),Map.of("name","Editado","price",50,"stock",8,"store","Central"))).andExpect(status().isOk());
        mvc.perform(body(put("/api/products/"+own.getId()).header("Authorization",t),Map.of("name","Editado","price",50,"stock",8,"store","Norte"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/"+other.getId()).header("Authorization",t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/"+other.getId()).header("Authorization",t)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/"+own.getId()).header("Authorization",t)).andExpect(status().isNoContent());
    }
    @Test void auditorReadsAllStoresAndUsersButCannotMutate() throws Exception {
        User u=user(Role.AUDITOR,"Central");Product p=product("Norte");product("Central");String t=bearer(u);
        mvc.perform(get("/api/products").header("Authorization",t)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/users").header("Authorization",t)).andExpect(status().isOk()).andExpect(jsonPath("$[0].mfaSecret").doesNotExist());
        mvc.perform(body(patch("/api/products/"+p.getId()+"/stock").header("Authorization",t),Map.of("stock",1))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/"+p.getId()).header("Authorization",t)).andExpect(status().isForbidden());
        mvc.perform(body(post("/api/products").header("Authorization",t),Map.of("name","x","price",1,"stock",0,"store","Central"))).andExpect(status().isForbidden());
        mvc.perform(body(put("/api/users/"+u.getId()).header("Authorization",t),Map.of("role","ADMIN","store","Central","fullName","x"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/users/"+u.getId()+"/unlock").header("Authorization",t)).andExpect(status().isForbidden());
    }
    @Test void administratorCanManageAllStoresAndAssignRoles() throws Exception {
        User admin=user(Role.ADMIN,"Central"), employee=user(Role.EMPLEADO,"Norte");Product p=product("Norte");String t=bearer(admin),old=bearer(employee);
        mvc.perform(body(put("/api/products/"+p.getId()).header("Authorization",t),Map.of("name","Updated","price",25,"stock",8,"store","Sur"))).andExpect(status().isOk());
        mvc.perform(body(put("/api/users/"+employee.getId()).header("Authorization",t),Map.of("role","GERENTE","store","Sur","fullName","Gerente Nuevo"))).andExpect(status().isOk());
        assertEquals(Role.GERENTE,users.findById(employee.getId()).orElseThrow().getRole());
        mvc.perform(get("/api/me").header("Authorization",old)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/products/"+p.getId()).header("Authorization",t)).andExpect(status().isNoContent());
    }
    @Test void administratorCanUnlockAndResetAllAttemptCounters() throws Exception {
        User admin=user(Role.ADMIN,"Central"),u=user(Role.EMPLEADO,"Central");
        u.setAccountLocked(true);u.setFailedAttempts(5);u.setMfaFailedAttempts(3);users.save(u);
        mvc.perform(post("/api/users/"+u.getId()+"/unlock").header("Authorization",bearer(admin))).andExpect(status().isOk());
        u=users.findById(u.getId()).orElseThrow();assertFalse(u.isAccountLocked());assertEquals(0,u.getFailedAttempts());assertEquals(0,u.getMfaFailedAttempts());
        mvc.perform(body(post("/api/auth/login"),login(u,"TestPass1!"))).andExpect(status().isOk());
    }
    @Test void reportsRespectScopeAndRole() throws Exception {
        Product p=product("Central");product("Norte");
        User manager=user(Role.GERENTE,"Central"), auditor=user(Role.AUDITOR,"Central"),employee=user(Role.EMPLEADO,"Central");
        mvc.perform(get("/api/reports/inventory").header("Authorization",bearer(manager))).andExpect(status().isOk())
            .andExpect(jsonPath("$.productCount").value(1)).andExpect(jsonPath("$.totalStock").value(10))
            .andExpect(jsonPath("$.inventoryValue").value(999.0));
        mvc.perform(get("/api/reports/inventory").header("Authorization",bearer(auditor))).andExpect(status().isOk()).andExpect(jsonPath("$.productCount").value(2));
        mvc.perform(get("/api/reports/inventory").header("Authorization",bearer(employee))).andExpect(status().isForbidden());
    }
    @Test void invalidProductDataIsRejectedAndMissingProductReturns404() throws Exception {
        User u=user(Role.ADMIN,"Central");String t=bearer(u);
        mvc.perform(body(post("/api/products").header("Authorization",t),Map.of("name","","price",-1,"stock",-1,"store",""))).andExpect(status().isBadRequest());
        Product p=product("Central");
        mvc.perform(body(patch("/api/products/"+p.getId()+"/stock").header("Authorization",t),Map.of("stock",-1))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/999999").header("Authorization",t)).andExpect(status().isNotFound());
    }
    @Test void oauthExchangeIsOneUseAndHonorsMfa() throws Exception {
        User u=mfaUser();String c=auth.newChallenge(u,"OAUTH");
        mvc.perform(body(post("/api/auth/oauth2/exchange"),Map.of("code",c))).andExpect(status().isOk())
            .andExpect(jsonPath("$.mfaRequired").value(true)).andExpect(jsonPath("$.token").doesNotExist());
        mvc.perform(body(post("/api/auth/oauth2/exchange"),Map.of("code",c))).andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/auth/oauth2/exchange"),Map.of("code",auth.newChallenge(u,"MFA")))).andExpect(status().isUnauthorized());
    }
    @Test void frontendAndProviderConfigurationArePublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/providers")).andExpect(status().isOk()).andExpect(jsonPath("$.google").value(false)).andExpect(jsonPath("$.github").value(false));
    }
}
