import { useState, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import cines from '../../assets/cines.json';
import peliculas from '../../assets/peliculas.json';
import { useCine } from '../../context/CineContext';

function generarSesiones(peliculaId, cineId, fecha) {
  const seed = (Number(peliculaId) * 11 + Number(cineId) * 7 + fecha.getDate() + fecha.getMonth() * 31) % 100;
  const catalogoHorarios = [
    { hora: '12:30', sala: 1, formato: '2D',   precio: 8.50 },
    { hora: '16:00', sala: 2, formato: '2D',   precio: 9.50 },
    { hora: '18:15', sala: 3, formato: '3D',   precio: 11.50 },
    { hora: '20:30', sala: 4, formato: 'VIP',  precio: 15.00 },
    { hora: '22:15', sala: 5, formato: 'IMAX', precio: 14.00 },
  ];

  return catalogoHorarios.filter((_, i) => (seed + i) % 4 !== 0 || i < 3).map((s) => ({
    ...s,
    asientosLibres: ((seed * (s.sala + 2)) % 45) + 12,
  }));
}

function generarDias() {
  const dias = [];
  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);
  for (let i = 0; i < 7; i++) {
    const d = new Date(hoy);
    d.setDate(hoy.getDate() + i);
    dias.push(d);
  }
  return dias;
}

const DIAS_SEMANA = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

function formatDia(d, index) {
  if (index === 0) return { label: 'Hoy', sub: `${d.getDate()} ${MESES[d.getMonth()]}` };
  if (index === 1) return { label: 'Mañana', sub: `${d.getDate()} ${MESES[d.getMonth()]}` };
  return { label: DIAS_SEMANA[d.getDay()], sub: `${d.getDate()} ${MESES[d.getMonth()]}` };
}

