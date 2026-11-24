package cl.duoc.gasvolcan.notificaciones.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
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
    SecurityFilterChain seguridad(
            HttpSecurity http, JwtAuthenticationConverter permisosJwt,
            @Value("${app.notificaciones.token}") String tokenInterno) throws Exception {
        if (tokenInterno.length() < 32) {
            throw new IllegalStateException("Configura NOTIFICACIONES_INTERNO_TOKEN con al menos 32 caracteres.");
        }
        byte[] token = tokenInterno.getBytes(StandardCharsets.UTF_8);
        AuthenticationEntryPoint sinSesion = (request, response, error) -> {
            if (request.getRequestURI().startsWith("/api/v1/interno/")) {
                enviarError(response, HttpServletResponse.SC_FORBIDDEN, "Clave interna inválida.");
                return;
            }
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            enviarError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token de acceso válido requerido.");
        };
        AccessDeniedHandler sinPermiso = (request, response, error) ->
                enviarError(response, HttpServletResponse.SC_FORBIDDEN, "No tienes permiso para esta operación.");

        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(sinSesion).accessDeniedHandler(sinPermiso))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(permisosJwt))
                        .authenticationEntryPoint(sinSesion)
                        .accessDeniedHandler(sinPermiso))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers("/api/v1/interno/notificaciones", "/api/v1/interno/notificaciones/**")
                        .access((identidad, peticion) -> {
                            String valor = peticion.getRequest().getHeader("X-Notificaciones-Key");
                            boolean valida = valor != null && MessageDigest.isEqual(token, valor.getBytes(StandardCharsets.UTF_8));
                            return new AuthorizationDecision(valida);
                        })
                        .requestMatchers(HttpMethod.GET, "/api/v1/notificaciones", "/api/v1/notificaciones/*")
                        .hasAnyRole("ADMIN", "OPERADORA")
                        .requestMatchers(HttpMethod.POST, "/api/v1/notificaciones").hasAnyRole("ADMIN", "OPERADORA")
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
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/notificaciones/**", cors);
        return source;
    }

    private static void enviarError(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
    }
}
