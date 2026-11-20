/**
 * Módulo de perfiles (MA-51, MA-52): estado de la cuenta que maneja su dueño. Desactivar la
 * cuenta toca otros módulos (pausa los alojamientos y revisa las reservas), por eso vive aquí y
 * no en usuarios, que no depende de ninguno. Consultar y actualizar el perfil y reactivar la
 * cuenta solo tocan al usuario y están en el módulo de usuarios.
 * <p>
 * Sigue las capas del estilo elegido (ADR-001).
 */
package co.edu.unbosque.marketplace.perfiles;
