package io.navalis.api.infrastructure.config;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration
public class ObservabilityConfig {

    private static final String[] IGNORED_URI_PREFIXES = {
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui"
    };

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }

    @Bean
    public ObservationPredicate noInfraObservations() {
        return (name, context) -> {
            // Filter out scheduled tasks
            if (name.toLowerCase().contains("task")) {
                return false;
            }

            // Filter out all Spring Security observations
            if (name.contains("spring.security")) {
                return false;
            }

            // Filter out infra HTTP endpoints and CORS preflight
            if (context instanceof ServerRequestObservationContext serverContext) {
                String method = serverContext.getCarrier().getMethod();
                if ("OPTIONS".equalsIgnoreCase(method)) {
                    return false;
                }

                String uri = serverContext.getCarrier().getRequestURI();
                for (String prefix : IGNORED_URI_PREFIXES) {
                    if (uri.startsWith(prefix)) {
                        return false;
                    }
                }
            }

            return true;
        };
    }
}
