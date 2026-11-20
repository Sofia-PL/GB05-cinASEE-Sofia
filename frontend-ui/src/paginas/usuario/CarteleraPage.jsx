import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { obtenerPeliculasEnCartelera, adaptarPelicula, nombreGenero } from '../../api/cartelera';
import { Cargando, ErrorCarga, SinDatos } from '../../components/EstadoCarga';

export default function CarteleraPage() {
  const navigate = useNavigate();
  const [hoveredId, setHoveredId] = useState(null);

  // Películas con sesiones programadas en Cartelera (Comp. 1)
  const [catalogo, setCatalogo] = useState({ cargando: true, error: '', lista: [] });
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let cancelado = false;
    obtenerPeliculasEnCartelera()
      .then((lista) => {
        if (!cancelado) setCatalogo({ cargando: false, error: '', lista: (Array.isArray(lista) ? lista : []).map(adaptarPelicula) });
      })
      .catch((err) => {
        if (!cancelado) setCatalogo({ cargando: false, error: err.message, lista: [] });
      });
    return () => { cancelado = true; };
  }, [recarga]);

  function handleComprar(peliculaId) {
    navigate(`/peliculas/${peliculaId}`);
  }

  return (
    <div className="cartelera-page">
      <header className="cartelera-header">
        <h1>Cartelera</h1>
        <p className="cartelera-subtitle">
          Pasa el ratón sobre cualquier película para consultar su sinopsis y comprar entradas
        </p>
      </header>

      {catalogo.cargando && <Cargando texto="Cargando cartelera…" />}
      {catalogo.error && (
        <ErrorCarga
          mensaje={`No se pudo cargar la cartelera: ${catalogo.error}`}
          onReintentar={() => { setCatalogo((c) => ({ ...c, cargando: true, error: '' })); setRecarga((n) => n + 1); }}
        />
      )}
      {!catalogo.cargando && !catalogo.error && catalogo.lista.length === 0 && (
        <SinDatos>Ahora mismo no hay películas con sesiones programadas.</SinDatos>
      )}

      <div className="cartelera-grid">
        {catalogo.lista.map((movie) => {
          const isHovered = hoveredId === movie.id;
          const durHoras = Math.floor(movie.durationMinutes / 60);
          const durMin = movie.durationMinutes % 60;

          return (
            <article
              key={movie.id}
              className={`cartelera-card${isHovered ? ' cartelera-card--expanded' : ''}`}
              onMouseEnter={() => setHoveredId(movie.id)}
              onMouseLeave={() => setHoveredId(null)}
            >
              <div className="cartelera-poster-container">
                {movie.posterUrl ? (
                  <img className="cartelera-poster-img" src={movie.posterUrl} alt={`Cartel de ${movie.title}`} loading="lazy" />
                ) : (
                  <div className="cartelera-poster-placeholder">{movie.title.charAt(0)}</div>
                )}
                <div className="cartelera-poster-tags">
                  {movie.ageRating && <span className="cartelera-badge-tag">{movie.ageRating}</span>}
                  {movie.durationMinutes > 0 && (
                    <span className="cartelera-badge-tag cartelera-badge-tag--dark">{durHoras}h {durMin}m</span>
                  )}
                </div>
              </div>

              <div className="cartelera-content">
                <div className="cartelera-meta-header">
                  <span className="cartelera-genre-label">{nombreGenero(movie.genre)}</span>
                  <div className="cartelera-header-actions">
                    {movie.trailerUrl && (
                      <a
                        href={movie.trailerUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="cartelera-trailer-btn"
                        title={`Ver tráiler de ${movie.title}`}
                        onClick={(e) => e.stopPropagation()}
                        aria-label={`Ver tráiler de ${movie.title}`}
                      >
                        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true">
                          <path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4zM4 20V10h16v10H4z"/>
                        </svg>
                      </a>
                    )}
                    <span className={`cartelera-plus-icon${isHovered ? ' active' : ''}`} aria-hidden="true">+</span>
                  </div>
                </div>

                <h2 className="cartelera-movie-title">{movie.title}</h2>

                <div className={`cartelera-hover-panel${isHovered ? ' visible' : ''}`}>
                  <p className="cartelera-synopsis-text">{movie.synopsis}</p>
                  <button className="home-button cartelera-buy-btn" type="button" onClick={() => handleComprar(movie.id)}>
                    Comprar entradas
                  </button>
                </div>
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}
