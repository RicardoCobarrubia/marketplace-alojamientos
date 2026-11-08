package co.edu.unbosque.marketplace.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import co.edu.unbosque.marketplace.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Publicación de un alojamiento. Pertenece a un único anfitrión (regla de negocio 6).
 * <p>
 * Los estados y sus transiciones válidas están en {@link EstadoAlojamiento}: cualquier cambio
 * de estado pasa por {@link #cambiarEstado}, que rechaza las transiciones no permitidas
 * (MA-56, escenario 3).
 */
@Entity
@Table(name = "alojamiento")
public class Alojamiento {

	public static final int MAXIMO_IMAGENES = 10;

	// ─────────────────────────────────────────
	// Identidad y dueño
	// ─────────────────────────────────────────

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anfitrion_id", nullable = false, updatable = false)
	private Usuario anfitrion;

	// ─────────────────────────────────────────
	// Información — MA-53
	// ─────────────────────────────────────────

	@Column(nullable = false, length = 120)
	private String titulo;

	@Column(nullable = false, length = 2000)
	private String descripcion;

	@Column(nullable = false, length = 200)
	private String ubicacion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tipo_id", nullable = false)
	private TipoAlojamiento tipo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ciudad_id", nullable = false)
	private Ciudad ciudad;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "politica_cancelacion_id", nullable = false)
	private PoliticaCancelacion politicaCancelacion;

	@Column(nullable = false)
	private int capacidad;

	@Column(name = "precio_noche", nullable = false, precision = 12, scale = 2)
	private BigDecimal precioNoche;

	@Column(name = "tarifa_limpieza", nullable = false, precision = 12, scale = 2)
	private BigDecimal tarifaLimpieza;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "alojamiento_servicio",
			joinColumns = @JoinColumn(name = "alojamiento_id"),
			inverseJoinColumns = @JoinColumn(name = "servicio_id"))
	private Set<Servicio> servicios = new HashSet<>();

	@OneToMany(mappedBy = "alojamiento", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("orden")
	private List<ReglaAlojamiento> reglas = new ArrayList<>();

	@OneToMany(mappedBy = "alojamiento", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("orden")
	private List<ImagenAlojamiento> imagenes = new ArrayList<>();

	@OneToMany(mappedBy = "alojamiento", cascade = CascadeType.ALL)
	@OrderBy("id")
	private List<ServicioAdicional> serviciosAdicionales = new ArrayList<>();

	// ─────────────────────────────────────────
	// Estado — MA-56, MA-57
	// ─────────────────────────────────────────

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoAlojamiento estado;

	/** Motivo del último rechazo del administrador, para que el anfitrión lo vea (MA-57). */
	@Column(name = "motivo_rechazo", length = 500)
	private String motivoRechazo;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@Version
	private long version;

	// ─────────────────────────────────────────
	// Estados de la publicación (enunciado, regla de negocio 6)
	// ─────────────────────────────────────────

	public enum EstadoAlojamiento {
		BORRADOR,
		PENDIENTE_REVISION,
		ACTIVA,
		PAUSADA,
		BLOQUEADA;

		/** Transiciones permitidas desde este estado. */
		public Set<EstadoAlojamiento> siguientes() {
			return switch (this) {
			case BORRADOR -> EnumSet.of(PENDIENTE_REVISION, BLOQUEADA);
			case PENDIENTE_REVISION -> EnumSet.of(ACTIVA, BORRADOR, BLOQUEADA);
			case ACTIVA -> EnumSet.of(PAUSADA, BLOQUEADA);
			case PAUSADA -> EnumSet.of(ACTIVA, BLOQUEADA);
			case BLOQUEADA -> EnumSet.of(PAUSADA);
			};
		}

		public boolean puedePasarA(EstadoAlojamiento destino) {
			return siguientes().contains(destino);
		}
	}

	// ─────────────────────────────────────────
	// Constructores
	// ─────────────────────────────────────────

	protected Alojamiento() {
		// Requerido por JPA
	}

	/** Crea el alojamiento en BORRADOR, asociado al anfitrión (MA-53, escenario 1). */
	public Alojamiento(Usuario anfitrion, DatosAlojamiento datos, Instant ahora) {
		this.anfitrion = anfitrion;
		this.estado = EstadoAlojamiento.BORRADOR;
		this.creadoEn = ahora;
		actualizarInformacion(datos, ahora);
	}

	// ─────────────────────────────────────────
	// Edición — MA-53, MA-54
	// ─────────────────────────────────────────

	public void actualizarInformacion(DatosAlojamiento datos, Instant ahora) {
		this.titulo = datos.titulo().trim();
		this.descripcion = datos.descripcion().trim();
		this.ubicacion = datos.ubicacion().trim();
		this.tipo = datos.tipo();
		this.ciudad = datos.ciudad();
		this.politicaCancelacion = datos.politicaCancelacion();
		this.capacidad = datos.capacidad();
		this.precioNoche = datos.precioNoche();
		this.tarifaLimpieza = datos.tarifaLimpieza();
		this.servicios.clear();
		this.servicios.addAll(datos.servicios());
		reemplazarReglas(datos.reglas());
		reemplazarImagenes(datos.imagenes());
		this.actualizadoEn = ahora;
	}

	private void reemplazarReglas(List<String> nuevas) {
		reglas.clear();
		short orden = 0;
		for (String regla : nuevas) {
			reglas.add(new ReglaAlojamiento(this, regla, orden++));
		}
	}

	private void reemplazarImagenes(List<String> rutas) {
		imagenes.clear();
		short orden = 0;
		for (String ruta : rutas) {
			imagenes.add(new ImagenAlojamiento(this, ruta, orden == 0, orden));
			orden++;
		}
	}

	// ─────────────────────────────────────────
	// Servicios adicionales — MA-55
	// ─────────────────────────────────────────

	public ServicioAdicional agregarServicioAdicional(String nombre, BigDecimal costo,
			ServicioAdicional.Modalidad modalidad, Instant ahora) {
		ServicioAdicional servicio = new ServicioAdicional(this, nombre, costo, modalidad);
		serviciosAdicionales.add(servicio);
		this.actualizadoEn = ahora;
		return servicio;
	}

	public Optional<ServicioAdicional> buscarServicioAdicional(Long servicioId) {
		return serviciosAdicionales.stream()
				.filter(servicio -> servicio.getId() != null && servicio.getId().equals(servicioId))
				.findFirst();
	}

	// ─────────────────────────────────────────
	// Estados — MA-56, MA-57
	// ─────────────────────────────────────────

	/**
	 * Campos que faltan o no cumplen las reglas para poder publicarse (regla de negocio 6:
	 * información mínima antes de activarse). Vacía si está completo.
	 */
	public List<String> camposFaltantes() {
		List<String> faltantes = new ArrayList<>();
		if (estaVacio(titulo)) {
			faltantes.add("titulo");
		}
		if (estaVacio(descripcion)) {
			faltantes.add("descripcion");
		}
		if (estaVacio(ubicacion)) {
			faltantes.add("ubicacion");
		}
		if (tipo == null) {
			faltantes.add("tipo");
		}
		if (ciudad == null) {
			faltantes.add("ciudad");
		}
		if (politicaCancelacion == null) {
			faltantes.add("politicaCancelacion");
		}
		if (capacidad <= 0) {
			faltantes.add("capacidad");
		}
		if (precioNoche == null || precioNoche.signum() <= 0) {
			faltantes.add("precioNoche");
		}
		if (servicios.isEmpty()) {
			faltantes.add("servicios");
		}
		if (reglas.isEmpty()) {
			faltantes.add("reglas");
		}
		if (imagenes.isEmpty() || imagenes.size() > MAXIMO_IMAGENES) {
			faltantes.add("imagenes");
		}
		return faltantes;
	}

	public void cambiarEstado(EstadoAlojamiento destino, Instant ahora) {
		if (!estado.puedePasarA(destino)) {
			throw new ReglaNegocioException("TRANSICION_INVALIDA",
					"No se puede pasar de " + estado + " a " + destino + ".");
		}
		this.estado = destino;
		this.actualizadoEn = ahora;
	}

	/** El administrador rechaza la publicación: vuelve a BORRADOR con el motivo (MA-57). */
	public void rechazar(String motivo, Instant ahora) {
		cambiarEstado(EstadoAlojamiento.BORRADOR, ahora);
		this.motivoRechazo = motivo.trim();
	}

	/** Al enviarlo de nuevo a revisión se limpia el motivo del rechazo anterior. */
	public void enviarARevision(Instant ahora) {
		cambiarEstado(EstadoAlojamiento.PENDIENTE_REVISION, ahora);
		this.motivoRechazo = null;
	}

	public boolean esDe(Long usuarioId) {
		return anfitrion.getId().equals(usuarioId);
	}

	private static boolean estaVacio(String texto) {
		return texto == null || texto.isBlank();
	}

	// ─────────────────────────────────────────
	// Getters
	// ─────────────────────────────────────────

	public Long getId() {
		return id;
	}

	public Usuario getAnfitrion() {
		return anfitrion;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public String getUbicacion() {
		return ubicacion;
	}

	public TipoAlojamiento getTipo() {
		return tipo;
	}

	public Ciudad getCiudad() {
		return ciudad;
	}

	public PoliticaCancelacion getPoliticaCancelacion() {
		return politicaCancelacion;
	}

	public int getCapacidad() {
		return capacidad;
	}

	public BigDecimal getPrecioNoche() {
		return precioNoche;
	}

	public BigDecimal getTarifaLimpieza() {
		return tarifaLimpieza;
	}

	public Set<Servicio> getServicios() {
		return Set.copyOf(servicios);
	}

	public List<ReglaAlojamiento> getReglas() {
		return List.copyOf(reglas);
	}

	public List<ImagenAlojamiento> getImagenes() {
		return List.copyOf(imagenes);
	}

	public List<ServicioAdicional> getServiciosAdicionales() {
		return List.copyOf(serviciosAdicionales);
	}

	public EstadoAlojamiento getEstado() {
		return estado;
	}

	public String getMotivoRechazo() {
		return motivoRechazo;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}
}
