package cl.duoc.gasvolcan.pedidos.service;

import cl.duoc.gasvolcan.pedidos.dto.ZonaDespachoRespuesta;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ZonaDespachoService {

    // Información de la hoja Zonas de Despacho del catálogo del caso.
    private static final List<ZonaDespachoRespuesta> ZONAS = List.of(
        new ZonaDespachoRespuesta("Zona Centro", "Chillán (sectores centro, norte y sur)",
            "Lunes a Sábado", "08:00 – 20:00", "1 – 3 horas"),
        new ZonaDespachoRespuesta("Zona Oriente", "Chillán (sector oriente), Chillán Viejo",
            "Lunes a Viernes", "08:00 – 18:00", "2 – 4 horas"),
        new ZonaDespachoRespuesta("Zona Rural", "El Carmen, Pinto, San Ignacio",
            "Martes y Jueves", "08:00 – 16:00", "3 – 6 horas"),
        new ZonaDespachoRespuesta("Zona Sur", "Bulnes, Quillón",
            "Miércoles", "08:00 – 16:00", "4 – 6 horas"),
        new ZonaDespachoRespuesta("Zona Comercial", "Parques industriales y locales comerciales",
            "Lunes a Viernes", "07:00 – 17:00", "Según agenda"));

    private static final Set<String> COMUNAS = Set.of(
        "Chillán", "Chillán Viejo", "El Carmen", "Pinto", "San Ignacio", "Bulnes", "Quillón");

    public List<ZonaDespachoRespuesta> listar() {
        return ZONAS;
    }

    public boolean cubre(String comuna) {
        return COMUNAS.contains(comuna);
    }
}
