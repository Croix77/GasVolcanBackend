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
        registrarSiFalta("gas-5kg", 1);
        registrarSiFalta("gas-15kg", 4);
    }

    private void registrarSiFalta(String id, int stock) {
        if (!existencias.existsById(id)) {
            existencias.save(new Existencia(id, stock));
        }
    }
}
