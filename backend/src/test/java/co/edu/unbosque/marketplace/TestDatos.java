package co.edu.unbosque.marketplace;

import java.lang.reflect.Field;
import java.time.Instant;

import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.model.Usuario.Rol;

/** Datos para pruebas unitarias. */
public final class TestDatos {

	private TestDatos() {
	}

	/** Usuario activo con id asignado, como si viniera de la base de datos. */
	public static Usuario usuario(Long id, Rol rol, String passwordHash) {
		Usuario usuario = new Usuario("Ana Pérez", "ana@correo.com", "3001234567", passwordHash, rol,
				Instant.parse("2026-11-01T10:00:00Z"));
		asignar(usuario, "id", id);
		return usuario;
	}

	/** Cambia un campo privado; sirve para preparar estados que en producción pone otra historia. */
	public static void asignar(Object objeto, String campo, Object valor) {
		try {
			Field field = objeto.getClass().getDeclaredField(campo);
			field.setAccessible(true);
			field.set(objeto, valor);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
