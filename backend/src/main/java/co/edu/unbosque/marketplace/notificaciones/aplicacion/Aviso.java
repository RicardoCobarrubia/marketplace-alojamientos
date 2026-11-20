package co.edu.unbosque.marketplace.notificaciones.aplicacion;

/** Lo que se le notifica a una persona por un evento. */
public record Aviso(Long usuarioId, String titulo, String mensaje) {
}
