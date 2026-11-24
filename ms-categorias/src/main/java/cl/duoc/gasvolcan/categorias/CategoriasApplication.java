package cl.duoc.gasvolcan.categorias;

import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CategoriasApplication {

    public static void main(String[] args) {
        SpringApplication.run(CategoriasApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "app.seed.catalogo", havingValue = "true")
    ApplicationRunner categoriasIniciales(CategoriaRepository categorias) {
        return args -> {
            if (categorias.count() != 0) {
                return;
            }
            for (String nombre : List.of("Cilindros de Gas", "Reguladores", "Mangueras y Conexiones", "Accesorios")) {
                if (!categorias.existsByNombreIgnoreCase(nombre)) {
                    categorias.save(new Categoria(nombre));
                }
            }
        };
    }
}
