package cl.duoc.gasvolcan.negocio.service;

import cl.duoc.gasvolcan.negocio.dto.ProductoRespuesta;
import cl.duoc.gasvolcan.negocio.dto.ProductoSolicitud;
import cl.duoc.gasvolcan.negocio.exception.ConflictoNegocioException;
import cl.duoc.gasvolcan.negocio.exception.NegocioInvalidoException;
import cl.duoc.gasvolcan.negocio.exception.RecursoNoEncontradoException;
import cl.duoc.gasvolcan.negocio.model.Producto;
import cl.duoc.gasvolcan.negocio.repository.ProductoRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductoService {

    private static final int STOCK_CRITICO_INICIAL = 10;

    private final ProductoRepository repository;
    private final CategoriaService categorias;

    public ProductoService(ProductoRepository repository, CategoriaService categorias) {
        this.repository = repository;
        this.categorias = categorias;
    }

    @Transactional(readOnly = true)
    public List<ProductoRespuesta> listar() {
        return repository.findByActivoTrueOrderByNombreAscIdAsc().stream()
                .map(ProductoRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductoRespuesta buscar(String id) {
        return repository.findByIdAndActivoTrue(id)
                .map(ProductoRespuesta::desde)
                .orElseThrow(() -> productoNoEncontrado(id));
    }

    @Transactional
    public ProductoRespuesta crear(ProductoSolicitud solicitud) {
        String codigo = solicitud.codigo().toUpperCase(Locale.ROOT);
        String id = codigo.toLowerCase(Locale.ROOT);
        if (repository.existsByCodigoIgnoreCase(codigo) || repository.existsById(id)) {
            throw codigoDuplicado(codigo);
        }

        Producto producto = new Producto();
        producto.setId(id);
        aplicarDatos(producto, solicitud, codigo);
        return ProductoRespuesta.desde(repository.save(producto));
    }

    @Transactional
    public ProductoRespuesta actualizar(String id, ProductoSolicitud solicitud) {
        Producto producto = buscarActivoParaActualizar(id);
        // Una compra puede cambiar el stock mientras el administrador tiene el formulario abierto.
        if (solicitud.version() == null) {
            throw new NegocioInvalidoException("Consulta la versión del producto antes de editarlo.");
        }
        if (!solicitud.version().equals(producto.getVersion())) {
            throw new ConflictoNegocioException("El producto cambió desde que abriste la edición. Consulta los datos nuevos y vuelve a intentarlo.");
        }
        String codigo = solicitud.codigo().toUpperCase(Locale.ROOT);
        if (repository.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
            throw codigoDuplicado(codigo);
        }

        aplicarDatos(producto, solicitud, codigo);
        repository.flush();
        return ProductoRespuesta.desde(producto);
    }

    @Transactional
    public void eliminar(String id) {
        // Los pedidos históricos conservan su referencia al producto desactivado.
        buscarActivoParaActualizar(id).setActivo(false);
    }

    private Producto buscarActivoParaActualizar(String id) {
        return repository.buscarParaActualizar(id)
                .filter(Producto::isActivo)
                .orElseThrow(() -> productoNoEncontrado(id));
    }

    private void aplicarDatos(Producto producto, ProductoSolicitud solicitud, String codigo) {
        Integer precioAnterior = solicitud.precioAnterior();
        boolean oferta = Boolean.TRUE.equals(solicitud.oferta());
        if (oferta && precioAnterior != null && precioAnterior <= solicitud.precio()) {
            throw new NegocioInvalidoException("El precio anterior debe ser mayor al precio en oferta.");
        }

        producto.setCodigo(codigo);
        producto.setNombre(solicitud.nombre().strip());
        producto.setDescripcion(solicitud.descripcion().strip());
        producto.setDescripcionLarga(solicitud.descripcionLarga() == null ? null : solicitud.descripcionLarga().strip());
        producto.setUnidad(solicitud.unidad().strip());
        producto.setCategoria(categorias.buscar(solicitud.categoriaId()));
        producto.setPrecio(solicitud.precio());
        producto.setPrecioComercial(solicitud.precioComercial());
        producto.setPrecioAnterior(oferta ? precioAnterior : null);
        producto.setStock(solicitud.stock());
        producto.setStockCritico(solicitud.stockCritico() == null ? STOCK_CRITICO_INICIAL : solicitud.stockCritico());
        producto.setImagen(solicitud.imagen() == null ? "" : solicitud.imagen().strip());
        producto.setOferta(oferta);
    }

    private RecursoNoEncontradoException productoNoEncontrado(String id) {
        return new RecursoNoEncontradoException("No se encontró el producto con id " + id + ".");
    }

    private ConflictoNegocioException codigoDuplicado(String codigo) {
        return new ConflictoNegocioException("Ya existe un producto con el código " + codigo + ".");
    }
}
