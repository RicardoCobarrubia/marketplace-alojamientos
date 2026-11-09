package co.edu.unbosque.marketplace.comun.configuracion;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ModelMapper para convertir entidades en DTO, igual que en Software 1.
 * Aquí queda separado de la clase principal de la aplicación.
 */
@Configuration
public class MapperConfig {

	@Bean
	public ModelMapper modelMapper() {
		return new ModelMapper();
	}
}
