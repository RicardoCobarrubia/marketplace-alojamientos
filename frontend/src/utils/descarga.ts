/** Hace que el navegador guarde un archivo recibido de la API con el nombre indicado. */
export function guardarArchivo(archivo: Blob, nombre: string): void {
  const url = URL.createObjectURL(archivo);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  // Se libera después de que el navegador empezó la descarga.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