export default function CineDetalle() {
  const { cineId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { setCineSeleccionado } = useCine();

  const cine = cines.find((item) => String(item.id) === cineId);
  const dias = generarDias();
  const [diaIdx, setDiaIdx] = useState(0);
  const [sesionSeleccionada, setSesionSeleccionada] = useState(null);

  // Carrusel de películas en la cabecera del cine
  const carruselPeliculas = peliculas.filter((p) => p.inShowcase);
  const [activeCarousel, setActiveCarousel] = useState(0);

  useEffect(() => {
    if (carruselPeliculas.length < 2) return undefined;
    const timer = window.setInterval(() => {
      setActiveCarousel((idx) => (idx + 1) % carruselPeliculas.length);
    }, 7000);
    return () => window.clearInterval(timer);
  }, [carruselPeliculas.length]);

  function prevCarousel() {
    setActiveCarousel((idx) => (idx - 1 + carruselPeliculas.length) % carruselPeliculas.length);
  }

  function nextCarousel() {
    setActiveCarousel((idx) => (idx + 1) % carruselPeliculas.length);
  }

  const peliCarousel = carruselPeliculas[activeCarousel];

  useEffect(() => {
    if (cine) {
      setCineSeleccionado(cine);
    }
  }, [cine, setCineSeleccionado]);

  if (!cine) {
    return (
      <section className="placeholder-page">
        <h1>Cine no disponible</h1>
        <p>No se ha encontrado la información correspondiente al cine solicitado.</p>
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
        fecha: dias[diaIdx].toISOString(),
        sesion,
      },
    });
  }

  const fechaSeleccionada = dias[diaIdx];
  const peliculaPreselId = searchParams.get('pelicula');

  return (
    <div className="detalle-cine-page">
      {/* 1. Cabecera con datos del cine y foto integrada (sin botón volver) */}
      <header className="detalle-cine-header-panel">
        <div className="detalle-cine-hero-grid">
          <div className="detalle-cine-datos">
            <span className="home-eyebrow">{cine.city}, {cine.province}</span>
            <h1 className="detalle-cine-title">{cine.name}</h1>
            <p className="detalle-cine-location">
              {cine.address}, {cine.postalCode}
            </p>
            <p className="detalle-cine-desc">
              Espacio audiovisual dotado de {cine.rooms} salas de proyección digital, climatización acústica y accesibilidad total.
            </p>
            <div className="detalle-cine-format-list">
              <span className="cine-format-pill">{cine.rooms} salas</span>
              {cine.formats.map((f) => (
                <span key={f} className="cine-format-pill">{f}</span>
              ))}
            </div>
          </div>

          {/* Carrusel de películas en la cabecera: ocupa la mitad de pantalla con título abajo a la derecha saliendo por debajo */}
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

            {/* Título abajo a la derecha: solo las letras, sin recuadro ni badge */}
            <div className="detalle-carousel-title-wrap">
              <h3 className="detalle-carousel-title-text">{peliCarousel?.title}</h3>
            </div>

            {/* Controles del carrusel */}
            {carruselPeliculas.length > 1 && (
              <>
                <button
                  className="detalle-carousel-control detalle-carousel-prev"
                  type="button"
                  onClick={prevCarousel}
                  aria-label="Película anterior"
                >
                  ‹
                </button>
                <button
                  className="detalle-carousel-control detalle-carousel-next"
                  type="button"
                  onClick={nextCarousel}
                  aria-label="Película siguiente"
                >
                  ›
                </button>
                <div className="detalle-carousel-dots">
                  {carruselPeliculas.map((item, index) => (
                    <button
                      key={item.id}
                      type="button"
                      className={index === activeCarousel ? 'active' : ''}
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
          <h2 className="detalle-fechas-subtitulo">
            Selecciona fecha de proyección
          </h2>
          <div className="detalle-dias-scroll">
            {dias.map((d, i) => {
              const info = formatDia(d, i);
              const activo = diaIdx === i;
              return (
                <button
                  key={d.toISOString()}
                  type="button"
                  className={`detalle-dia-pill${activo ? ' active' : ''}`}
                  onClick={() => { setDiaIdx(i); setSesionSeleccionada(null); }}
                >
                  <span className="detalle-dia-principal">{info.label}</span>
                  <span className="detalle-dia-secundario">{info.sub}</span>
                </button>
              );
            })}
          </div>
        </div>
      </section>

      {/* 3. Películas: Info a la izquierda, póster a la derecha y sesiones debajo */}
      <section className="detalle-cine-cartelera-panel">
        <div className="detalle-cine-container">
          <p className="detalle-cartelera-intro">
            Sesiones para <strong>{formatDia(fechaSeleccionada, diaIdx).label} ({formatDia(fechaSeleccionada, diaIdx).sub})</strong> en <strong>{cine.name}</strong>
          </p>

          <div className="detalle-peliculas-list">
            {peliculas.map((peli) => {
              const sesiones = generarSesiones(peli.id, cine.id, fechaSeleccionada);
              const isPreselected = String(peli.id) === String(peliculaPreselId);
              const sesActual = sesionSeleccionada?.peliculaId === peli.id ? sesionSeleccionada.sesion : null;
              const durHoras = Math.floor(peli.durationMinutes / 60);
              const durMin = peli.durationMinutes % 60;

              return (
                <article
                  key={peli.id}
                  className={`detalle-peli-card${isPreselected ? ' preselected' : ''}`}
                >
                  {/* Fila superior: Izquierda texto / Derecha imagen fija */}
                  <div className="detalle-peli-row">
                    <div className="detalle-peli-col-info">
                      <div className="detalle-peli-badges">
                        <span className="cartelera-badge-tag">{peli.genre.replace('_', ' ')}</span>
                        <span className="cartelera-badge-tag">{peli.ageRating}</span>
                        <span className="cartelera-badge-tag cartelera-badge-tag--dark">
                          {durHoras}h {durMin}m
                        </span>
                      </div>
                      <h2 className="detalle-peli-heading">{peli.title}</h2>
                      <p className="detalle-peli-synopsis-body">{peli.synopsis}</p>
                    </div>

                    <div className="detalle-peli-col-poster">
                      {peli.posterUrl ? (
                        <img
                          className="detalle-peli-fixed-poster"
                          src={peli.posterUrl}
                          alt={`Póster de ${peli.title}`}
                          loading="lazy"
                        />
                      ) : (
                        <div className="detalle-peli-fixed-poster placeholder">
                          {peli.title.charAt(0)}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Fila inferior: Horarios y sesiones */}
                  <div className="detalle-sesiones-bloque">
                    <h3 className="detalle-sesiones-titulo">Horarios disponibles:</h3>
                    <div className="detalle-sesiones-fila-accion">
                      <div className="detalle-sesiones-grid">
                        {sesiones.map((ses) => {
                          const isSelected = sesActual?.hora === ses.hora && sesActual?.sala === ses.sala;
                          return (
                            <button
                              key={`${ses.hora}-${ses.sala}`}
                              type="button"
                              className={`detalle-hora-card${isSelected ? ' selected' : ''}`}
                              onClick={() =>
                                setSesionSeleccionada(
                                  isSelected ? null : { peliculaId: peli.id, sesion: ses }
                                )
                              }
                            >
                              <span className="detalle-hora-time">{ses.hora}</span>
                              <span className="detalle-hora-meta">{ses.formato} · Sala {ses.sala}</span>
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
