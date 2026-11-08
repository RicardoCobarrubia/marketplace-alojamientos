package co.edu.unbosque.marketplace.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Política de cancelación asociada a un alojamiento. Los parámetros (horas, porcentajes) se
 * guardan en JSON para poder ajustarlos sin cambiar código; el cálculo de la devolución se
 * implementa en MA-73.
 */
@Entity
@Table(name = "politica_cancelacion")
public class PoliticaCancelacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Short id;

	@Column(nullable = false, unique = true, length = 40)
	private String codigo;

	@Column(nullable = false, length = 80)
	private String nombre;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String parametros;

	protected PoliticaCancelacion() {
		// Requerido por JPA
	}

	public Short getId() {
		return id;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getNombre() {
		return nombre;
	}

	public String getParametros() {
		return parametros;
	}
}
