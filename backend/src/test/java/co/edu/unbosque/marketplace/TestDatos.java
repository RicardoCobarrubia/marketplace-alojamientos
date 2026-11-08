package co.edu.unbosque.marketplace;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import co.edu.unbosque.marketplace.model.Alojamiento;
import co.edu.unbosque.marketplace.model.Ciudad;
import co.edu.unbosque.marketplace.model.DatosAlojamiento;
import co.edu.unbosque.marketplace.model.PoliticaCancelacion;
import co.edu.unbosque.marketplace.model.Servicio;
import co.edu.unbosque.marketplace.model.TipoAlojamiento;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.model.Usuario.Rol;

/** Datos para pruebas unitarias. */
public final class TestDatos {

	public static final Instant AHORA = Instant.parse("2026-11-07T12:00:00Z");

	private TestDatos() {
	}

	/** Usuario activo con id asignado y correo ana@correo.com, como si viniera de la base de datos. */
	public static Usuario usuario(Long id, Rol rol, String passwordHash) {
		return usuario(id, rol, passwordHash, "ana@correo.com");
	}

	public static Usuario usuario(Long id, Rol rol, String passwordHash, String email) {
		Usuario usuario = new Usuario("Ana Pérez", email, "3001234567", passwordHash, rol,
				Instant.parse("2026-11-01T10:00:00Z"));
		asignar(usuario, "id", id);
		return usuario;
	}

	/** Datos completos y válidos de un alojamiento. */
	public static DatosAlojamiento datosAlojamiento() {
		Servicio wifi = new Servicio("WIFI", "Wifi");
		asignar(wifi, "id", (short) 1);
		return new DatosAlojamiento("Apartamento frente al mar", "Vista al mar, dos habitaciones.",
				"Calle 5 # 3-20, El Rodadero", new TipoAlojamiento("APARTAMENTO", "Apartamento"),
				new Ciudad("Santa Marta", "Magdalena"), politica(), 4, new BigDecimal("250000.00"),
				new BigDecimal("40000.00"), Set.of(wifi), List.of("No fumar"),
				List.of("https://imagenes.ejemplo.com/1.jpg", "https://imagenes.ejemplo.com/2.jpg"));
	}

	/** Alojamiento en BORRADOR con id asignado. */
	public static Alojamiento alojamiento(Long id, Usuario anfitrion) {
		Alojamiento alojamiento = new Alojamiento(anfitrion, datosAlojamiento(), AHORA);
		asignar(alojamiento, "id", id);
		return alojamiento;
	}

	public static PoliticaCancelacion politica() {
		PoliticaCancelacion politica = instanciar(PoliticaCancelacion.class);
		asignar(politica, "id", (short) 1);
		asignar(politica, "codigo", "FLEXIBLE");
		asignar(politica, "nombre", "Flexible");
		asignar(politica, "parametros", "{}");
		return politica;
	}

	/** Cambia un campo privado; sirve para preparar estados que en producción pone otra historia. */
	public static void asignar(Object objeto, String campo, Object valor) {
		try {
			Field field = buscarCampo(objeto.getClass(), campo);
			field.setAccessible(true);
			field.set(objeto, valor);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Field buscarCampo(Class<?> tipo, String campo) throws NoSuchFieldException {
		try {
			return tipo.getDeclaredField(campo);
		} catch (NoSuchFieldException e) {
			if (tipo.getSuperclass() == null) {
				throw e;
			}
			return buscarCampo(tipo.getSuperclass(), campo);
		}
	}

	private static <T> T instanciar(Class<T> tipo) {
		try {
			var constructor = tipo.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
