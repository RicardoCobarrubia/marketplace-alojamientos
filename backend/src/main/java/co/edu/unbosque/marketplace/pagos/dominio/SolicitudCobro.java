package co.edu.unbosque.marketplace.pagos.dominio;

import java.math.BigDecimal;

/** Datos de un cobro: la clave de idempotencia lo identifica ante la pasarela. */
public record SolicitudCobro(String claveIdempotencia, BigDecimal monto, String descripcion) {
}
