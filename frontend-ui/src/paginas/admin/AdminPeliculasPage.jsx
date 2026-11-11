import { useState, useEffect } from 'react';
import initialPeliculas from '../../assets/peliculas.json';

const LS_PELIS_KEY = 'cinasee_admin_peliculas';

const GENRES = [
  { value: 'ACCION', label: 'Acción' },
  { value: 'CIENCIA_FICCION', label: 'Ciencia Ficción' },
  { value: 'DRAMA', label: 'Drama' },
  { value: 'COMEDIA', label: 'Comedia' },
  { value: 'TERROR', label: 'Terror' },
  { value: 'ANIMACION', label: 'Animación' },
  { value: 'THRILLER', label: 'Thriller' },
  { value: 'ROMANCE', label: 'Romance' },
];

const AGE_RATINGS = ['TP', '+7', '+12', '+16', '+18'];

function loadPeliculas() {
  try {
    const raw = localStorage.getItem(LS_PELIS_KEY);
    if (raw) return JSON.parse(raw);
  } catch {
    // fallback
  }
  return initialPeliculas.map((p) => ({
    ...p,
    trailerUrl: p.trailerUrl,
  }));
}

export default function AdminPeliculasPage() {
  const [peliculas, setPeliculas] = useState(loadPeliculas);
  const [busqueda, setBusqueda] = useState('');
  const [filtroGenero, setFiltroGenero] = useState('TODOS');

  // Estado del modal (null = cerrado, 'create' = nuevo, objeto peli = editar)
  const [modalMode, setModalMode] = useState(null);

  // Formulario reactivo
  const [formData, setFormData] = useState({
    id: null,
    title: '',
    genre: 'ACCION',
    durationMinutes: 120,
    trailerUrl: '',
    posterUrl: '',
    synopsis: '',
    ageRating: '+12',
    inShowcase: false,
  });

  // Persistir en localStorage [Esto ns ]
  useEffect(() => {
    try {
      localStorage.setItem(LS_PELIS_KEY, JSON.stringify(peliculas));
    } catch {
      // ignore
    }
  }, [peliculas]);

  function abrirCrear() {
    setFormData({
      id: null,
      title: '',
      genre: 'ACCION',
      durationMinutes: 120,
      trailerUrl: '',
      posterUrl: '/ljdh.jpe',
      synopsis: '',
      ageRating: '+12',
      inShowcase: false,
    });
    setModalMode('create');
  }

  function abrirEditar(peli) {
    setFormData({
      id: peli.id,
      title: peli.title,
      genre: peli.genre,
      durationMinutes: peli.durationMinutes || 120,
      trailerUrl: peli.trailerUrl || '',
      posterUrl: peli.posterUrl || '',
      synopsis: peli.synopsis || '',
      ageRating: peli.ageRating || '+12',
      inShowcase: !!peli.inShowcase,
    });
    setModalMode('edit');
  }

  function cerrarModal() {
    setModalMode(null);
  }

  function handleSubmit(e) {
    e.preventDefault();
    if (!formData.title.trim()) return;

    if (modalMode === 'create') {
      const nueva = {
        ...formData,
        id: Date.now(),
        durationMinutes: Number(formData.durationMinutes) || 90,
      };
      setPeliculas([nueva, ...peliculas]);
    } else if (modalMode === 'edit') {
      setPeliculas(
        peliculas.map((p) =>
          p.id === formData.id
            ? {
              ...p,
              ...formData,
              durationMinutes: Number(formData.durationMinutes) || 90,
            }
            : p
        )
      );
    }
    cerrarModal();
  }

  function handleDelete(id, titulo) {
    if (window.confirm(`¿Confirmas la eliminación definitiva de "${titulo}" del catálogo central?`)) {
      setPeliculas(peliculas.filter((p) => p.id !== id));
    }
  }

  const peliculasFiltradas = peliculas.filter((p) => {
    const coincideTitulo = p.title.toLowerCase().includes(busqueda.toLowerCase());
    const coincideGenero = filtroGenero === 'TODOS' || p.genre === filtroGenero;
    return coincideTitulo && coincideGenero;
  });

  return (
    <div className="admin-page-container">
      <header className="admin-header">
        <div>
          <h1 className="admin-header-title">Gestión de Catálogo de Películas</h1>
          <p className="admin-header-desc">
            Administración central de títulos, fichas técnicas, tráileres oficiales, pósters y sinopsis.
          </p>
        </div>

        <button className="admin-btn admin-btn-primary" type="button" onClick={abrirCrear}>
          + Añadir Película
        </button>
      </header>

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
              {GENRES.map((g) => (
                <option key={g.value} value={g.value}>{g.label}</option>
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
              <th>Carrusel</th>
              <th style={{ textAlign: 'right' }}>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {peliculasFiltradas.length === 0 ? (
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
                          {peli.title.charAt(0)}
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
                      <span className="admin-badge admin-badge-dark">
                        {GENRES.find((g) => g.value === peli.genre)?.label || peli.genre}
                      </span>
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
                      {peli.inShowcase ? (
                        <span className="admin-badge admin-badge-success">Destacada</span>
                      ) : (
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Estándar</span>
                      )}
                    </td>

                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <button
                        className="admin-btn admin-btn-secondary admin-btn-sm"
                        type="button"
                        onClick={() => abrirEditar(peli)}
                        style={{ marginRight: '6px' }}
                      >
                        Editar
                      </button>
                      <button
                        className="admin-btn admin-btn-danger admin-btn-sm"
                        type="button"
                        onClick={() => handleDelete(peli.id, peli.title)}
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
        <div className="admin-modal-backdrop" onClick={cerrarModal}>
          <div className="admin-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal-header">
              <h2 className="admin-modal-title">
                {modalMode === 'create' ? 'Nueva Película' : `Modificar: ${formData.title}`}
              </h2>
              <button
                type="button"
                className="admin-btn admin-btn-secondary admin-btn-sm"
                onClick={cerrarModal}
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
                  <label className="admin-label">Género Cinematográfico</label>
                  <select
                    className="admin-select"
                    value={formData.genre}
                    onChange={(e) => setFormData({ ...formData, genre: e.target.value })}
                  >
                    {GENRES.map((g) => (
                      <option key={g.value} value={g.value}>{g.label}</option>
                    ))}
                  </select>
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Duración en Minutos *</label>
                  <input
                    type="number"
                    className="admin-input"
                    required
                    min={30}
                    max={400}
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
                    placeholder="/ljdh.jpe o https://..."
                    value={formData.posterUrl}
                    onChange={(e) => setFormData({ ...formData, posterUrl: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Clasificación por Edad</label>
                  <select
                    className="admin-select"
                    value={formData.ageRating}
                    onChange={(e) => setFormData({ ...formData, ageRating: e.target.value })}
                  >
                    {AGE_RATINGS.map((r) => (
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
                <label className="admin-label">Sinopsis Argumental *</label>
                <textarea
                  className="admin-textarea"
                  required
                  rows={4}
                  placeholder="Escribe la descripción argumental y sinopsis de la obra..."
                  value={formData.synopsis}
                  onChange={(e) => setFormData({ ...formData, synopsis: e.target.value })}
                />
              </div>

              <div className="admin-form-group" style={{ flexDirection: 'row', alignItems: 'center', gap: '10px', marginTop: '6px' }}>
                <input
                  type="checkbox"
                  id="inShowcase"
                  checked={formData.inShowcase}
                  onChange={(e) => setFormData({ ...formData, inShowcase: e.target.checked })}
                  style={{ width: '18px', height: '18px', cursor: 'pointer' }}
                />
                <label htmlFor="inShowcase" style={{ cursor: 'pointer', fontSize: '0.88rem', fontWeight: 600 }}>
                  Destacar en el carrusel principal de la web
                </label>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '24px', paddingTop: '16px', borderTop: '1px solid rgba(43,18,21,0.1)' }}>
                <button
                  type="button"
                  className="admin-btn admin-btn-secondary"
                  onClick={cerrarModal}
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="admin-btn admin-btn-primary"
                >
                  {modalMode === 'create' ? 'Guardar Película' : 'Actualizar Cambios'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
