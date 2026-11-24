package cl.duoc.gasvolcan.auth.dto;

public record LoginResponse(String token, String correo, String rol, String nombre) {
}
