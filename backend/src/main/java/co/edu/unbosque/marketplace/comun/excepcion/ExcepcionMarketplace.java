package co.edu.unbosque.marketplace.comun.excepcion;

import java.util.Map;

import org.springframework.http.HttpStatus;

import co.edu.unbosque.marketplace.comun.web.ManejadorGlobalExcepciones;

/**
 * Base de las excepciones de negocio. Cada una sabe con qué código HTTP se responde y lleva
 * un {@code codigo} estable que el frontend puede usar para decidir qué mostrar.
 * {@link ManejadorGlobalExcepciones} las traduce todas con un solo método.
 */
public abstract class ExcepcionMarketplace extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final HttpStatus estado;
	private final String codigo;
	private final String titulo;

	protected ExcepcionMarketplace(HttpStatus estado, String codigo, String titulo, String mensaje) {
		super(mensaje);
		this.estado = estado;
		this.codigo = codigo;
		this.titulo = titulo;
	}

	public HttpStatus getEstado() {
		return estado;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getTitulo() {
		return titulo;
	}

	/** Datos extra que se agregan a la respuesta, por ejemplo la lista de campos faltantes. */
	public Map<String, Object> getPropiedadesAdicionales() {
		return Map.of();
	}
}
