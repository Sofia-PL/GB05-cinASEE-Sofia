import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import peliculas from '../assets/peliculas.json';
import cines from '../assets/cines.json';

export default function Inicio() {
  const navigate = useNavigate();
  const availableMovies = peliculas.filter((movie) => movie.inShowcase);
  const [activeMovie, setActiveMovie] = useState(0);
  const movie = availableMovies[activeMovie];

  // Estado del botón flotante de compra rápida
  const [modalAbierto, setModalAbierto] = useState(false);
  const [busqueda, setBusqueda] = useState('');

  const cinesFiltrados = cines.filter((c) =>
    c.name.toLowerCase().includes(busqueda.toLowerCase()) ||
    c.city.toLowerCase().includes(busqueda.toLowerCase()) ||
    c.province.toLowerCase().includes(busqueda.toLowerCase())
  );

  useEffect(() => {
    if (availableMovies.length < 2) {
      return undefined;
    }

    const timer = window.setInterval(() => {
      setActiveMovie((index) => (index + 1) % availableMovies.length);
    }, 8000);

    return () => window.clearInterval(timer);
  }, [availableMovies.length]);

  function previousMovie() {
    setActiveMovie((index) => (index - 1 + availableMovies.length) % availableMovies.length);
  }

  function nextMovie() {
    setActiveMovie((index) => (index + 1) % availableMovies.length);
  }

  function handleSeleccionarCine(cineId) {
    setModalAbierto(false);
    navigate(`/cines/${cineId}`);
  }

  return (
    <div className="home-page">
      {/* Carrusel de cabecera */}
      <section className="home-carousel" aria-label="Películas destacadas">
        {movie?.posterUrl ? (
          <img key={movie.id} className="home-carousel-image" src={movie.posterUrl} alt={`Cartel de ${movie.title}`} />
        ) : (
          <div className="home-carousel-image home-carousel-image-placeholder">CinASEE</div>
        )}
        <div key={movie?.id} className="home-carousel-overlay">
          <h1>{movie?.title || 'Películas disponibles'}</h1>
          <Link className="home-button" to="/cines">Encuentra tu cine</Link>
        </div>
        {availableMovies.length > 1 && (
          <>
            <button className="home-carousel-control home-carousel-previous" type="button" onClick={previousMovie} aria-label="Película anterior">‹</button>
            <button className="home-carousel-control home-carousel-next" type="button" onClick={nextMovie} aria-label="Película siguiente">›</button>
            <div className="home-carousel-dots">
              {availableMovies.map((item, index) => (
                <button
                  className={index === activeMovie ? 'active' : ''}
                  key={item.id}
                  type="button"
                  onClick={() => setActiveMovie(index)}
                  aria-label={`Mostrar ${item.title}`}
                />
              ))}
            </div>
          </>
        )}
      </section>

      {/* Listado de cines en el inicio con diseño actual y armónico */}
      {/* Listado de cines en el inicio con avance difuminado y botón "Ver más cines" */}
      <section className="home-cinemas-section">
        <p className="home-eyebrow">CinASEE España</p>
        <h2>Nuestros cines</h2>
        <p className="home-section-description">
          Salas equipadas con la última tecnología audiovisual para que vivas el cine al máximo nivel.
        </p>

        <div className="cines-preview">
          <div className="cines-grid">
            {cines.map((cine) => (
              <article className="cine-card" key={cine.id}>
                <div className="cine-card-header">
                  <span className="cine-card-city">{cine.city}</span>
                  <span className="cine-card-rooms">{cine.rooms} salas</span>
                </div>
                <h3 className="cine-card-title">{cine.name}</h3>
                <p className="cine-card-address">{cine.address}, {cine.postalCode}</p>
                <div className="cine-card-formats">
                  {cine.formats.map((f) => (
                    <span key={f} className="cine-format-pill">{f}</span>
                  ))}
                </div>
                <Link className="home-button home-button-dark cine-card-btn" to={`/cines/${cine.id}`}>
                  Ver sesiones
                </Link>
              </article>
            ))}
          </div>

          <Link className="home-button" to="/cines">
            Ver más cines
          </Link>
        </div>
      </section>

      {/* Sección de Noticias y Novedades Cinematográficas */}
      <section className="home-news-section">
        <div className="home-news-header">
          <p className="home-eyebrow">Actualidad y Estrenos</p>
          <h2>Últimas noticias</h2>
          <p className="home-section-description">
            Descubre los próximos lanzamientos, tecnologías de sala y novedades del universo CinASEE.
          </p>
        </div>

        <div className="home-news-grid">
          <article className="home-news-card">
            <div className="home-news-meta">
              <span className="home-news-tag">Tecnología</span>
              <time className="home-news-date">10 Nov 2026</time>
            </div>
            <h3 className="home-news-title">
              Inauguración de la nueva Sala IMAX Láser con sonido inmersivo 12 canales
            </h3>
            <p className="home-news-excerpt">
              CinASEE Gran Vía estrena una pantalla de más de 20 metros de ancho equipada con proyección láser dual 4K y acústica envolvente de última generación.
            </p>
            <span className="home-news-link">Leer artículo completo →</span>
          </article>

          <article className="home-news-card">
            <div className="home-news-meta">
              <span className="home-news-tag">Ciclos de cine</span>
              <time className="home-news-date">6 Nov 2026</time>
            </div>
            <h3 className="home-news-title">
              Retrospectiva de Clásicos de Ciencia Ficción restaurados en 4K
            </h3>
            <p className="home-news-excerpt">
              Durante todo el mes proyectaremos títulos legendarios remasterizados digitalmente, con pases especiales comentados por directores y críticos invitados.
            </p>
            <span className="home-news-link">Ver programación →</span>
          </article>

          <article className="home-news-card">
            <div className="home-news-meta">
              <span className="home-news-tag">Promociones</span>
              <time className="home-news-date">1 Nov 2026</time>
            </div>
            <h3 className="home-news-title">
              Miércoles de Cine y ventajas exclusivas para usuarios registrados
            </h3>
            <p className="home-news-excerpt">
              Disfruta de entradas a tarifa reducida todos los miércoles en cualquiera de nuestros cines y acumula puntos con tu cuenta digital en cada visita.
            </p>
            <span className="home-news-link">Más información →</span>
          </article>
        </div>
      </section>

      {/* Botón flotante para comprar entradas rápido */}
      {!modalAbierto && (
        <button
          type="button"
          className="boton-flotante-compra"
          onClick={() => setModalAbierto(true)}
          aria-label="Comprar entradas de forma rápida"
        >
          <span className="boton-flotante-icono">+</span>
          <span className="boton-flotante-texto">Comprar entradas</span>
        </button>
      )}

      {/* Modal flotante expandido con buscador de cines */}
      {modalAbierto && (
        <div className="flotante-overlay" onClick={() => setModalAbierto(false)}>
          <div
            className="flotante-panel"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-label="Selector rápido de cine"
          >
            <div className="flotante-panel-header">
              <div>
                <p className="home-eyebrow" style={{ margin: 0 }}>Paso directo a cartelera</p>
                <h2 className="flotante-panel-title">Elige tu cine</h2>
              </div>
              <button
                type="button"
                className="flotante-cerrar-btn"
                onClick={() => setModalAbierto(false)}
                aria-label="Cerrar buscador"
              >
                ×
              </button>
            </div>

            <div className="flotante-buscador-wrap">
              <input
                type="text"
                className="flotante-input"
                placeholder="Escribe el nombre del cine o tu ciudad..."
                value={busqueda}
                onChange={(e) => setBusqueda(e.target.value)}
                autoFocus
              />
            </div>

            <div className="flotante-lista-cines">
              {cinesFiltrados.length === 0 ? (
                <p className="flotante-vacio">No se han encontrado cines para "{busqueda}"</p>
              ) : (
                cinesFiltrados.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    className="flotante-cine-item"
                    onClick={() => handleSeleccionarCine(c.id)}
                  >
                    <div className="flotante-cine-info">
                      <span className="flotante-cine-city">{c.city}</span>
                      <strong className="flotante-cine-name">{c.name}</strong>
                      <span className="flotante-cine-address">{c.address}</span>
                    </div>
                    <span className="flotante-cine-action">Seleccionar →</span>
                  </button>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
