package com.techstore.inventory.config;
import com.techstore.inventory.dto.*;
import com.techstore.inventory.model.*;
import com.techstore.inventory.model.Role;
import com.techstore.inventory.repository.*;
import com.techstore.inventory.service.*;
import jakarta.validation.Validator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.math.BigDecimal;
@Configuration
public class BootstrapConfig {
    @Bean
    CommandLineRunner bootstrap(Environment env, AuthService auth, UserRepository users, ProductRepository products, Validator validator) {
        return args -> {
            String email=env.getProperty("BOOTSTRAP_ADMIN_EMAIL","");
            String password=env.getProperty("BOOTSTRAP_ADMIN_PASSWORD","");
            if(!email.isBlank() && !password.isBlank()) create(auth,users,validator,email,password,"Administrador",Role.ADMIN,"Central");
            if(java.util.Arrays.asList(env.getActiveProfiles()).contains("demo")) {
                for(Role r:Role.values()) create(auth,users,validator,r.name().toLowerCase()+"@techstore.test",
                    "TechStore1!","Demo "+r,r,"Lima Centro");
                if(products.count()==0) {
                    product(products,"Laptop Pro","2499.90",12,"Central");
                    product(products,"Mouse inalambrico","59.90",35,"Central");
                    product(products,"Monitor 24 pulgadas","699.00",8,"Norte");
                }
            }
            if(env.getProperty("app.inventory.demo-data",Boolean.class,true)) {
                demoProduct(products,"Laptop Lenovo","2500.00",10);
                demoProduct(products,"Mouse Logitech","80.00",25);
                demoProduct(products,"Teclado Redragon","150.00",15);
                demoProduct(products,"Monitor Samsung","850.00",8);
                demoProduct(products,"Audífonos HyperX","250.00",12);
            }
        };
    }
    private void create(AuthService auth,UserRepository users,Validator validator,String email,String password,String name,Role role,String store) {
        if(users.existsByEmail(AuthService.normalizeEmail(email))) return;
        RegisterRequest r=new RegisterRequest(); r.setEmail(email); r.setPassword(password); r.setFullName(name); r.setStore(store);
        if(!validator.validate(r).isEmpty()) throw new IllegalArgumentException("Credenciales bootstrap invalidas");
        User u=auth.register(r); u.setRole(role); users.save(u);
    }
    private void product(ProductRepository products,String name,String price,int stock,String store) {
        Product p=new Product(); p.setName(name); p.setPrice(new BigDecimal(price)); p.setStock(stock); p.setStore(store); products.save(p);
    }
    private void demoProduct(ProductRepository products,String name,String price,int stock) {
        if(!products.existsByNameAndStore(name,"Lima Centro")) product(products,name,price,stock,"Lima Centro");
    }
}
