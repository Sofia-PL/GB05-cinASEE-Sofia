import { useState, useEffect } from 'react';
import { useAuth } from '../../context/useAuth';
import {
  obtenerPeliculas,
  obtenerPeliculasEnCartelera,
  crearPelicula,
  actualizarPelicula,
  eliminarPelicula,
} from '../../api/cartelera';
import { AvisoAdmin, AvisoSinToken } from './AdminAvisos';

/* Géneros en mayúsculas y sin tildes, como los guarda Cartelera (Comp. 1) */
const GENRES = [
  { value: 'ACCION', label: 'Acción' },
  { value: 'CIENCIA_FICCION', label: 'Ciencia Ficción' },
  { value: 'DRAMA', label: 'Drama' },
  { value: 'COMEDIA', label: 'Comedia' },
  { value: 'TERROR', label: 'Terror' },
  { value: 'ANIMACION', label: 'Animación' },
  { value: 'FANTASIA', label: 'Fantasía' },
  { value: 'THRILLER', label: 'Thriller' },
  { value: 'ROMANCE', label: 'Romance' },
];

const AGE_RATINGS = ['TP', '+7', '+12', '+16', '+18'];

const FORM_VACIO = {
  id: null,
  title: '',
  genre: 'ACCION',
  durationMinutes: 120,
  ageRating: '+12',
  synopsis: '',
  posterUrl: '',
  trailerUrl: '',
};

/** Opciones de un select añadiendo el valor actual si no está en la lista (datos ya guardados) */
function conValorActual(opciones, valor) {
  return valor && !opciones.includes(valor) ? [...opciones, valor] : opciones;
}

/** Cuerpo que espera POST/PUT /movies (CreateMovieRequest) */
function cuerpoPelicula(form) {
  return {
    title: form.title.trim(),
    synopsis: form.synopsis.trim() || null,
    durationMinutes: Number(form.durationMinutes),
    genre: form.genre,
    ageRating: form.ageRating,
    posterUrl: form.posterUrl.trim() || null,
    trailerUrl: form.trailerUrl.trim() || null,
  };
}

