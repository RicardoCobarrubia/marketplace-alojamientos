package co.edu.unbosque.marketplace.cotizacion.dominio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reglas de la cotización que configura el administrador (MA-65): tarifa de servicio y
 * descuento por estadía larga. Hay una sola fila; se lee en cada cotización, así que un cambio
 * se aplica de inmediato a las cotizaciones nuevas.
 */
@Entity
@Table(name = "configuracion_cotizacion")
public class ConfiguracionCotizacion {

	/** La configuración es una sola fila con este id. */
	public static final short ID_UNICO = 1;

	@Id
	private Short id;

	@Column(name = "porcentaje_tarifa_servicio", nullable = false, precision = 5, scale = 2)
	private BigDecimal porcentajeTarifaServicio;

	@Column(name = "porcentaje_descuento_estadia_larga", nullable = false, precision = 5, scale = 2)
	private BigDecimal porcentajeDescuentoEstadiaLarga;

	@Column(name = "minimo_noches_descuento", nullable = false)
	private int minimoNochesDescuento;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@Column(name = "actualizado_por")
	private Long actualizadoPor;

	protected ConfiguracionCotizacion() {
		// Requerido por JPA
	}

	/** Para pruebas y para armar la configuración sin base de datos. */
	public ConfiguracionCotizacion(BigDecimal porcentajeTarifaServicio, BigDecimal porcentajeDescuentoEstadiaLarga,
			int minimoNochesDescuento) {
		this.id = ID_UNICO;
		this.porcentajeTarifaServicio = porcentajeTarifaServicio;
		this.porcentajeDescuentoEstadiaLarga = porcentajeDescuentoEstadiaLarga;
		this.minimoNochesDescuento = minimoNochesDescuento;
	}

	public void actualizar(BigDecimal porcentajeTarifaServicio, BigDecimal porcentajeDescuentoEstadiaLarga,
			int minimoNochesDescuento, Long administradorId, Instant ahora) {
		this.porcentajeTarifaServicio = porcentajeTarifaServicio;
		this.porcentajeDescuentoEstadiaLarga = porcentajeDescuentoEstadiaLarga;
		this.minimoNochesDescuento = minimoNochesDescuento;
		this.actualizadoPor = administradorId;
		this.actualizadoEn = ahora;
	}

	public BigDecimal getPorcentajeTarifaServicio() {
		return porcentajeTarifaServicio;
	}

	public BigDecimal getPorcentajeDescuentoEstadiaLarga() {
		return porcentajeDescuentoEstadiaLarga;
	}

	public int getMinimoNochesDescuento() {
		return minimoNochesDescuento;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}
}
