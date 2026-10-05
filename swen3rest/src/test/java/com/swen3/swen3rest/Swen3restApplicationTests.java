package com.swen3.swen3rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

// replaces the PostgreSQL datasource with an in-memory H2 database,
// so the full context starts without a running database (e.g. in CI)
@SpringBootTest
@AutoConfigureTestDatabase
class Swen3restApplicationTests {

	@Test
	void contextLoads() {
	}

}