export default function AdminPeliculasPage() {
  const { tokenUsuario } = useAuth();
  const token = tokenUsuario(); // null si la sesión ha caducado

  const [peliculas, setPeliculas] = useState([]);
  /** Ids de las películas con sesiones futuras no canceladas (lo calcula Cartelera) */
  const [enCartelera, setEnCartelera] = useState(new Set());
  const [cargando, setCargando] = useState(true);
  const [recarga, setRecarga] = useState(0);
  const [aviso, setAviso] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const [busqueda, setBusqueda] = useState('');
  const [filtroGenero, setFiltroGenero] = useState('TODOS');

  // null = cerrado, 'create' = nueva, 'edit' = modificar
  const [modalMode, setModalMode] = useState(null);
  const [formData, setFormData] = useState(FORM_VACIO);

  useEffect(() => {
    let cancelado = false;
    Promise.all([
      obtenerPeliculas(),
      obtenerPeliculasEnCartelera(),
    ])
      .then(([todas, activas]) => {
        if (cancelado) return;
        setPeliculas(Array.isArray(todas) ? todas : []);
        setEnCartelera(new Set((Array.isArray(activas) ? activas : []).map((m) => m.id)));
      })
      .catch((err) => {
        if (!cancelado) setAviso({ tipo: 'error', texto: `No se pudo cargar el catálogo: ${err.message}` });
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => { cancelado = true; };
  }, [recarga]);

  function abrirCrear() {
    setFormData(FORM_VACIO);
    setModalMode('create');
  }

  function abrirEditar(peli) {
    setFormData({
      id: peli.id,
      title: peli.title || '',
      genre: peli.genre || 'ACCION',
      durationMinutes: peli.durationMinutes || 120,
      ageRating: peli.ageRating || '+12',
      synopsis: peli.synopsis || '',
      posterUrl: peli.posterUrl || '',
      trailerUrl: peli.trailerUrl || '',
    });
    setModalMode('edit');
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!formData.title.trim()) return;
    setGuardando(true);
    try {
      const cuerpo = cuerpoPelicula(formData);
      if (modalMode === 'create') {
        await crearPelicula(cuerpo, token);
        setAviso({ tipo: 'ok', texto: `Película "${cuerpo.title}" creada en el catálogo.` });
      } else {
        await actualizarPelicula(formData.id, cuerpo, token);
        setAviso({ tipo: 'ok', texto: `Película "${cuerpo.title}" actualizada.` });
      }
      setModalMode(null);
      setRecarga((n) => n + 1);
    } catch (err) {
      setAviso({ tipo: 'error', texto: `No se pudo guardar la película: ${err.message}` });
    } finally {
      setGuardando(false);
    }
  }

  async function handleDelete(peli) {
    if (!window.confirm(`¿Confirmas la eliminación definitiva de "${peli.title}" del catálogo central?`)) return;
    try {
      await eliminarPelicula(peli.id, token);
      setAviso({ tipo: 'ok', texto: `Película "${peli.title}" eliminada.` });
      setRecarga((n) => n + 1);
    } catch (err) {
      const texto = err.status === 409
        ? `No se puede eliminar "${peli.title}" porque tiene sesiones asociadas. Resuelve esas sesiones antes de eliminarla.`
        : `No se pudo eliminar la película: ${err.message}`;
      setAviso({ tipo: 'error', texto });
    }
  }

  const peliculasFiltradas = peliculas.filter((p) => {
    const coincideTitulo = (p.title || '').toLowerCase().includes(busqueda.toLowerCase());
    const coincideGenero = filtroGenero === 'TODOS' || p.genre === filtroGenero;
    return coincideTitulo && coincideGenero;
  });

  // Géneros conocidos + los que ya existan en la BD con otro valor
  const generosFiltro = [...new Set([...GENRES.map((g) => g.value), ...peliculas.map((p) => p.genre).filter(Boolean)])];

  const etiquetaGenero = (g) => GENRES.find((x) => x.value === g)?.label || g;

  return (
    <div className="admin-page-container">
      <header className="admin-header">
        <div>
          <h1 className="admin-header-title">Gestión de Catálogo de Películas</h1>
          <p className="admin-header-desc">
            Administración central de títulos, fichas técnicas, tráileres oficiales, pósters y sinopsis.
          </p>
        </div>

        <button className="admin-btn admin-btn-primary" type="button" onClick={abrirCrear} disabled={!token}>
          + Añadir Película
        </button>
      </header>

      {!token && <AvisoSinToken />}
      <AvisoAdmin aviso={aviso} onCerrar={() => setAviso(null)} />

      {/* Barra de filtros y búsqueda */}
      <div className="admin-panel" style={{ padding: '14px 18px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', gap: '16px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ flex: '1 1 260px' }}>
            <input
              type="text"
              className="admin-input"
              placeholder="Buscar película por título..."
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
            />
          </div>

          <div style={{ width: '200px' }}>
            <select
              className="admin-select"
              value={filtroGenero}
              onChange={(e) => setFiltroGenero(e.target.value)}
            >
              <option value="TODOS">Todos los géneros</option>
              {generosFiltro.map((g) => (
                <option key={g} value={g}>{etiquetaGenero(g)}</option>
              ))}
            </select>
          </div>

          <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginLeft: 'auto' }}>
            Registros encontrados: <strong>{peliculasFiltradas.length}</strong>
          </div>
        </div>
      </div>

      {/* Tabla CRUD de Películas */}
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th style={{ width: '60px' }}>Póster</th>
              <th>Título y Ficha</th>
              <th>Género</th>
              <th>Duración</th>
              <th>Edad</th>
              <th>Tráiler</th>
              <th title="Calculado por Cartelera: tiene sesiones futuras no canceladas">En cartelera</th>
              <th style={{ textAlign: 'right' }}>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {cargando ? (
              <tr>
                <td colSpan="8" style={{ textAlign: 'center', padding: '36px', color: 'var(--text-muted)' }}>
                  Cargando catálogo…
                </td>
              </tr>
            ) : peliculasFiltradas.length === 0 ? (
              <tr>
                <td colSpan="8" style={{ textAlign: 'center', padding: '36px', color: 'var(--text-muted)' }}>
                  No se han encontrado películas con el criterio de búsqueda especificado.
                </td>
              </tr>
            ) : (
              peliculasFiltradas.map((peli) => {
                const durH = Math.floor((peli.durationMinutes || 0) / 60);
                const durM = (peli.durationMinutes || 0) % 60;

                return (
                  <tr key={peli.id}>
                    <td>
                      {peli.posterUrl ? (
                        <img
                          src={peli.posterUrl}
                          alt={peli.title}
                          style={{
                            width: '46px',
                            height: '64px',
                            objectFit: 'cover',
                            borderRadius: '3px',
                            border: '1px solid rgba(43,18,21,0.15)',
                            display: 'block',
                          }}
                        />
                      ) : (
                        <div style={{
                          width: '46px',
                          height: '64px',
                          background: 'var(--burgundy)',
                          color: '#fff',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '1.2rem',
                          borderRadius: '3px',
                        }}>
                          {(peli.title || '?').charAt(0)}
                        </div>
                      )}
                    </td>

                    <td>
                      <strong style={{ fontSize: '0.98rem', color: 'var(--burgundy)', display: 'block' }}>
                        {peli.title}
                      </strong>
                      <p style={{
                        margin: '4px 0 0',
                        fontSize: '0.8rem',
                        color: 'var(--text-muted)',
                        maxWidth: '420px',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                      }}>
                        {peli.synopsis}
                      </p>
                    </td>

                    <td>
                      <span className="admin-badge admin-badge-dark">{etiquetaGenero(peli.genre)}</span>
                    </td>

                    <td style={{ whiteSpace: 'nowrap' }}>
                      {durH}h {durM}m ({peli.durationMinutes} min)
                    </td>

                    <td>
                      <span className="admin-badge admin-badge-warning">{peli.ageRating}</span>
                    </td>

                    <td>
                      {peli.trailerUrl ? (
                        <a
                          href={peli.trailerUrl}
                          target="_blank"
                          rel="noreferrer"
                          style={{ color: 'var(--granate)', fontWeight: 600, fontSize: '0.82rem' }}
                        >
                          Ver enlace
                        </a>
                      ) : (
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>Sin tráiler</span>
                      )}
                    </td>

                    <td>
                      {enCartelera.has(peli.id) ? (
                        <span className="admin-badge admin-badge-success">Con sesiones</span>
                      ) : (
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Sin sesiones</span>
                      )}
                    </td>

                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <button
                        className="admin-btn admin-btn-secondary admin-btn-sm"
                        type="button"
                        onClick={() => abrirEditar(peli)}
                        disabled={!token}
                        style={{ marginRight: '6px' }}
                      >
                        Editar
                      </button>
                      <button
                        className="admin-btn admin-btn-danger admin-btn-sm"
                        type="button"
                        onClick={() => handleDelete(peli)}
                        disabled={!token}
                      >
                        Eliminar
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Modal CRUD: Añadir / Modificar Película */}
      {modalMode && (
        <div className="admin-modal-backdrop" onClick={() => setModalMode(null)}>
          <div className="admin-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal-header">
              <h2 className="admin-modal-title">
                {modalMode === 'create' ? 'Nueva Película' : `Modificar: ${formData.title}`}
              </h2>
              <button
                type="button"
                className="admin-btn admin-btn-secondary admin-btn-sm"
                onClick={() => setModalMode(null)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSubmit}>
              <div className="admin-form-group">
                <label className="admin-label">Título de la Película *</label>
                <input
                  type="text"
                  className="admin-input"
                  required
                  placeholder="Ej: Oppenheimer"
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label">Género Cinematográfico *</label>
                  <select
                    className="admin-select"
                    required
                    value={formData.genre}
                    onChange={(e) => setFormData({ ...formData, genre: e.target.value })}
                  >
                    {conValorActual(GENRES.map((g) => g.value), formData.genre).map((g) => (
                      <option key={g} value={g}>{etiquetaGenero(g)}</option>
                    ))}
                  </select>
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Duración en Minutos *</label>
                  <input
                    type="number"
                    className="admin-input"
                    required
                    min={1}
                    max={600}
                    value={formData.durationMinutes}
                    onChange={(e) => setFormData({ ...formData, durationMinutes: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label">URL del Póster / Fotografía</label>
                  <input
                    type="text"
                    className="admin-input"
                    placeholder="https://..."
                    value={formData.posterUrl}
                    onChange={(e) => setFormData({ ...formData, posterUrl: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Clasificación por Edad *</label>
                  <select
                    className="admin-select"
                    required
                    value={formData.ageRating}
                    onChange={(e) => setFormData({ ...formData, ageRating: e.target.value })}
                  >
                    {conValorActual(AGE_RATINGS, formData.ageRating).map((r) => (
                      <option key={r} value={r}>{r}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="admin-form-group">
                <label className="admin-label">URL del Tráiler Oficial</label>
                <input
                  type="url"
                  className="admin-input"
                  placeholder="https://www.youtube.com/watch?v=..."
                  value={formData.trailerUrl}
                  onChange={(e) => setFormData({ ...formData, trailerUrl: e.target.value })}
                />
              </div>

              <div className="admin-form-group">
                <label className="admin-label">Sinopsis Argumental</label>
                <textarea
                  className="admin-textarea"
                  rows={4}
                  placeholder="Escribe la descripción argumental y sinopsis de la obra..."
                  value={formData.synopsis}
                  onChange={(e) => setFormData({ ...formData, synopsis: e.target.value })}
                />
              </div>

              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', margin: '4px 0 0' }}>
                Una película aparece en cartelera automáticamente cuando tiene sesiones programadas.
              </p>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '24px', paddingTop: '16px', borderTop: '1px solid rgba(43,18,21,0.1)' }}>
                <button
                  type="button"
                  className="admin-btn admin-btn-secondary"
                  onClick={() => setModalMode(null)}
                >
                  Cancelar
                </button>
                <button type="submit" className="admin-btn admin-btn-primary" disabled={guardando}>
                  {guardando ? 'Guardando…' : modalMode === 'create' ? 'Guardar Película' : 'Actualizar Cambios'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
