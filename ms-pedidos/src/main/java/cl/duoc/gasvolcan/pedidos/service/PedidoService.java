package cl.duoc.gasvolcan.pedidos.service;

import cl.duoc.gasvolcan.pedidos.dto.PedidoRespuesta;
import cl.duoc.gasvolcan.pedidos.dto.PedidoSolicitud;
import cl.duoc.gasvolcan.pedidos.exception.ConflictoNegocioException;
import cl.duoc.gasvolcan.pedidos.exception.NegocioInvalidoException;
import cl.duoc.gasvolcan.pedidos.exception.RecursoNoEncontradoException;
import cl.duoc.gasvolcan.pedidos.model.EstadoPedido;
import cl.duoc.gasvolcan.pedidos.model.Pedido;
import cl.duoc.gasvolcan.pedidos.model.Existencia;
import cl.duoc.gasvolcan.pedidos.repository.PedidoRepository;
import cl.duoc.gasvolcan.pedidos.repository.ExistenciaRepository;
import cl.duoc.gasvolcan.pedidos.service.CatalogoProductos.ProductoCatalogo;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {


    private static final ZoneId HUSO_CHILE = ZoneId.of("America/Santiago");
    private static final Pattern LETRA = Pattern.compile("\\p{L}");
    private static final Pattern LETRA_O_NUMERO = Pattern.compile("[\\p{L}\\p{N}]");
    private static final Pattern TELEFONO_CHILENO = Pattern.compile("(?:\\+?56)?[2-9]\\d{8}");

    private final PedidoRepository pedidos;
    private final ExistenciaRepository existencias;
    private final ZonaDespachoService zonas;

    public PedidoService(PedidoRepository pedidos, ExistenciaRepository existencias, ZonaDespachoService zonas) {
        this.pedidos = pedidos;
        this.existencias = existencias;
        this.zonas = zonas;
    }

    @Transactional
    public Compra crear(PedidoSolicitud solicitud, Authentication usuario, Map<String, Integer> cantidades,
                        Map<String, ProductoCatalogo> productos) {
        String cliente = correo(usuario);
        String referencia = solicitud.referenciaCompra().toString();
        var anterior = pedidos.findByCorreoClienteAndReferenciaCompra(cliente, referencia);
        if (anterior.isPresent()) return new Compra(PedidoRespuesta.desde(anterior.get()), false);
        String telefono = validarEntrega(solicitud);
        Pedido pedido = new Pedido(cliente, solicitud.nombre().strip(), telefono,
            solicitud.direccion().strip(), solicitud.comuna().strip(),
            solicitud.latitudEntrega(), solicitud.longitudEntrega(), referencia);

        // Reservar la referencia primero evita descontar stock dos veces ante un reintento concurrente.
        // La reserva y el descuento se revierten juntos si falla algún producto.
        pedidos.saveAndFlush(pedido);

        // El orden fijo de los bloqueos evita que dos compras bloqueen los mismos productos al revés.
        for (Map.Entry<String, Integer> item : cantidades.entrySet()) {
            String id = item.getKey();
            int cantidad = item.getValue();
            ProductoCatalogo producto = productos.get(id);
            Existencia existencia = existencias.buscarParaActualizar(id)
                    .orElseThrow(() -> new ConflictoNegocioException("Stock insuficiente para " + producto.nombre() + "."));
            existencia.descontar(cantidad, producto.nombre());
            pedido.agregarItem(id, producto.nombre(), producto.precio(), cantidad);
        }

        // Si falla cualquier artículo se revierten también las reservas de stock previas.
        return new Compra(PedidoRespuesta.desde(pedido), true);
    }

    @Transactional(readOnly = true)
    public Optional<PedidoRespuesta> buscarCompra(UUID referencia, Authentication usuario) {
        if (referencia == null) return Optional.empty();
        return pedidos.findByCorreoClienteAndReferenciaCompra(correo(usuario), referencia.toString())
            .map(PedidoRespuesta::desde);
    }

    @Transactional(readOnly = true)
    public List<PedidoRespuesta> listar(LocalDate fecha, Authentication usuario) {
        Specification<Pedido> alcance = alcanceDe(usuario);
        if (fecha != null) {
            Instant inicio = fecha.atStartOfDay(HUSO_CHILE).toInstant();
            Instant fin = fecha.plusDays(1).atStartOfDay(HUSO_CHILE).toInstant();
            alcance = alcance.and((pedido, consulta, criterios) -> criterios.and(
                criterios.greaterThanOrEqualTo(pedido.get("fechaCreacion"), inicio),
                criterios.lessThan(pedido.get("fechaCreacion"), fin)));
        }
        boolean incluirDatosEntrega = !tieneRol(usuario, "VENDEDOR");
        return pedidos.findAll(alcance, Sort.by(Sort.Direction.DESC, "fechaCreacion", "id")).stream()
            .map(pedido -> PedidoRespuesta.desde(pedido, incluirDatosEntrega))
            .toList();
    }

    @Transactional(readOnly = true)
    public PedidoRespuesta buscar(Long id, Authentication usuario) {
        Specification<Pedido> porId = (pedido, consulta, criterios) -> criterios.equal(pedido.get("id"), id);
        return pedidos.findOne(porId.and(alcanceDe(usuario)))
            .map(pedido -> PedidoRespuesta.desde(pedido, !tieneRol(usuario, "VENDEDOR")))
            .orElseThrow(() -> pedidoNoEncontrado(id));
    }

    @Transactional
    public PedidoRespuesta asignar(Long id, String correoRepartidor) {
        Pedido pedido = buscarParaActualizar(id);
        if (pedido.getEstado() != EstadoPedido.PENDIENTE && pedido.getEstado() != EstadoPedido.ASIGNADO) {
            throw new ConflictoNegocioException("Solo se pueden asignar pedidos pendientes o asignados.");
        }

        pedido.asignar(correoRepartidor.strip().toLowerCase(Locale.ROOT));
        return PedidoRespuesta.desde(pedido);
    }

    @Transactional
    public PedidoRespuesta cambiarEstado(Long id, EstadoPedido estado, Authentication usuario) {
        Pedido pedido = buscarParaActualizar(id);
        verificarRepartidor(pedido, usuario);
        EstadoPedido actual = pedido.getEstado();

        if (estado == EstadoPedido.EN_CAMINO && actual == EstadoPedido.ASIGNADO) {
            pedido.iniciarRecorrido();
        } else if (estado == EstadoPedido.ENTREGADO && actual == EstadoPedido.EN_CAMINO) {
            pedido.registrarEntrega();
        } else if (estado != actual || (estado != EstadoPedido.EN_CAMINO && estado != EstadoPedido.ENTREGADO)) {
            throw new ConflictoNegocioException("El cambio de estado solicitado no está permitido.");
        }

        return PedidoRespuesta.desde(pedido);
    }

    @Transactional
    public PedidoRespuesta ubicar(Long id, double latitud, double longitud, Authentication usuario) {
        Pedido pedido = buscarParaActualizar(id);
        verificarRepartidor(pedido, usuario);
        if (pedido.getEstado() != EstadoPedido.EN_CAMINO) {
            throw new ConflictoNegocioException("Puedes informar la ubicación durante el recorrido.");
        }
        pedido.actualizarUbicacion(latitud, longitud);
        return PedidoRespuesta.desde(pedido);
    }

    private String validarEntrega(PedidoSolicitud solicitud) {
        String nombre = solicitud.nombre().strip();
        if (nombre.length() < 2 || !LETRA.matcher(nombre).find()) {
            throw new NegocioInvalidoException("Ingresa un nombre válido para la entrega.");
        }
        String telefono = solicitud.telefono().replaceAll("[\\s()-]", "");
        if (!TELEFONO_CHILENO.matcher(telefono).matches()) {
            throw new NegocioInvalidoException("Ingresa un teléfono chileno válido.");
        }
        String direccion = solicitud.direccion().strip();
        if (direccion.length() < 5 || !LETRA_O_NUMERO.matcher(direccion).find()) {
            throw new NegocioInvalidoException("Ingresa una dirección de entrega válida.");
        }
        if (!zonas.cubre(solicitud.comuna().strip())) {
            throw new NegocioInvalidoException("Selecciona una comuna dentro de la cobertura.");
        }
        return telefono;
    }

    private Specification<Pedido> alcanceDe(Authentication usuario) {
        if (tieneRol(usuario, "ADMIN") || tieneRol(usuario, "OPERADORA") || tieneRol(usuario, "VENDEDOR")) {
            return (pedido, consulta, criterios) -> criterios.conjunction();
        }
        if (tieneRol(usuario, "CLIENTE")) {
            return (pedido, consulta, criterios) -> criterios.equal(pedido.get("correoCliente"), correo(usuario));
        }
        if (tieneRol(usuario, "REPARTIDOR")) {
            return (pedido, consulta, criterios) -> criterios.equal(pedido.get("correoRepartidor"), correo(usuario));
        }
        throw new AccessDeniedException("No tienes permiso para consultar pedidos.");
    }

    private void verificarRepartidor(Pedido pedido, Authentication usuario) {
        if (tieneRol(usuario, "REPARTIDOR") && !correo(usuario).equals(pedido.getCorreoRepartidor())) {
            throw new AccessDeniedException("Este pedido está asignado a otro repartidor.");
        }
    }

    private boolean tieneRol(Authentication usuario, String rol) {
        return usuario.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + rol));
    }

    private String correo(Authentication usuario) {
        return usuario.getName().strip().toLowerCase(Locale.ROOT);
    }

    public record Compra(PedidoRespuesta pedido, boolean nueva) {}

    private Pedido buscarParaActualizar(Long id) {
        return pedidos.buscarParaActualizar(id).orElseThrow(() -> pedidoNoEncontrado(id));
    }

    private RecursoNoEncontradoException pedidoNoEncontrado(Long id) {
        return new RecursoNoEncontradoException("Pedido no encontrado: " + id);
    }
}
