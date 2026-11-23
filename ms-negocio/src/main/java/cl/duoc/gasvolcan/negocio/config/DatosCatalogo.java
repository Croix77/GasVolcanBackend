package cl.duoc.gasvolcan.negocio.config;

import cl.duoc.gasvolcan.negocio.model.Categoria;
import cl.duoc.gasvolcan.negocio.model.Producto;
import cl.duoc.gasvolcan.negocio.repository.CategoriaRepository;
import cl.duoc.gasvolcan.negocio.repository.ProductoRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
                    "Unidad", 5900, 6000, 6500, 80, 10, "/img/gas-5kg.png"),
            new Ficha("gas-11kg", "CL002", CILINDROS, "Cilindro de Gas 11 Kg",
                    "Cilindro estándar doméstico. El más utilizado en hogares chilenos. Compatible con reguladores estándar.",
                    "El cilindro más utilizado en los hogares chilenos. Con 11 kg de capacidad es ideal para el consumo diario de una familia promedio, incluyendo cocina y calefacción. Compatible con reguladores estándar.",
                    "Unidad", 12000, 11000, null, 200, 20, "/img/gas-11kg.png"),
            new Ficha("gas-15kg", "CL003", CILINDROS, "Cilindro de Gas 15 Kg",
                    "Cilindro de mayor capacidad para hogares de alto consumo o locales pequeños.",
                    "Cilindro de mayor capacidad, recomendado para hogares con alto consumo o locales pequeños. Con 15 kg de gas licuado se reduce la frecuencia de recargas y es ideal para calefacción central o varios artefactos simultáneos.",
                    "Unidad", 16000, 14500, null, 90, 15, "/img/gas-15kg.png"),
            new Ficha("cl004", "CL004", CILINDROS, "Cilindro GLP 45 kg",
                    "Cilindro industrial. Uso comercial: restaurantes, talleres, calefacción de locales.",
                    "Cilindro industrial de 45 kg. Uso comercial: restaurantes, talleres y calefacción de locales. Pensado para clientes comerciales con alto consumo de gas licuado.",
                    "Unidad", 45000, 40000, null, 30, 5, ""),
            new Ficha("rg001", "RG001", REGULADORES, "Regulador doméstico estándar",
                    "Regulador de 1 etapa para cilindros 5, 11 y 15 kg. Presión de salida 28 mbar.",
                    "Regulador de 1 etapa para cilindros de 5, 11 y 15 kg. Presión de salida de 28 mbar. Compatible con los reguladores y mangueras estándar del mercado.",
                    "Unidad", 7990, 8200, 8990, 45, 8, "/img/regulador-domestico.png"),
            new Ficha("rg002", "RG002", REGULADORES, "Regulador de alta presión",
                    "Regulador para cocinas industriales o equipos de mayor consumo. Presión regulable.",
                    "Regulador de alta presión para cocinas industriales o equipos de mayor consumo. Presión regulable según la necesidad del artefacto conectado.",
                    "Unidad", 18990, 17000, null, 12, 3, ""),
            new Ficha("rg003", "RG003", REGULADORES, "Regulador dual (2 salidas)",
                    "Permite conectar dos artefactos simultáneamente al mismo cilindro.",
                    "Regulador dual con dos salidas. Permite conectar dos artefactos simultáneamente al mismo cilindro, ideal para hogares con varios equipos a gas.",
                    "Unidad", 14990, 13500, null, 18, 4, ""),
            new Ficha("mg001", "MG001", MANGUERAS, "Manguera gas 1.5 m",
                    "Manguera flexible homologada. Diámetro interior 9mm. Compatible con reguladores estándar.",
                    "Manguera flexible homologada de 1.5 metros. Diámetro interior de 9 mm. Compatible con reguladores estándar, ideal para distancias cortas entre el cilindro y el artefacto.",
                    "Unidad", 3990, 3500, null, 80, 15, ""),
            new Ficha("mg002", "MG002", MANGUERAS, "Manguera gas 3 m",
                    "Manguera larga para instalaciones donde el artefacto está alejado del cilindro.",
                    "Manguera larga de 3 metros para instalaciones donde el artefacto está alejado del cilindro. Flexible y homologada con las normas de seguridad vigentes.",
                    "Unidad", 6990, 6200, null, 50, 10, ""),
            new Ficha("mg003", "MG003", MANGUERAS, "Abrazadera metálica",
                    "Abrazadera de acero para asegurar la conexión manguera-regulador y manguera-artefacto.",
                    "Abrazadera de acero para asegurar la conexión manguera-regulador y manguera-artefacto. Elemento de seguridad esencial en toda instalación de gas.",
                    "Unidad", 990, 800, null, 200, 30, ""),
            new Ficha("mg004", "MG004", MANGUERAS, "Kit conexión completo (regulador + manguera 1.5m + abrazaderas)",
                    "Todo lo necesario para instalar un cilindro nuevo.",
                    "Kit con todo lo necesario para instalar un cilindro nuevo: regulador, manguera de 1.5 metros y abrazaderas. Ahorra tiempo y asegura una instalación correcta.",
                    "Kit", 12990, 11500, null, 25, 5, ""),
            new Ficha("ac001", "AC001", ACCESORIOS, "Carro porta cilindro 11/15 kg",
                    "Carro metálico con ruedas para transportar cilindros dentro del hogar con seguridad.",
                    "Carro metálico con ruedas para transportar cilindros de 11 y 15 kg dentro del hogar con seguridad y sin esfuerzo.",
                    "Unidad", 12990, 11000, null, 20, 4, ""),
            new Ficha("ac002", "AC002", ACCESORIOS, "Tapa protectora para válvula",
                    "Tapa de plástico ABS para proteger la válvula del cilindro durante el transporte.",
                    "Tapa de plástico ABS para proteger la válvula del cilindro durante el transporte o almacenamiento. Evita daños y mantiene la válvula limpia.",
                    "Unidad", 1490, 1200, null, 60, 12, ""),
            new Ficha("ac003", "AC003", ACCESORIOS, "Detector de gas a batería",
                    "Sensor electroquímico. Alarma sonora y visual ante fuga de gas GLP o metano.",
                    "Detector de gas a batería con sensor electroquímico. Alarma sonora y visual ante fuga de gas GLP o metano. Elemento de seguridad recomendado para todos los hogares.",
                    "Unidad", 17990, 17000, 19990, 8, 2, "/img/detector-gas-bateria.png"));

    private final CategoriaRepository categorias;
    private final ProductoRepository productos;

    public DatosCatalogo(CategoriaRepository categorias, ProductoRepository productos) {
        this.categorias = categorias;
        this.productos = productos;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argumentos) {
        for (Ficha ficha : FICHAS) {
            // Un arranque posterior conserva los cambios y los productos desactivados.
            if (productos.existsById(ficha.id()) || productos.existsByCodigoIgnoreCase(ficha.codigo())) {
                continue;
            }
            Categoria categoria = categorias.findByNombreIgnoreCase(ficha.categoria())
                    .orElseGet(() -> crearCategoria(ficha.categoria()));
            productos.save(crearProducto(ficha, categoria));
        }
    }

    private Categoria crearCategoria(String nombre) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        return categorias.save(categoria);
    }

    private Producto crearProducto(Ficha ficha, Categoria categoria) {
        Producto producto = new Producto();
        producto.setId(ficha.id());
        producto.setCodigo(ficha.codigo());
        producto.setNombre(ficha.nombre());
        producto.setDescripcion(ficha.descripcion());
        producto.setDescripcionLarga(ficha.descripcionLarga());
        producto.setUnidad(ficha.unidad());
        producto.setCategoria(categoria);
        producto.setPrecio(ficha.precio());
        producto.setPrecioComercial(ficha.precioComercial());
        producto.setPrecioAnterior(ficha.precioAnterior());
        producto.setStock(ficha.stock());
        producto.setStockCritico(ficha.stockCritico());
        producto.setImagen(ficha.imagen());
        producto.setOferta(ficha.precioAnterior() != null);
        return producto;
    }

    private record Ficha(
            String id, String codigo, String categoria, String nombre, String descripcion,
            String descripcionLarga, String unidad, int precio, int precioComercial,
            Integer precioAnterior, int stock, int stockCritico, String imagen) {
    }
}
