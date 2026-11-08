package co.edu.unbosque.marketplace.model;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.exception.ReglaNegocioException;
import co.edu.unbosque.marketplace.model.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.model.ServicioAdicional.Modalidad;
import co.edu.unbosque.marketplace.model.Usuario.Rol;

class AlojamientoTest {

	private Alojamiento alojamiento;

	@BeforeEach
	void preparar() {
		alojamiento = TestDatos.alojamiento(10L, TestDatos.usuario(1L, Rol.ANFITRION, "hash", "anfitrion@correo.com"));
	}

	@Test
	void seCreaEnBorradorConLaPrimeraImagenComoPrincipal() {
		assertThat(alojamiento.getEstado()).isEqualTo(EstadoAlojamiento.BORRADOR);
		assertThat(alojamiento.esDe(1L)).isTrue();
		assertThat(alojamiento.getImagenes()).hasSize(2);
		assertThat(alojamiento.getImagenes().get(0).isEsPrincipal()).isTrue();
		assertThat(alojamiento.getImagenes().get(1).isEsPrincipal()).isFalse();
		assertThat(alojamiento.camposFaltantes()).isEmpty();
	}

	@Test
	void recorreElCicloDePublicacion() {
		alojamiento.enviarARevision(AHORA);
		assertThat(alojamiento.getEstado()).isEqualTo(EstadoAlojamiento.PENDIENTE_REVISION);

		alojamiento.cambiarEstado(EstadoAlojamiento.ACTIVA, AHORA);
		alojamiento.cambiarEstado(EstadoAlojamiento.PAUSADA, AHORA);
		alojamiento.cambiarEstado(EstadoAlojamiento.ACTIVA, AHORA);

		assertThat(alojamiento.getEstado()).isEqualTo(EstadoAlojamiento.ACTIVA);
	}

	@Test
	void rechazaTransicionesInvalidas() {
		assertThatThrownBy(() -> alojamiento.cambiarEstado(EstadoAlojamiento.ACTIVA, AHORA))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "TRANSICION_INVALIDA");
		assertThatThrownBy(() -> alojamiento.cambiarEstado(EstadoAlojamiento.PAUSADA, AHORA))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void alRechazarVuelveABorradorConMotivoYAlReenviarSeLimpia() {
		alojamiento.enviarARevision(AHORA);

		alojamiento.rechazar("Las fotos no corresponden", AHORA);

		assertThat(alojamiento.getEstado()).isEqualTo(EstadoAlojamiento.BORRADOR);
		assertThat(alojamiento.getMotivoRechazo()).isEqualTo("Las fotos no corresponden");

		alojamiento.enviarARevision(AHORA);
		assertThat(alojamiento.getMotivoRechazo()).isNull();
	}

	@Test
	void indicaLosCamposQueFaltan() {
		DatosAlojamiento base = TestDatos.datosAlojamiento();
		DatosAlojamiento incompletos = new DatosAlojamiento(base.titulo(), " ", base.ubicacion(), base.tipo(),
				base.ciudad(), base.politicaCancelacion(), 0, base.precioNoche(), base.tarifaLimpieza(), Set.of(),
				List.of(), base.imagenes());

		alojamiento.actualizarInformacion(incompletos, AHORA);

		assertThat(alojamiento.camposFaltantes())
				.containsExactlyInAnyOrder("descripcion", "capacidad", "servicios", "reglas");
	}

	@Test
	void unServicioAdicionalRetiradoQuedaInactivo() {
		ServicioAdicional desayuno = alojamiento.agregarServicioAdicional("Desayuno", new BigDecimal("30000"),
				Modalidad.POR_NOCHE, AHORA);

		desayuno.retirar();

		assertThat(desayuno.isActivo()).isFalse();
		assertThat(alojamiento.getServiciosAdicionales()).containsExactly(desayuno);
	}

	@Test
	void unBloqueadoSoloPuedePasarAPausada() {
		assertThat(EstadoAlojamiento.BLOQUEADA.siguientes()).containsExactly(EstadoAlojamiento.PAUSADA);
		assertThat(EstadoAlojamiento.PENDIENTE_REVISION.puedePasarA(EstadoAlojamiento.ACTIVA)).isTrue();
		assertThat(EstadoAlojamiento.BORRADOR.puedePasarA(EstadoAlojamiento.ACTIVA)).isFalse();
	}
}
