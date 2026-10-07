package com.techstore.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:context-test", "BOOTSTRAP_ADMIN_EMAIL=",
    "GOOGLE_CLIENT_ID=", "GOOGLE_CLIENT_SECRET=", "GITHUB_CLIENT_ID=", "GITHUB_CLIENT_SECRET="})
class InventoryApplicationTests {

	@Test
	void contextLoads() {
	}

}
