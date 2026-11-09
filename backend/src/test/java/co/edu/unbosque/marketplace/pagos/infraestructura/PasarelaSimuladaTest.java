package co.edu.unbosque.marketplace.pagos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplace.pagos.dominio.ResultadoPago.EstadoPago;
import co.edu.unbosque.marketplace.pagos.dominio.SolicitudCobro;

class PasarelaSimuladaTest {

	private final PasarelaSimulada pasarela = new PasarelaSimulada();

	@Test
	void cobraUnaSolaVezPorClave() {
		SolicitudCobro solicitud = new SolicitudCobro("reserva-1", BigDecimal.TEN, "Reserva 1");

		var primero = pasarela.cobrar(solicitud);
		var segundo = pasarela.cobrar(solicitud);

		assertThat(primero.estado()).isEqualTo(EstadoPago.APROBADO);
		assertThat(segundo).isEqualTo(primero);
		assertThat(pasarela.cantidadCobros()).isEqualTo(1);
		assertThat(pasarela.consultar("reserva-1", BigDecimal.TEN)).isEqualTo(primero);
		assertThat(pasarela.consultar("reserva-2", BigDecimal.TEN).estado()).isEqualTo(EstadoPago.NO_COBRADO);
	}

	@Test
	void devuelveUnaSolaVezPorClave() {
		pasarela.devolver("sim_1", BigDecimal.TEN, "reserva-1");
		pasarela.devolver("sim_1", BigDecimal.TEN, "reserva-1");

		assertThat(pasarela.cantidadDevoluciones()).isEqualTo(1);
	}
}
