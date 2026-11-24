package cl.duoc.gasvolcan.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 100)
	private String nombre;

	@Column(nullable = false, unique = true)
	private String correo;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	private String rol;

	@Column(nullable = false)
	private boolean habilitado = true;

	// Cambia al modificar permisos o credenciales, para rechazar tokens previos.
	@Column(nullable = false, columnDefinition = "bigint default 0")
	private long versionSesion;

	@Version
	@Column(nullable = false, columnDefinition = "bigint default 0")
	private long versionRegistro;

	public String nombreVisible() {
		if (nombre != null && !nombre.isBlank()) {
			return nombre;
		}
		// Las cuentas ya creadas en la etapa anterior pueden venir sin nombre.
		return correo.split("@", 2)[0];
	}

	public void invalidarSesiones() {
		versionSesion++;
	}
}
