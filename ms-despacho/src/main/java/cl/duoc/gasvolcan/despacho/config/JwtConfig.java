package cl.duoc.gasvolcan.despacho.config;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    private static final Set<String> ROLES = Set.of("ADMIN", "VENDEDOR", "CLIENTE", "OPERADORA", "REPARTIDOR");
    private static final Pattern CORREO = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secreto, @Value("${app.jwt.issuer}") String emisor) {
        byte[] clave = decodificarClave(secreto);

        // Ambos servicios usan HS256 con la misma clave compartida.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(clave, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator vigencia = new JwtTimestampValidator(Duration.ZERO);
        vigencia.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(emisor), vigencia, this::validarIdentidad));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // El correo del sub se mantiene como nombre del principal. El rol sale solo del JWT verificado.
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("rol"))));
        return converter;
    }

    private byte[] decodificarClave(String secreto) {
        byte[] clave;
        try {
            clave = Base64.getDecoder().decode(secreto.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SECRET debe estar codificado en Base64.");
        }
        if (clave.length < 32) {
            throw new IllegalStateException("Configura JWT_SECRET con una clave de al menos 32 bytes codificada en Base64.");
        }
        return clave;
    }

    private OAuth2TokenValidatorResult validarIdentidad(Jwt jwt) {
        String correo = jwt.getSubject();
        if (correo == null || correo.length() > 254 || !CORREO.matcher(correo).matches()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "El JWT requiere un sub con correo válido.", null));
        }
        if (!(jwt.getClaims().get("rol") instanceof String rol) || !ROLES.contains(rol)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "El JWT requiere un rol válido.", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
