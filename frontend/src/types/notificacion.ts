/** NotificacionDTO del backend (MA-75, MA-76). `leidaEn` es null mientras no se ha leído. */
export interface Notificacion {
  id: number;
  tipo: string;
  titulo: string;
  mensaje: string;
  creadaEn: string;
  leidaEn: string | null;
}
