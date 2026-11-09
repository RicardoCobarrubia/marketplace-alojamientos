package co.edu.unbosque.marketplace.usuarios.dominio;

import org.springframework.http.HttpStatus;

import co.edu.unbosque.marketplace.comun.excepcion.ExcepcionMarketplace;

/**
 * La cuenta está desactivada por su dueño: solo puede reactivarse (MA-48, escenario 2; la
 * reactivación es MA-52). El frontend usa el código {@code CUENTA_DESACTIVADA} para ofrecerla. HTTP 403.
 */
public class CuentaDesactivadaException extends ExcepcionMarketplace {

	private static final long serialVersionUID = 1L;

	public CuentaDesactivadaException() {
		super(HttpStatus.FORBIDDEN, "CUENTA_DESACTIVADA", "Cuenta desactivada",
				"Tu cuenta está desactivada. Puedes reactivarla para volver a entrar.");
	}
}
