package cl.duoc.gasvolcan.productos;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
class ProductoService {

    private final ProductoRepository productos;
    private final CategoriasClient categorias;

    ProductoService(ProductoRepository productos, CategoriasClient categorias) {
        this.productos = productos;
        this.categorias = categorias;
    }

    List<ProductoRespuesta> listar() {
        List<Producto> catalogo = productos.findAllByActivoTrueOrderByNombreAscIdAsc();
        if (catalogo.isEmpty()) {
            return List.of();
        }

        var nombres = categorias.nombres();
        return catalogo.stream().map(producto -> producto.vista(nombres.get(producto.getCategoriaId()))).toList();
    }

    ProductoRespuesta buscar(String id) {
        Producto producto = buscarActivo(id);
        return producto.vista(categorias.nombres().get(producto.getCategoriaId()));
    }

    Producto.PrecioVenta precioVenta(String id) {
        return buscarActivo(id).precioVenta();
    }

    @Transactional
    ProductoRespuesta crear(ProductoSolicitud solicitud) {
        return registrar(solicitud.codigo().toLowerCase(Locale.ROOT), solicitud);
    }

    @Transactional
    void crearInicial(String id, ProductoSolicitud solicitud) {
        if (falta(id, solicitud.codigo())) {
            registrar(id, solicitud);
        }
    }

    boolean falta(String id, String codigo) {
        return !productos.existsById(id) && !productos.existsByCodigoIgnoreCase(codigo);
    }

    @Transactional
    ProductoRespuesta actualizar(String id, ProductoSolicitud solicitud) {
        Producto producto = bloquearActivo(id);
        if (solicitud.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consulta la versión del producto antes de editarlo.");
        }
        if (!solicitud.version().equals(producto.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El producto cambió desde que abriste la edición. Consulta los datos nuevos y vuelve a intentarlo.");
        }

        String codigo = solicitud.codigo().toUpperCase(Locale.ROOT);
        if (productos.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
            throw codigoDuplicado(codigo);
        }
        validarOferta(solicitud.precio(), Boolean.TRUE.equals(solicitud.oferta()) ? solicitud.precioAnterior() : null);
        String categoria = producto.getCategoriaId() == solicitud.categoriaId()
                ? categorias.nombres().get(producto.getCategoriaId())
                : reservarCategoria(producto, solicitud.categoriaId());

        producto.actualizar(solicitud, codigo);
        productos.flush();
        return producto.vista(categoria);
    }

    @Transactional
    Producto.PrecioVenta cambiarPrecio(String id, int precio) {
        Producto producto = bloquearActivo(id);
        validarOferta(precio, producto.getPrecioAnterior());
        producto.cambiarPrecio(precio);
        return producto.precioVenta();
    }

    @Transactional
    void eliminar(String id) {
        // Se conserva la ficha histórica y su categoría, aunque salga del catálogo.
        bloquearActivo(id).desactivar();
    }

    private ProductoRespuesta registrar(String id, ProductoSolicitud solicitud) {
        String codigo = solicitud.codigo().toUpperCase(Locale.ROOT);
        if (productos.existsById(id) || productos.existsByCodigoIgnoreCase(codigo)) {
            throw codigoDuplicado(codigo);
        }
        validarOferta(solicitud.precio(), Boolean.TRUE.equals(solicitud.oferta()) ? solicitud.precioAnterior() : null);

        Producto producto = new Producto(id);
        String categoria = reservarCategoria(producto, solicitud.categoriaId());
        producto.actualizar(solicitud, codigo);
        return productos.saveAndFlush(producto).vista(categoria);
    }

    private String reservarCategoria(Producto producto, long categoriaId) {
        String anterior = producto.getReferenciaCategoria();
        String nueva = UUID.randomUUID().toString();
        // Cada intento tiene su propia referencia. Una reversión libera solo la suya.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                if (estado == STATUS_COMMITTED && anterior != null) {
                    categorias.liberar(anterior);
                } else if (estado == STATUS_ROLLED_BACK) {
                    categorias.liberar(nueva);
                }
            }
        });
        var categoria = categorias.asociar(categoriaId, producto.getId(), nueva);
        producto.cambiarCategoria(categoriaId, nueva);
        return categoria.nombre();
    }

    private void validarOferta(int precio, Integer anterior) {
        if (anterior != null && anterior <= precio) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El precio anterior debe ser mayor al precio en oferta.");
        }
    }

    private Producto buscarActivo(String id) {
        return productos.findByIdAndActivoTrue(id).orElseThrow(() -> productoNoEncontrado(id));
    }

    private Producto bloquearActivo(String id) {
        return productos.bloquear(id).filter(Producto::isActivo).orElseThrow(() -> productoNoEncontrado(id));
    }

    private ResponseStatusException productoNoEncontrado(String id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el producto con id " + id + ".");
    }

    private ResponseStatusException codigoDuplicado(String codigo) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un producto con el código " + codigo + ".");
    }
}
