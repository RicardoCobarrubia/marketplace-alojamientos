/** Calificación de 1 a 5 como estrellas (reseñas, RF-33). Los estilos están en ModeracionAdmin.css. */
export function Estrellas({ valor }: { valor: number }) {
  return (
    <span className="estrellas" role="img" aria-label={`${valor} de 5 estrellas`}>
      {'★'.repeat(valor)}
      <span className="estrellas__vacia">{'★'.repeat(5 - valor)}</span>
    </span>
  );
}
