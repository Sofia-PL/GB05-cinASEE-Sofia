import { useState, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { useCine } from '../../context/useCine';
import { useAuth } from '../../context/useAuth';
import { tokenParaLeer } from '../../api/usuarios';
import { obtenerCine, cargarCarteleraCine, nombreGenero } from '../../api/cartelera';
import { Cargando, ErrorCarga, SinDatos } from '../../components/EstadoCarga';
import { generarDias, formatDia } from '../../utils/dias';

export default function CineDetalle() {
  const { cineId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { cines, cargandoCines, setCineSeleccionado } = useCine();
  const { tokenUsuario } = useAuth();
  const tokenCliente = tokenUsuario();

  // Cine: de la lista global o, si aún no está, directamente de Cartelera
  const cineLista = cines.find((c) => c.id === cineId) || null;
  const [cineDirecto, setCineDirecto] = useState({ id: null, cine: null, error: '' });
  const cine = cineLista || (cineDirecto.id === cineId ? cineDirecto.cine : null);

  useEffect(() => {
    if (cineLista || cargandoCines) return undefined;
    let cancelado = false;
    obtenerCine(cineId)
      .then((c) => { if (!cancelado) setCineDirecto({ id: cineId, cine: c, error: '' }); })
      .catch((err) => { if (!cancelado) setCineDirecto({ id: cineId, cine: null, error: err.status === 404 ? 'no-existe' : err.message }); });
    return () => { cancelado = true; };
  }, [cineId, cineLista, cargandoCines]);

  const dias = generarDias();
  const [diaIdx, setDiaIdx] = useState(0);
  const [sesionSeleccionada, setSesionSeleccionada] = useState(null);
  const [recarga, setRecarga] = useState(0);
  const diaClave = dias[diaIdx].toDateString();
  const claveCartelera = `${cineId}|${diaClave}|${recarga}`;

  // Cartelera del día: sesiones a la venta en las salas de este cine
  const [cartelera, setCartelera] = useState({ clave: null, peliculas: [], sesionesPorPelicula: {}, error: '' });

  useEffect(() => {
    let cancelado = false;
    const clave = `${cineId}|${diaClave}|${recarga}`;
    tokenParaLeer(tokenCliente)
      .then((token) => cargarCarteleraCine(cineId, new Date(diaClave), token))
      .then((res) => { if (!cancelado) setCartelera({ clave, ...res, error: '' }); })
      .catch((err) => { if (!cancelado) setCartelera({ clave, peliculas: [], sesionesPorPelicula: {}, error: err.message }); });
    return () => { cancelado = true; };
  }, [cineId, diaClave, tokenCliente, recarga]);

  const cargandoCartelera = cartelera.clave !== claveCartelera;

  // Carrusel con las películas del día
  const carruselPeliculas = cargandoCartelera ? [] : cartelera.peliculas.filter((p) => p.posterUrl);
  const [activeCarousel, setActiveCarousel] = useState(0);
  const peliCarousel = carruselPeliculas[activeCarousel % Math.max(1, carruselPeliculas.length)];

  useEffect(() => {
    if (carruselPeliculas.length < 2) return undefined;
    const timer = window.setInterval(() => {
      setActiveCarousel((idx) => (idx + 1) % carruselPeliculas.length);
    }, 7000);
    return () => window.clearInterval(timer);
  }, [carruselPeliculas.length]);

  // Visitar un cine lo deja elegido para la cartelera y la compra
  useEffect(() => {
    if (cine) setCineSeleccionado(cine);
  }, [cine, setCineSeleccionado]);

  if (!cine) {
    if (cineDirecto.id === cineId && cineDirecto.error) {
      return (
        <section className="placeholder-page">
          <h1>Cine no disponible</h1>
          <p>
            {cineDirecto.error === 'no-existe'
              ? 'No se ha encontrado el cine solicitado.'
              : `No se pudo cargar el cine: ${cineDirecto.error}`}
          </p>
        </section>
      );
    }
    return (
      <section className="placeholder-page">
        <Cargando texto="Cargando cine…" />
      </section>
    );
  }

  function handleComprar(pelicula, sesion) {
    setCineSeleccionado(cine);
    navigate('/compra', {
      state: {
        cineId: cine.id,
        cineName: cine.name,
        peliculaId: pelicula.id,
        peliculaTitle: pelicula.title,
        fecha: sesion.startTime,
        sesion,
      },
    });
  }

  const fechaSeleccionada = dias[diaIdx];
  const peliculaPreselId = searchParams.get('pelicula');
  const infoDia = formatDia(fechaSeleccionada, diaIdx);

  return (
    <div className="detalle-cine-page">
      {/* 1. Cabecera con datos del cine */}
      <header className="detalle-cine-header-panel">
        <div className="detalle-cine-hero-grid">
          <div className="detalle-cine-datos">
            <span className="home-eyebrow">{cine.city}</span>
            <h1 className="detalle-cine-title">{cine.name}</h1>
            <p className="detalle-cine-location">{cine.address}</p>
            {cine.phone && <p className="detalle-cine-desc">Teléfono: {cine.phone}</p>}
          </div>

          <div className="detalle-cine-carousel-wrap">
            <div className="detalle-carousel-inner">
              {peliCarousel?.posterUrl ? (
                <img
                  key={peliCarousel.id}
                  className="detalle-carousel-img"
                  src={peliCarousel.posterUrl}
                  alt={`Póster de ${peliCarousel.title}`}
                />
              ) : (
                <div className="detalle-carousel-placeholder">CinASEE</div>
              )}
              <div className="detalle-carousel-overlay-shadow" />
            </div>

            {peliCarousel && (
              <div className="detalle-carousel-title-wrap">
                <h3 className="detalle-carousel-title-text">{peliCarousel.title}</h3>
              </div>
            )}

            {carruselPeliculas.length > 1 && (
              <>
                <button
                  className="detalle-carousel-control detalle-carousel-prev"
                  type="button"
                  onClick={() => setActiveCarousel((idx) => (idx - 1 + carruselPeliculas.length) % carruselPeliculas.length)}
                  aria-label="Película anterior"
                >
                  ‹
                </button>
                <button
                  className="detalle-carousel-control detalle-carousel-next"
                  type="button"
                  onClick={() => setActiveCarousel((idx) => (idx + 1) % carruselPeliculas.length)}
                  aria-label="Película siguiente"
                >
                  ›
                </button>
                <div className="detalle-carousel-dots">
                  {carruselPeliculas.map((item, index) => (
                    <button
                      key={item.id}
                      type="button"
                      className={index === activeCarousel % carruselPeliculas.length ? 'active' : ''}
                      onClick={() => setActiveCarousel(index)}
                      aria-label={`Mostrar ${item.title}`}
                    />
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      </header>

      {/* 2. Selector de fechas */}
      <section className="detalle-cine-fechas-panel">
        <div className="detalle-cine-container">
          <h2 className="detalle-fechas-subtitulo">Selecciona fecha de proyección</h2>
          <div className="detalle-dias-scroll">
            {dias.map((d, i) => {
              const info = formatDia(d, i);
              return (
                <button
                  key={d.toISOString()}
                  type="button"
                  className={`detalle-dia-pill${diaIdx === i ? ' active' : ''}`}
                  onClick={() => { setDiaIdx(i); setSesionSeleccionada(null); setActiveCarousel(0); }}
                >
                  <span className="detalle-dia-principal">{info.label}</span>
                  <span className="detalle-dia-secundario">{info.sub}</span>
                </button>
              );
            })}
          </div>
        </div>
      </section>

      {/* 3. Películas con sus sesiones */}
      <section className="detalle-cine-cartelera-panel">
        <div className="detalle-cine-container">
          <p className="detalle-cartelera-intro">
            Sesiones para <strong>{infoDia.label} ({infoDia.sub})</strong> en <strong>{cine.name}</strong>
          </p>

          {cargandoCartelera && <Cargando texto="Consultando las sesiones a la venta…" />}
          {!cargandoCartelera && cartelera.error && (
            <ErrorCarga mensaje={`No se pudo cargar la cartelera: ${cartelera.error}`} onReintentar={() => setRecarga((n) => n + 1)} />
          )}
          {!cargandoCartelera && !cartelera.error && cartelera.peliculas.length === 0 && (
            <SinDatos>No hay sesiones a la venta en este cine para este día.</SinDatos>
          )}

          <div className="detalle-peliculas-list">
            {!cargandoCartelera && cartelera.peliculas.map((peli) => {
              const sesiones = cartelera.sesionesPorPelicula[peli.id] || [];
              const isPreselected = String(peli.id) === String(peliculaPreselId);
              const sesActual = sesionSeleccionada?.peliculaId === peli.id ? sesionSeleccionada.sesion : null;
              const durHoras = Math.floor(peli.durationMinutes / 60);
              const durMin = peli.durationMinutes % 60;

              return (
                <article key={peli.id} className={`detalle-peli-card${isPreselected ? ' preselected' : ''}`}>
                  <div className="detalle-peli-row">
                    <div className="detalle-peli-col-info">
                      <div className="detalle-peli-badges">
                        {peli.genre && <span className="cartelera-badge-tag">{nombreGenero(peli.genre)}</span>}
                        {peli.ageRating && <span className="cartelera-badge-tag">{peli.ageRating}</span>}
                        {peli.durationMinutes > 0 && (
                          <span className="cartelera-badge-tag cartelera-badge-tag--dark">{durHoras}h {durMin}m</span>
                        )}
                        {peli.trailerUrl && (
                          <a
                            href={peli.trailerUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="detalle-trailer-btn"
                            title={`Ver tráiler de ${peli.title}`}
                          >
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" aria-hidden="true">
                              <path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4zM4 20V10h16v10H4z"/>
                            </svg>
                            <span>Ver tráiler</span>
                          </a>
                        )}
                      </div>
                      <h2 className="detalle-peli-heading">{peli.title}</h2>
                      <p className="detalle-peli-synopsis-body">{peli.synopsis}</p>
                    </div>

                    <div className="detalle-peli-col-poster">
                      {peli.posterUrl ? (
                        <img className="detalle-peli-fixed-poster" src={peli.posterUrl} alt={`Póster de ${peli.title}`} loading="lazy" />
                      ) : (
                        <div className="detalle-peli-fixed-poster placeholder">{peli.title.charAt(0)}</div>
                      )}
                    </div>
                  </div>

                  <div className="detalle-sesiones-bloque">
                    <h3 className="detalle-sesiones-titulo">Horarios disponibles:</h3>
                    <div className="detalle-sesiones-fila-accion">
                      <div className="detalle-sesiones-grid">
                        {sesiones.map((ses) => {
                          const isSelected = sesActual?.showId === ses.showId;
                          return (
                            <button
                              key={ses.showId}
                              type="button"
                              className={`detalle-hora-card${isSelected ? ' selected' : ''}`}
                              onClick={() => setSesionSeleccionada(isSelected ? null : { peliculaId: peli.id, sesion: ses })}
                            >
                              <span className="detalle-hora-time">{ses.hora}</span>
                              <span className="detalle-hora-meta">
                                {[ses.formato, ses.salaNombre].filter(Boolean).join(', ') || 'Sala de proyección'}
                              </span>
                              <span className="detalle-hora-price">{ses.precio.toFixed(2)} €</span>
                            </button>
                          );
                        })}
                      </div>

                      {sesActual && (
                        <button
                          className="home-button detalle-btn-comprar-inline"
                          type="button"
                          onClick={() => handleComprar(peli, sesActual)}
                        >
                          Comprar entradas
                        </button>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        </div>
      </section>
    </div>
  );
}
