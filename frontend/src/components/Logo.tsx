import './Logo.css';

interface PropiedadesLogo {
  /** "claro" para fondos oscuros, "oscuro" para fondos claros. */
  variante?: 'claro' | 'oscuro';
}

export function Logo({ variante = 'oscuro' }: PropiedadesLogo) {
  return (
    <span className={`logo logo--${variante}`}>
      <span className="logo__insignia" aria-hidden="true">
        LR
      </span>
      <span className="logo__nombre">LR Alojamientos</span>
    </span>
  );
}
