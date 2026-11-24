package cl.duoc.gasvolcan.auth.dto;

import cl.duoc.gasvolcan.auth.model.Usuario;

public record UsuarioRespuesta(Long id, String nombre, String correo, String rol, boolean activo) {

	public static UsuarioRespuesta desde(Usuario usuario) {
		return new UsuarioRespuesta(usuario.getId(), usuario.nombreVisible(),
				usuario.getCorreo(), usuario.getRol(), usuario.isHabilitado());
	}
}
