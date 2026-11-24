package cl.duoc.gasvolcan.pedidos.config;

import cl.duoc.gasvolcan.pedidos.model.Existencia;
import cl.duoc.gasvolcan.pedidos.repository.ExistenciaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.seed.stock", havingValue = "true")
public class DatosStock implements ApplicationRunner {

    private final ExistenciaRepository existencias;

    public DatosStock(ExistenciaRepository existencias) {
        this.existencias = existencias;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        // Existencias iniciales del catálogo Forma C. Las actualizaciones quedan en esta base.
        registrarSiFalta("gas-5kg", 80);
        registrarSiFalta("gas-11kg", 200);
        registrarSiFalta("gas-15kg", 90);
        registrarSiFalta("cl004", 30);
        registrarSiFalta("rg001", 45);
        registrarSiFalta("rg002", 12);
        registrarSiFalta("rg003", 18);
        registrarSiFalta("mg001", 80);
        registrarSiFalta("mg002", 50);
        registrarSiFalta("mg003", 200);
        registrarSiFalta("mg004", 25);
        registrarSiFalta("ac001", 20);
        registrarSiFalta("ac002", 60);
        registrarSiFalta("ac003", 8);
    }

    private void registrarSiFalta(String id, int stock) {
        if (!existencias.existsById(id)) {
            existencias.save(new Existencia(id, stock));
        }
    }
}
