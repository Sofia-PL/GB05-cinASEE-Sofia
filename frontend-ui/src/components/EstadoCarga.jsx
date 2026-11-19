/* =========================================================
   Mensajes comunes mientras se leen datos del backend
   ========================================================= */

export function Cargando({ texto = 'Cargando…' }) {
  return <p className="estado-carga" role="status">{texto}</p>;
}

export function ErrorCarga({ mensaje, onReintentar }) {
  return (
    <div className="cinasee-error" role="alert">
      {mensaje}
      {onReintentar && (
        <>
          {' '}
          <button type="button" className="cinasee-aviso-link" onClick={onReintentar}>
            Reintentar
          </button>
        </>
      )}
    </div>
  );
}

export function SinDatos({ children }) {
  return <div className="cinasee-aviso">{children}</div>;
}
