package cl.duoc.gasvolcan.auth.config;

import cl.duoc.gasvolcan.auth.repository.UsuarioRepository;
import cl.duoc.gasvolcan.auth.security.JwtService;
import cl.duoc.gasvolcan.auth.service.AuthService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, @Value("${springdoc.api-docs.enabled}") boolean swagger)
			throws Exception {
		AuthenticationEntryPoint sinSesion = (solicitud, respuesta, ex) -> {
			respuesta.setHeader("WWW-Authenticate", "Bearer");
			errorJson(respuesta, 401, "Se requiere una sesión válida.");
		};
		return http.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(errores -> errores.authenticationEntryPoint(sinSesion)
						.accessDeniedHandler((solicitud, respuesta, ex) ->
								errorJson(respuesta, 403, "No tienes permisos para esta operación.")))
				.authorizeHttpRequests(auth -> {
					auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
					auth.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll();
					if (swagger) {
						auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
					}
					auth.requestMatchers("/api/auth/usuarios", "/api/auth/usuarios/**").hasRole("ADMIN")
							.requestMatchers(HttpMethod.GET, "/api/auth/repartidores").hasAnyRole("ADMIN", "OPERADORA")
							.requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
							.requestMatchers(HttpMethod.POST, "/api/auth/refresh").authenticated()
							.anyRequest().denyAll();
				})
				.oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(sinSesion).jwt(jwt -> jwt
						.jwtAuthenticationConverter(conversorJwt())))
				.build();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtService jwt, UsuarioRepository usuarios, @Value("${app.jwt.issuer}") String emisor) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwt.clave()).macAlgorithm(MacAlgorithm.HS256).build();
		JwtTimestampValidator vigencia = new JwtTimestampValidator(Duration.ZERO);
		vigencia.setAllowEmptyExpiryClaim(false);
		decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(new JwtIssuerValidator(emisor), vigencia,
				token -> {
					String correo = token.getSubject();
					if (correo != null && usuarios.findByCorreoIgnoreCase(correo)
							.filter(usuario -> AuthService.coincideSesion(token, usuario)).isPresent()) {
						return OAuth2TokenValidatorResult.success();
					}
					return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
				}));
		return decoder;
	}

	private void errorJson(HttpServletResponse respuesta, int estado, String mensaje) throws IOException {
		respuesta.setStatus(estado);
		respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
		respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
		respuesta.getWriter().write("{\"mensaje\":\"" + mensaje + "\"}");
	}

	private JwtAuthenticationConverter conversorJwt() {
		JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
		conversor.setJwtGrantedAuthoritiesConverter(token ->
				List.of(new SimpleGrantedAuthority("ROLE_" + token.getClaimAsString("rol"))));
		return conversor;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origins}") String origenes) {
		CorsConfiguration configuracion = new CorsConfiguration();
		configuracion.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::strip)
				.filter(origen -> !origen.isBlank()).toList());
		configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
		configuracion.setAllowedHeaders(List.of("Content-Type", "Authorization"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuracion);
		return source;
	}
}
