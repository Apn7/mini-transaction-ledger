package com.ekramul.ledger.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves the backend is alive and reachable.
 *
 * <p>Called once by the frontend on load, to show whether the API is reachable.
 *
 * <p>It reports that this process is answering HTTP, nothing more — it does not check the
 * database. Docker Compose gates the backend on the database's own {@code pg_isready}
 * healthcheck instead. A dependency-aware check is what Spring Boot Actuator provides.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}
}
