package cl.duoc.gasvolcan.productos.config;

import cl.duoc.gasvolcan.productos.client.CategoriasClient;
import cl.duoc.gasvolcan.productos.dto.ProductoSolicitud;
import cl.duoc.gasvolcan.productos.service.ProductoService;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.seed.catalogo", havingValue = "true")
public class DatosCatalogo implements ApplicationRunner {

    private static final String CILINDROS = "Cilindros de Gas";
    private static final String REGULADORES = "Reguladores";
    private static final String MANGUERAS = "Mangueras y Conexiones";
    private static final String ACCESORIOS = "Accesorios";

    // Las fichas del catálogo de Forma C conservan imágenes, ofertas y umbrales de la demostración React.
    private static final List<Ficha> FICHAS = List.of(
            new Ficha("gas-5kg", "CL001", CILINDROS, "Cilindro de Gas 5 Kg",
                    "Cilindro de gas licuado de petróleo 5 kg. Para uso residencial (cocina, calefacción pequeña).",
                    "Cilindro de gas licuado de petróleo de 5 kg, pensado para hogares de bajo consumo. Perfecto para cocina, calefacción pequeña o artefactos de uso ocasional. Fabricado bajo normativa vigente y con válvula de seguridad.",
                    "Unidad", 5900, 6000, 6500, 10, "/img/gas-5kg.png"),
            new Ficha("gas-11kg", "CL002", CILINDROS, "Cilindro de Gas 11 Kg",
                    "Cilindro estándar doméstico. El más utilizado en hogares chilenos. Compatible con reguladores estándar.",
                    "El cilindro más utilizado en los hogares chilenos. Con 11 kg de capacidad es ideal para el consumo diario de una familia promedio, incluyendo cocina y calefacción. Compatible con reguladores estándar.",
                    "Unidad", 12000, 11000, null, 20, "/img/gas-11kg.png"),
            new Ficha("gas-15kg", "CL003", CILINDROS, "Cilindro de Gas 15 Kg",
                    "Cilindro de mayor capacidad para hogares de alto consumo o locales pequeños.",
                    "Cilindro de mayor capacidad, recomendado para hogares con alto consumo o locales pequeños. Con 15 kg de gas licuado se reduce la frecuencia de recargas y es ideal para calefacción central o varios artefactos simultáneos.",
                    "Unidad", 16000, 14500, null, 15, "/img/gas-15kg.png"),
            new Ficha("cl004", "CL004", CILINDROS, "Cilindro GLP 45 kg",
                    "Cilindro industrial. Uso comercial: restaurantes, talleres, calefacción de locales.",
                    "Cilindro industrial de 45 kg. Uso comercial: restaurantes, talleres y calefacción de locales. Pensado para clientes comerciales con alto consumo de gas licuado.",
                    "Unidad", 45000, 40000, null, 5, ""),
            new Ficha("rg001", "RG001", REGULADORES, "Regulador doméstico estándar",
                    "Regulador de 1 etapa para cilindros 5, 11 y 15 kg. Presión de salida 28 mbar.",
                    "Regulador de 1 etapa para cilindros de 5, 11 y 15 kg. Presión de salida de 28 mbar. Compatible con los reguladores y mangueras estándar del mercado.",
                    "Unidad", 7990, 8200, 8990, 8, "/img/regulador-domestico.png"),
            new Ficha("rg002", "RG002", REGULADORES, "Regulador de alta presión",
                    "Regulador para cocinas industriales o equipos de mayor consumo. Presión regulable.",
                    "Regulador de alta presión para cocinas industriales o equipos de mayor consumo. Presión regulable según la necesidad del artefacto conectado.",
                    "Unidad", 18990, 17000, null, 3, ""),
            new Ficha("rg003", "RG003", REGULADORES, "Regulador dual (2 salidas)",
                    "Permite conectar dos artefactos simultáneamente al mismo cilindro.",
                    "Regulador dual con dos salidas. Permite conectar dos artefactos simultáneamente al mismo cilindro, ideal para hogares con varios equipos a gas.",
                    "Unidad", 14990, 13500, null, 4, ""),
            new Ficha("mg001", "MG001", MANGUERAS, "Manguera gas 1.5 m",
                    "Manguera flexible homologada. Diámetro interior 9mm. Compatible con reguladores estándar.",
                    "Manguera flexible homologada de 1.5 metros. Diámetro interior de 9 mm. Compatible con reguladores estándar, ideal para distancias cortas entre el cilindro y el artefacto.",
                    "Unidad", 3990, 3500, null, 15, ""),
            new Ficha("mg002", "MG002", MANGUERAS, "Manguera gas 3 m",
                    "Manguera larga para instalaciones donde el artefacto está alejado del cilindro.",
                    "Manguera larga de 3 metros para instalaciones donde el artefacto está alejado del cilindro. Flexible y homologada con las normas de seguridad vigentes.",
                    "Unidad", 6990, 6200, null, 10, ""),
            new Ficha("mg003", "MG003", MANGUERAS, "Abrazadera metálica",
                    "Abrazadera de acero para asegurar la conexión manguera-regulador y manguera-artefacto.",
                    "Abrazadera de acero para asegurar la conexión manguera-regulador y manguera-artefacto. Elemento de seguridad esencial en toda instalación de gas.",
                    "Unidad", 990, 800, null, 30, ""),
            new Ficha("mg004", "MG004", MANGUERAS, "Kit conexión completo (regulador + manguera 1.5m + abrazaderas)",
                    "Todo lo necesario para instalar un cilindro nuevo.",
                    "Kit con todo lo necesario para instalar un cilindro nuevo: regulador, manguera de 1.5 metros y abrazaderas. Ahorra tiempo y asegura una instalación correcta.",
                    "Kit", 12990, 11500, null, 5, ""),
            new Ficha("ac001", "AC001", ACCESORIOS, "Carro porta cilindro 11/15 kg",
                    "Carro metálico con ruedas para transportar cilindros dentro del hogar con seguridad.",
                    "Carro metálico con ruedas para transportar cilindros de 11 y 15 kg dentro del hogar con seguridad y sin esfuerzo.",
                    "Unidad", 12990, 11000, null, 4, ""),
            new Ficha("ac002", "AC002", ACCESORIOS, "Tapa protectora para válvula",
                    "Tapa de plástico ABS para proteger la válvula del cilindro durante el transporte.",
                    "Tapa de plástico ABS para proteger la válvula del cilindro durante el transporte o almacenamiento. Evita daños y mantiene la válvula limpia.",
                    "Unidad", 1490, 1200, null, 12, ""),
            new Ficha("ac003", "AC003", ACCESORIOS, "Detector de gas a batería",
                    "Sensor electroquímico. Alarma sonora y visual ante fuga de gas GLP o metano.",
                    "Detector de gas a batería con sensor electroquímico. Alarma sonora y visual ante fuga de gas GLP o metano. Elemento de seguridad recomendado para todos los hogares.",
                    "Unidad", 17990, 17000, 19990, 2, "/img/detector-gas-bateria.png"));

private final CategoriasClient categorias;
    private final ProductoService productos;

