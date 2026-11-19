package co.edu.unbosque.marketplace.pagos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplace.pagos.dominio.ResultadoPago.EstadoPago;
import co.edu.unbosque.marketplace.pagos.dominio.SolicitudCobro;

class PasarelaSimuladaTest {

	private final PasarelaSimulada pasarela = new PasarelaSimulada();
	private final SolicitudCobro solicitud = new SolicitudCobro("reserva-1", BigDecimal.TEN, "Reserva 1");

	@Test
	void prepararConLaMismaClaveDaLaMismaReferenciaYNoCobra() {
		String referencia = pasarela.preparar(solicitud);

		assertThat(pasarela.preparar(solicitud)).isEqualTo(referencia);
		assertThat(pasarela.consultar(referencia).estado()).isEqualTo(EstadoPago.NO_COBRADO);
		assertThat(pasarela.cantidadCobros()).isZero();
	}

	@Test
	void cobraUnaSolaVezPorReferencia() {
		String referencia = pasarela.preparar(solicitud);

		assertThat(pasarela.cobrar(referencia, solicitud).estado()).isEqualTo(EstadoPago.APROBADO);
		pasarela.cobrar(referencia, solicitud);

		assertThat(pasarela.cantidadCobros()).isEqualTo(1);
		assertThat(pasarela.consultar(referencia).referencia()).isEqualTo(referencia);
	}

	@Test
	void devuelveUnaSolaVezPorCobro() {
		pasarela.devolver("sim_1", BigDecimal.TEN, "reserva-1", 1);
		pasarela.devolver("sim_1", BigDecimal.TEN, "reserva-1", 2);

		assertThat(pasarela.cantidadDevoluciones()).isEqualTo(1);
	}
}
