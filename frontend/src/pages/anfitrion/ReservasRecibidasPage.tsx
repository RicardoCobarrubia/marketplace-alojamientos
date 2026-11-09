import { CalendarClock } from 'lucide-react';

/** El backend todavía no tiene el módulo de reservas (RF-23 a RF-30). */
export default function ReservasRecibidasPage() {
  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Reservas recibidas</h1>
          <p>Las reservas de tus alojamientos</p>
        </div>
      </div>
      <section className="tarjeta estado-vacio">
        <CalendarClock size={28} aria-hidden="true" />
        <h2>Muy pronto</h2>
        <p>Esta sección se habilita cuando el backend tenga el módulo de reservas.</p>
      </section>
    </>
  );
}
