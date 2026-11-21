package co.edu.unbosque.marketplace.reportes.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria.Resultado;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.panel.aplicacion.PanelService;
import co.edu.unbosque.marketplace.panel.aplicacion.Periodo;
import co.edu.unbosque.marketplace.reportes.dominio.FormatoReporte;
import co.edu.unbosque.marketplace.reportes.dominio.GeneradorReporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

	private static final LocalDate DESDE = LocalDate.parse("2026-10-01");
	private static final LocalDate HASTA = LocalDate.parse("2026-10-30");
	private static final Periodo PERIODO = new Periodo(DESDE, HASTA);
	private static final String SOLICITUD = "PDF del 2026-10-01 al 2026-10-30";

	@Mock
	private PanelService panel;
	@Mock
	private ContenidoReporte contenido;
	@Mock
	private GeneradorReporte pdf;
	@Mock
	private Auditoria auditoria;

	private ReporteService servicio;
	private final Usuario administrador = TestDatos.usuario(9L, Rol.ADMINISTRADOR, "hash", "admin@correo.com");
	private final Reporte reporte = new Reporte("Reporte", List.of(), List.of());

	@BeforeEach
	void preparar() {
		when(pdf.formato()).thenReturn(FormatoReporte.PDF);
		servicio = new ReporteService(panel, contenido, List.of(pdf), auditoria);
	}

	private static EventoAuditoria evento(String anterior, String nuevo, Resultado resultado, String detalle) {
		return new EventoAuditoria("EXPORTAR_REPORTE", "REPORTE", null, anterior, nuevo, resultado, detalle, 9L,
				"ADMINISTRADOR");
	}

	@Test
	void registraLaSolicitudAntesDeGenerarYLuegoQueSeGenero() {
		when(panel.periodo(DESDE, HASTA)).thenReturn(PERIODO);
		when(contenido.armar(PERIODO, "admin@correo.com")).thenReturn(reporte);
		when(pdf.generar(reporte)).thenReturn(new byte[] { 1, 2, 3 });

		ArchivoReporte archivo = servicio.exportar(administrador, FormatoReporte.PDF, DESDE, HASTA);

		assertThat(archivo.nombre()).isEqualTo("reporte-gestion-2026-10-01-a-2026-10-30.pdf");
		assertThat(archivo.formato()).isEqualTo(FormatoReporte.PDF);
		assertThat(archivo.contenido()).containsExactly(1, 2, 3);
		InOrder orden = inOrder(auditoria, contenido, pdf);
		orden.verify(auditoria).registrar(evento(null, "SOLICITADO", Resultado.EXITO, SOLICITUD));
		orden.verify(contenido).armar(PERIODO, "admin@correo.com");
		orden.verify(pdf).generar(reporte);
		orden.verify(auditoria).registrar(evento("SOLICITADO", "GENERADO", Resultado.EXITO, SOLICITUD + ", 3 bytes"));
	}

	@Test
	void siLaGeneracionFallaQuedanLaSolicitudYElError() {
		when(panel.periodo(DESDE, HASTA)).thenReturn(PERIODO);
		when(contenido.armar(PERIODO, "admin@correo.com")).thenReturn(reporte);
		when(pdf.generar(reporte)).thenThrow(new IllegalStateException("disco lleno"));

		assertThatThrownBy(() -> servicio.exportar(administrador, FormatoReporte.PDF, DESDE, HASTA))
				.isInstanceOf(IllegalStateException.class);

		verify(auditoria).registrar(evento(null, "SOLICITADO", Resultado.EXITO, SOLICITUD));
		verify(auditoria).registrar(evento("SOLICITADO", "FALLIDO", Resultado.ERROR,
				SOLICITUD + " (IllegalStateException)"));
	}

	@Test
	void rechazaUnPeriodoDeMasDeUnAnioSinGenerarNada() {
		LocalDate inicio = LocalDate.parse("2025-01-01");
		when(panel.periodo(inicio, HASTA)).thenReturn(new Periodo(inicio, HASTA));

		assertThatThrownBy(() -> servicio.exportar(administrador, FormatoReporte.PDF, inicio, HASTA))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "RANGO_DEMASIADO_LARGO");
		verify(contenido, never()).armar(any(), any());
		verify(auditoria, never()).registrar(any());
	}
}
