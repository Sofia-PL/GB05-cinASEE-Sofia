/* =========================================================
   Avisos comunes de las páginas de administración
   ========================================================= */

const estiloBase = {
  padding: '10px 16px',
  borderRadius: '4px',
  marginBottom: '18px',
  fontSize: '0.88rem',
  fontWeight: 600,
};

const ESTILOS = {
  ok: { background: 'rgba(40,167,69,0.12)', border: '1px solid rgba(40,167,69,0.3)', color: '#155724' },
  error: { background: 'rgba(220,53,69,0.1)', border: '1px solid rgba(220,53,69,0.3)', color: '#721c24' },
  info: { background: 'rgba(255,193,7,0.12)', border: '1px solid rgba(255,193,7,0.4)', color: '#856404' },
};

/** Mensaje de resultado: { tipo: 'ok' | 'error' | 'info', texto } */
export function AvisoAdmin({ aviso, onCerrar }) {
  if (!aviso) return null;
  return (
    <div role={aviso.tipo === 'error' ? 'alert' : 'status'} style={{ ...estiloBase, ...ESTILOS[aviso.tipo] }}>
      {aviso.texto}
      {onCerrar && (
        <button
          type="button"
          onClick={onCerrar}
          aria-label="Cerrar aviso"
          style={{ float: 'right', background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', fontWeight: 700 }}
        >
          ✕
        </button>
      )}
    </div>
  );
}

/** Aviso cuando el token de la sesión ha caducado */
export function AvisoSinToken() {
  return (
    <AvisoAdmin
      aviso={{
        tipo: 'info',
        texto: 'Tu sesión ha caducado. Vuelve a iniciar sesión para consultar y modificar los datos.',
      }}
    />
  );
}
