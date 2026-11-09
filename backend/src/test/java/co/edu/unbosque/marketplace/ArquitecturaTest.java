package co.edu.unbosque.marketplace;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Comprueba que el código respeta el estilo elegido en el ADR-001: monolito modular en capas,
 * con puertos y adaptadores solo en pagos y notificaciones. Si alguien rompe una regla, el
 * pipeline falla.
 */
@AnalyzeClasses(packages = "co.edu.unbosque.marketplace", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

	/**
	 * Las capas de cada módulo dependen hacia abajo: presentación → aplicación → dominio e
	 * infraestructura. Nadie usa la capa de presentación de otro, y a la capa de aplicación
	 * solo llega la presentación.
	 */
	@ArchTest
	static final ArchRule capas = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.layer("Presentacion").definedBy("..presentacion..")
			.layer("Aplicacion").definedBy("..aplicacion..")
			.layer("Dominio").definedBy("..dominio..")
			.layer("Infraestructura").definedBy("..infraestructura..")
			.whereLayer("Presentacion").mayNotBeAccessedByAnyLayer()
			.whereLayer("Aplicacion").mayOnlyBeAccessedByLayers("Presentacion")
			.whereLayer("Infraestructura").mayOnlyBeAccessedByLayers("Aplicacion")
			.whereLayer("Dominio").mayOnlyBeAccessedByLayers("Presentacion", "Aplicacion", "Infraestructura");

	/** Los módulos no forman ciclos: si alojamientos usa usuarios, usuarios no puede usar alojamientos. */
	@ArchTest
	static final ArchRule modulosSinCiclos = slices()
			.matching("co.edu.unbosque.marketplace.(*)..")
			.should().beFreeOfCycles();

	/** El código común no depende de ningún módulo de negocio. */
	@ArchTest
	static final ArchRule comunIndependiente = noClasses()
			.that().resideInAPackage("..marketplace.comun..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"..marketplace.usuarios..", "..marketplace.alojamientos..",
					"..marketplace.disponibilidad..", "..marketplace.pagos..",
					"..marketplace.notificaciones..");

	/**
	 * ADR-001 y ADR-006: el dominio y la aplicación no conocen el SDK de Stripe; solo el adaptador
	 * de pagos lo usa, así que cambiar de proveedor toca un componente.
	 */
	@ArchTest
	static final ArchRule pagosAislados = noClasses()
			.that().resideInAnyPackage("..dominio..", "..aplicacion..")
			.should().dependOnClassesThat().resideInAPackage("com.stripe..")
			.allowEmptyShould(true);

	/** El dominio no depende de Spring MVC ni de detalles HTTP de los controladores. */
	@ArchTest
	static final ArchRule dominioSinWeb = noClasses()
			.that().resideInAPackage("..dominio..")
			.should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..");
}
