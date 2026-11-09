import { Eye, EyeOff } from 'lucide-react';

interface PropiedadesBotonVerContrasena {
  visible: boolean;
  alCambiar: () => void;
}

/** Botón del ojo para mostrar u ocultar la contraseña dentro de un CampoTexto. */
export function BotonVerContrasena({ visible, alCambiar }: PropiedadesBotonVerContrasena) {
  return (
    <button
      type="button"
      className="campo__accesorio"
      onClick={alCambiar}
      aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
      aria-pressed={visible}
    >
      {visible ? <EyeOff size={16} /> : <Eye size={16} />}
    </button>
  );
}
