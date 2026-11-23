package cl.duoc.gasvolcan.pedidos.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        AuthenticationEntryPoint sinSesion = (request, response, error) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            responderError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token de acceso válido requerido.");
        };
        AccessDeniedHandler sinPermiso = (request, response, error) ->
                responderError(response, HttpServletResponse.SC_FORBIDDEN, "No tienes permiso para esta operación.");

        return http
                // Cada solicitud trae el token Bearer en su cabecera y se autentica de forma independiente.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(sinSesion).accessDeniedHandler(sinPermiso))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(sinSesion)
                        .accessDeniedHandler(sinPermiso))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/stock/*", "/api/v1/zonas")
                                .hasAnyRole("ADMIN", "VENDEDOR", "CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos", "/api/v1/pedidos/*")
                                .hasAnyRole("ADMIN", "VENDEDOR", "CLIENTE", "OPERADORA", "REPARTIDOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pedidos").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/interno/pedidos/*/asignacion").hasAnyRole("ADMIN", "OPERADORA")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/interno/pedidos/*/estado").hasAnyRole("ADMIN", "OPERADORA", "REPARTIDOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/interno/pedidos/*/ubicacion").hasAnyRole("ADMIN", "REPARTIDOR")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origins}") String origenes) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origenes.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    private static void responderError(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
    }
}
