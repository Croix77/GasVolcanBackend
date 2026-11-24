-- Ejecutar con una cuenta de mantenimiento después de crear los esquemas de destino.
-- Detener las escrituras del sistema anterior durante la copia. La base de origen se conserva.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS gas_volcan_pedidos.migrar_negocio;
DELIMITER //
CREATE PROCEDURE gas_volcan_pedidos.migrar_negocio()
BEGIN
    DECLARE hay_productos BOOLEAN DEFAULT FALSE;
    DECLARE hay_pedidos BOOLEAN DEFAULT FALSE;
    DECLARE hay_detalles BOOLEAN DEFAULT FALSE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'gas_volcan_negocio' AND table_name = 'categorias'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Falta gas_volcan_negocio.categorias en el esquema de origen.';
    END IF;

    SELECT EXISTS(SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'gas_volcan_negocio' AND table_name = 'productos') INTO hay_productos;
    SELECT EXISTS(SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'gas_volcan_negocio' AND table_name = 'pedidos') INTO hay_pedidos;
    SELECT EXISTS(SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'gas_volcan_negocio' AND table_name = 'detalles_pedido') INTO hay_detalles;
    IF hay_pedidos <> hay_detalles THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El origen debe incluir pedidos y detalles_pedido juntos.';
    END IF;

    START TRANSACTION;

    IF EXISTS (SELECT 1 FROM gas_volcan_categorias.categorias)
        OR EXISTS (SELECT 1 FROM gas_volcan_categorias.categoria_productos)
        OR EXISTS (SELECT 1 FROM gas_volcan_productos.productos)
        OR EXISTS (SELECT 1 FROM gas_volcan_pedidos.existencias)
        OR EXISTS (SELECT 1 FROM gas_volcan_pedidos.pedidos)
        OR EXISTS (SELECT 1 FROM gas_volcan_pedidos.detalles_pedido) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Los esquemas de destino deben estar vacíos. La migración nunca sobrescribe datos.';
    END IF;

    INSERT INTO gas_volcan_categorias.categorias (id, nombre)
        SELECT id, nombre FROM gas_volcan_negocio.categorias;

    IF hay_productos THEN
        INSERT INTO gas_volcan_categorias.categoria_productos (categoria_id, producto_id, referencia)
            SELECT categoria_id, id, CONCAT('legacy-', id) FROM gas_volcan_negocio.productos;

        INSERT INTO gas_volcan_productos.productos
            (id, version, codigo, nombre, descripcion, descripcion_larga, unidad, categoria_id,
             referencia_categoria, precio, precio_comercial, precio_anterior, stock_critico, imagen, oferta, activo)
            SELECT id, COALESCE(version, 0), codigo, nombre, descripcion, descripcion_larga, unidad, categoria_id,
                   CONCAT('legacy-', id), precio, precio_comercial, precio_anterior, stock_critico, imagen, oferta, activo
            FROM gas_volcan_negocio.productos;

        INSERT INTO gas_volcan_pedidos.existencias (id, stock, version)
            SELECT id, stock, COALESCE(version, 0) FROM gas_volcan_negocio.productos;
    END IF;

    IF hay_pedidos THEN
        INSERT INTO gas_volcan_pedidos.pedidos
            (id, correo_cliente, referencia_compra, nombre, telefono, direccion, comuna, estado,
             correo_repartidor, total, fecha_creacion, fecha_entrega, pago_efectivo, latitud_entrega,
             longitud_entrega, latitud_repartidor, longitud_repartidor, fecha_ubicacion)
            SELECT id, correo_cliente, referencia_compra, nombre, telefono, direccion, comuna, estado,
                   correo_repartidor, total, fecha_creacion, fecha_entrega, pago_efectivo, latitud_entrega,
                   longitud_entrega, latitud_repartidor, longitud_repartidor, fecha_ubicacion
            FROM gas_volcan_negocio.pedidos;

        INSERT INTO gas_volcan_pedidos.detalles_pedido
            (id, pedido_id, producto_id, nombre, precio_unitario, cantidad)
            SELECT id, pedido_id, producto_id, nombre, precio_unitario, cantidad
            FROM gas_volcan_negocio.detalles_pedido;
    END IF;
    COMMIT;
END//
DELIMITER ;

CALL gas_volcan_pedidos.migrar_negocio();
DROP PROCEDURE gas_volcan_pedidos.migrar_negocio;

SELECT
    (SELECT COUNT(*) FROM gas_volcan_categorias.categorias) AS categorias,
    (SELECT COUNT(*) FROM gas_volcan_categorias.categoria_productos) AS referencias,
    (SELECT COUNT(*) FROM gas_volcan_productos.productos) AS productos,
    (SELECT COUNT(*) FROM gas_volcan_pedidos.existencias) AS existencias,
    (SELECT COUNT(*) FROM gas_volcan_pedidos.pedidos) AS pedidos,
    (SELECT COUNT(*) FROM gas_volcan_pedidos.detalles_pedido) AS detalles;