    public DatosCatalogo(CategoriasClient categorias, ProductoService productos) {
        this.categorias = categorias;
        this.productos = productos;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        var pendientes = FICHAS.stream().filter(ficha -> productos.falta(ficha.id(), ficha.codigo())).toList();
        if (pendientes.isEmpty()) {
            return;
        }

        var identificadores = Arrays.stream(categorias.listar()).collect(Collectors.toMap(
                categoria -> categoria.nombre().toLowerCase(Locale.ROOT), CategoriasClient.CategoriaVista::id
        ));
        for (Ficha ficha : pendientes) {
            Long categoria = identificadores.get(ficha.categoria().toLowerCase(Locale.ROOT));
            if (categoria == null) {
                throw new IllegalStateException("Falta la categoría de catálogo: " + ficha.categoria()
                        + ". Inicia ms-categorias con APP_SEED_CATALOGO=true.");
            }
            var datos = new ProductoSolicitud(
                    ficha.codigo(), ficha.nombre(), ficha.descripcion(), ficha.descripcionLarga(),
                    ficha.unidad(), categoria, ficha.precio(), ficha.precioComercial(), ficha.precioAnterior(),
                    ficha.stockCritico(), ficha.imagen(), ficha.precioAnterior() != null, null
            );
            productos.crearInicial(ficha.id(), datos);
        }
    }

    private record Ficha(
            String id, String codigo, String categoria, String nombre, String descripcion,
            String descripcionLarga, String unidad, int precio, int precioComercial,
            Integer precioAnterior, int stockCritico, String imagen) {
    }
}
