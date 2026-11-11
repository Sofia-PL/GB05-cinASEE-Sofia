import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import peliculas from '../../assets/peliculas.json';
import { useCine } from '../../context/CineContext';

const GENRE_LABELS = {
  ACCION: 'Acción',
  CIENCIA_FICCION: 'Ciencia Ficción',
  DRAMA: 'Drama',
  COMEDIA: 'Comedia',
  TERROR: 'Terror',
  ANIMACION: 'Animación',
  THRILLER: 'Thriller',
  ROMANCE: 'Romance',
};

export default function CarteleraPage() {
  const navigate = useNavigate();
  const { cineSeleccionado } = useCine();
  const [hoveredId, setHoveredId] = useState(null);

  function handleComprar(peliculaId) {
    if (cineSeleccionado) {
      navigate(`/cines/${cineSeleccionado.id}?pelicula=${peliculaId}`);
    } else {
      navigate('/cines', { state: { peliculaId } });
    }
  }

  return (
    <div className="cartelera-page">
      <header className="cartelera-header">
        <h1>Cartelera</h1>
        <p className="cartelera-subtitle">
          Pasa el ratón sobre cualquier película para consultar su sinopsis y comprar entradas
        </p>
        {cineSeleccionado && (
          <div className="cartelera-cine-badge">
            <span>Cine seleccionado: <strong>{cineSeleccionado.name}</strong></span>
            <button
              type="button"
              className="cartelera-cambiar-cine-btn"
              onClick={() => navigate('/cines')}
            >
              Cambiar
            </button>
          </div>
        )}
      </header>

      <div className="cartelera-grid">
        {peliculas.map((movie) => {
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
              {/* Contenedor de póster con tamaño uniforme estricto */}
              <div className="cartelera-poster-container">
                {movie.posterUrl ? (
                  <img
                    className="cartelera-poster-img"
                    src={movie.posterUrl}
                    alt={`Cartel de ${movie.title}`}
                    loading="lazy"
                  />
                ) : (
                  <div className="cartelera-poster-placeholder">
                    {movie.title.charAt(0)}
                  </div>
                )}
                <div className="cartelera-poster-tags">
                  <span className="cartelera-badge-tag">{movie.ageRating}</span>
                  <span className="cartelera-badge-tag cartelera-badge-tag--dark">
                    {durHoras}h {durMin}m
                  </span>
                </div>
              </div>

              {/* Información de la película */}
              <div className="cartelera-content">
                <div className="cartelera-meta-header">
                  <span className="cartelera-genre-label">
                    {GENRE_LABELS[movie.genre] || movie.genre}
                  </span>
                  <span className={`cartelera-plus-icon${isHovered ? ' active' : ''}`} aria-hidden="true">
                    +
                  </span>
                </div>

                <h2 className="cartelera-movie-title">{movie.title}</h2>

                {/* Sinopsis expandible con hover */}
                <div className={`cartelera-hover-panel${isHovered ? ' visible' : ''}`}>
                  <p className="cartelera-synopsis-text">{movie.synopsis}</p>
                  <button
                    className="home-button cartelera-buy-btn"
                    type="button"
                    onClick={() => handleComprar(movie.id)}
                  >
                    {cineSeleccionado
                      ? `Comprar en ${cineSeleccionado.name}`
                      : 'Comprar entradas'}
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
