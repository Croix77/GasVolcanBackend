package cl.duoc.gasvolcan.productos;

import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ProductosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductosApplication.class, args);
    }

    @Bean
    ApplicationRunner datosDemostracion(ProductoRepository productos) {
        return args -> {
            if (productos.count() == 0) {
                productos.saveAll(List.of(
                        new Producto("gas-5kg", "Cilindro de gas de 5 kg", 5900, 1),
                        new Producto("gas-15kg", "Cilindro de gas de 15 kg", 16000, 1)));
            }
        };
    }
}
