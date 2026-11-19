package co.edu.unbosque.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.parser.PartTree;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Revisa sin base de datos que cada método de los repositorios sin {@code @Query} tenga un nombre
 * que Spring Data sabe convertir en consulta. Un nombre mal escrito haría fallar el arranque.
 */
class RepositoriosTest {

	@Test
	void losMetodosDerivadosNombranPropiedadesQueExisten() {
		List<Class<?>> repositorios = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("co.edu.unbosque.marketplace").stream()
				.filter(clase -> clase.isInterface() && clase.isAssignableTo(JpaRepository.class))
				.<Class<?>>map(clase -> clase.reflect())
				.toList();
		assertThat(repositorios).hasSizeGreaterThan(5);

		for (Class<?> repositorio : repositorios) {
			Class<?> entidad = entidadDe(repositorio);
			for (Method metodo : repositorio.getDeclaredMethods()) {
				if (!metodo.isAnnotationPresent(Query.class) && !metodo.isDefault()) {
					assertThatCode(() -> new PartTree(metodo.getName(), entidad))
							.as(repositorio.getSimpleName() + "." + metodo.getName())
							.doesNotThrowAnyException();
				}
			}
		}
	}

	private static Class<?> entidadDe(Class<?> repositorio) {
		for (Type tipo : repositorio.getGenericInterfaces()) {
			if (tipo instanceof ParameterizedType parametrizado && parametrizado.getRawType() == JpaRepository.class) {
				return (Class<?>) parametrizado.getActualTypeArguments()[0];
			}
		}
		throw new IllegalStateException("No se encontró la entidad de " + repositorio.getName());
	}
}
