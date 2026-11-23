package cl.duoc.gasvolcan.categorias;

import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CategoriasApplication {

    public static void main(String[] args) {
        SpringApplication.run(CategoriasApplication.class, args);
    }

    @Bean
    ApplicationRunner datosDemostracion(CategoriaRepository categorias) {
        return args -> {
            if (categorias.count() == 0) {
                categorias.saveAll(List.of(new Categoria(1, "Cilindros de Gas"), new Categoria(2, "Accesorios")));
            }
        };
    }
}
