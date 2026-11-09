package co.edu.unbosque.marketplace.reservas.dominio;

import java.math.BigDecimal;

import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional.Modalidad;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Servicio adicional contratado, con el precio que tenía al reservar (MA-64, MA-66). */
@Entity
@Table(name = "reserva_servicio")
public class ReservaServicio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reserva_id", nullable = false)
	private Reserva reserva;

	@Column(name = "servicio_adicional_id", nullable = false)
	private Long servicioAdicionalId;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Modalidad modalidad;

	@Column(name = "costo_unitario", nullable = false, precision = 12, scale = 2)
	private BigDecimal costoUnitario;

	@Column(nullable = false)
	private int cantidad;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal valor;

	protected ReservaServicio() {
		// Requerido por JPA
	}

	ReservaServicio(Reserva reserva, Long servicioAdicionalId, String nombre, Modalidad modalidad,
			BigDecimal costoUnitario, int cantidad, BigDecimal valor) {
		this.reserva = reserva;
		this.servicioAdicionalId = servicioAdicionalId;
		this.nombre = nombre;
		this.modalidad = modalidad;
		this.costoUnitario = costoUnitario;
		this.cantidad = cantidad;
		this.valor = valor;
	}

	public Long getServicioAdicionalId() {
		return servicioAdicionalId;
	}

	public String getNombre() {
		return nombre;
	}

	public Modalidad getModalidad() {
		return modalidad;
	}

	public BigDecimal getCostoUnitario() {
		return costoUnitario;
	}

	public int getCantidad() {
		return cantidad;
	}

	public BigDecimal getValor() {
		return valor;
	}
}
