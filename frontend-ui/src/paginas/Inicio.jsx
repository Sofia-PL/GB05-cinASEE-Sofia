import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { obtenerPeliculasEnCartelera, adaptarPelicula } from '../api/cartelera';
import { useCine } from '../context/useCine';
import { Cargando, ErrorCarga, SinDatos } from '../components/EstadoCarga';

export default function Inicio() {
  const navigate = useNavigate();
  const { cines, cargandoCines, errorCines, recargarCines } = useCine();

  // Carrusel: películas con sesiones programadas (Cartelera, ?inShowcase=true)
  const [destacadas, setDestacadas] = useState([]);
  const [activeMovie, setActiveMovie] = useState(0);
  const movie = destacadas[activeMovie] || null;

  // Estado del botón flotante de compra rápida
  const [modalAbierto, setModalAbierto] = useState(false);
  const [busqueda, setBusqueda] = useState('');

  useEffect(() => {
    let cancelado = false;
    obtenerPeliculasEnCartelera()
      .then((lista) => {
        if (!cancelado) setDestacadas((Array.isArray(lista) ? lista : []).map(adaptarPelicula));
      })
      .catch(() => {
        // sin carrusel: la cabecera muestra el nombre de la marca
      });
    return () => { cancelado = true; };
  }, []);

  useEffect(() => {
    if (destacadas.length < 2) return undefined;
    const timer = window.setInterval(() => {
      setActiveMovie((index) => (index + 1) % destacadas.length);
    }, 5000);
    return () => window.clearInterval(timer);
  }, [destacadas.length]);

  const texto = busqueda.toLowerCase();
  const cinesFiltrados = cines.filter((c) =>
    (c.name || '').toLowerCase().includes(texto) || (c.city || '').toLowerCase().includes(texto)
  );

  function previousMovie() {
    setActiveMovie((index) => (index - 1 + destacadas.length) % destacadas.length);
  }

  function nextMovie() {
    setActiveMovie((index) => (index + 1) % destacadas.length);
  }

  function handleSeleccionarCine(cineId) {
    setModalAbierto(false);
    navigate(`/cines/${cineId}`);
  }

  return (
    <div className="home-page">
      {/* Carrusel de cabecera */}
      <section className="home-carousel" aria-label="Películas en cartelera">
        {movie?.posterUrl ? (
          <img key={movie.id} className="home-carousel-image" src={movie.posterUrl} alt={`Cartel de ${movie.title}`} />
        ) : (
          <div className="home-carousel-image home-carousel-image-placeholder">CinASEE</div>
        )}
        <div key={movie?.id} className="home-carousel-overlay">
          <h1>{movie?.title || 'CinASEE'}</h1>
          <div className="home-carousel-actions">
            <Link className="home-button" to="/cines">Encuentra tu cine</Link>
            {movie?.trailerUrl && (
              <a
                className="home-button home-button-trailer"
                href={movie.trailerUrl}
                target="_blank"
                rel="noopener noreferrer"
                title={`Ver tráiler de ${movie.title}`}
              >
                <svg className="icono-pelicula" viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
                  <path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4zM4 20V10h16v10H4z"/>
                </svg>
                <span>Ver tráiler</span>
              </a>
            )}
          </div>
        </div>
        {destacadas.length > 1 && (
          <>
            <button className="home-carousel-control home-carousel-previous" type="button" onClick={previousMovie} aria-label="Película anterior">‹</button>
            <button className="home-carousel-control home-carousel-next" type="button" onClick={nextMovie} aria-label="Película siguiente">›</button>
            <div className="home-carousel-dots">
              {destacadas.map((item, index) => (
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

      {/* Listado de cines */}
      <section className="home-cinemas-section">
        <p className="home-eyebrow">CinASEE España</p>
        <h2>Nuestros cines</h2>

        <div className="cines-preview">
          {cargandoCines && <Cargando texto="Cargando cines…" />}
          {errorCines && <ErrorCarga mensaje={`No se pudieron cargar los cines: ${errorCines}`} onReintentar={recargarCines} />}
          {!cargandoCines && !errorCines && cines.length === 0 && <SinDatos>Todavía no hay cines dados de alta.</SinDatos>}

          <div className="cines-grid">
            {cines.map((cine) => (
              <article className="cine-card" key={cine.id}>
                <div className="cine-card-header">
                  <span className="cine-card-city">{cine.city}</span>
                </div>
                <h3 className="cine-card-title">{cine.name}</h3>
                <p className="cine-card-address">{cine.address}</p>
                {cine.phone && <p className="cine-card-address">Tel. {cine.phone}</p>}
                <Link className="home-button home-button-dark cine-card-btn" to={`/cines/${cine.id}`}>
                  Ver sesiones
                </Link>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* Botón flotante para comprar entradas rápido */}
      {!modalAbierto && cines.length > 0 && (
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

      {/* Modal flotante con buscador de cines */}
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
                    <span className="flotante-cine-action">Seleccionar</span>
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
