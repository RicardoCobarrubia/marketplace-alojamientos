package co.edu.unbosque.marketplace.usuarios.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class UsuarioDTOTest {

	private static ValidatorFactory fabrica;
	private static Validator validador;

	@BeforeAll
	static void crear() {
		fabrica = Validation.buildDefaultValidatorFactory();
		validador = fabrica.getValidator();
	}

	@AfterAll
	static void cerrar() {
		fabrica.close();
	}

	private static boolean telefonoValido(String telefono) {
		UsuarioDTO perfil = new UsuarioDTO(null, "Ana", null, telefono, null, null, null);
		return validador.validateProperty(perfil, "telefono", UsuarioDTO.Perfil.class).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "3001234567", "+57 300 123 4567", "1234567", "12345678901234567890",
			"+1234567890123456789" })
	void aceptaDeSieteDigitosHastaVeinteCaracteres(String telefono) {
		assertThat(telefonoValido(telefono)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "1      ", "123456", "123456789012345678901", "+57  3001234567", "300-123-4567",
			" 3001234567", "3001234567 ", "+57 300 123 4567 89012" })
	void rechazaPocosDigitosMasDeVeinteCaracteresYOtrosSimbolos(String telefono) {
		assertThat(telefonoValido(telefono)).isFalse();
	}
}
