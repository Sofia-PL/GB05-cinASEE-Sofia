import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useCine } from '../../context/useCine';
import { useAuth } from '../../context/useAuth';
import { tokenParaLeer } from '../../api/usuarios';
import { obtenerPelicula, adaptarPelicula, cargarSesionesPelicula, nombreGenero, fechaIso } from '../../api/cartelera';
import { Cargando, ErrorCarga, SinDatos } from '../../components/EstadoCarga';
import { generarDias, formatDia } from '../../utils/dias';

/* =========================================================
   Ficha de película (compra por película)
   ---------------------------------------------------------
   Cartelera -> esta ficha -> día -> pase (agrupados por cine) -> /compra.
   Datos de Cartelera (Comp. 1): la película, sus sesiones y las salas
   (para saber a qué cine pertenece cada pase).
   ========================================================= */

export default function PeliculaDetalle() {
  const { peliculaId } = useParams();
  const navigate = useNavigate();
  const { cines, cineSeleccionado, setCineSeleccionado } = useCine();
  const { tokenUsuario } = useAuth();
  const tokenCliente = tokenUsuario();

  const [recarga, setRecarga] = useState(0);
  const clave = `${peliculaId}|${recarga}`;
  const [datos, setDatos] = useState({ clave: null, pelicula: null, pases: [], error: '', noExiste: false });

  useEffect(() => {
    let cancelado = false;
    const claveCarga = `${peliculaId}|${recarga}`;
    Promise.all([
      obtenerPelicula(peliculaId),
      tokenParaLeer(tokenCliente).then((token) => cargarSesionesPelicula(peliculaId, token)),
    ])
      .then(([pelicula, pases]) => {
        if (!cancelado) setDatos({ clave: claveCarga, pelicula: adaptarPelicula(pelicula), pases, error: '', noExiste: false });
      })
      .catch((err) => {
        if (!cancelado) setDatos({ clave: claveCarga, pelicula: null, pases: [], error: err.message, noExiste: err.status === 404 });
      });
    return () => { cancelado = true; };
  }, [peliculaId, tokenCliente, recarga]);

  const cargando = datos.clave !== clave;

  // Días: hoy + 6. Por defecto, el primero que tenga pases.
  const dias = generarDias();
  const diasConPases = new Set(datos.pases.map((p) => p.dia));
  const [diaElegido, setDiaElegido] = useState(null);
  const primerDiaConPases = dias.findIndex((d) => diasConPases.has(fechaIso(d)));
  const diaIdx = diaElegido ?? Math.max(0, primerDiaConPases);
  const diaIsoActual = fechaIso(dias[diaIdx]);
  const infoDia = formatDia(dias[diaIdx], diaIdx);

  const [seleccion, setSeleccion] = useState(null); // pase elegido

  if (cargando) {
    return (
      <section className="placeholder-page">
        <Cargando texto="Cargando película…" />
      </section>
    );
  }

  if (!datos.pelicula) {
    return (
      <section className="placeholder-page">
        <h1>Película no disponible</h1>
        {datos.noExiste ? (
          <p>No se ha encontrado la película solicitada.</p>
        ) : (
          <ErrorCarga mensaje={`No se pudo cargar la película: ${datos.error}`} onReintentar={() => setRecarga((n) => n + 1)} />
        )}
        <Link to="/cartelera" className="home-button">Volver a la cartelera</Link>
      </section>
    );
  }

  const peli = datos.pelicula;
  const durHoras = Math.floor(peli.durationMinutes / 60);
  const durMin = peli.durationMinutes % 60;

  // Pases del día agrupados por cine; el cine que el usuario tenga elegido va primero
  const pasesDelDia = datos.pases.filter((p) => p.dia === diaIsoActual);
  const porCine = new Map();
  pasesDelDia.forEach((p) => {
    if (!porCine.has(p.cinemaId)) porCine.set(p.cinemaId, []);
    porCine.get(p.cinemaId).push(p);
  });
  const grupos = Array.from(porCine.entries())
    .map(([cinemaId, pases]) => ({ cine: cines.find((c) => c.id === cinemaId) || null, cinemaId, pases }))
    .sort((a, b) => {
      if (a.cinemaId === cineSeleccionado?.id) return -1;
      if (b.cinemaId === cineSeleccionado?.id) return 1;
      return (a.cine?.name || '').localeCompare(b.cine?.name || '');
    });

  function handleComprar(grupo, pase) {
    if (grupo.cine) setCineSeleccionado(grupo.cine);
    navigate('/compra', {
      state: {
        cineId: grupo.cinemaId,
        cineName: grupo.cine?.name || '',
        peliculaId: peli.id,
        peliculaTitle: peli.title,
        fecha: pase.startTime,
        sesion: pase,
      },
    });
  }

  return (
    <div className="detalle-cine-page">
      {/* 1. Datos de la película */}
      <section className="detalle-cine-cartelera-panel">
        <div className="detalle-cine-container">
          <Link to="/cartelera" className="detalle-cartelera-intro" style={{ display: 'inline-block', marginBottom: '12px' }}>
            Volver a la cartelera
          </Link>
          <article className="detalle-peli-card">
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
                <h1 className="detalle-peli-heading">{peli.title}</h1>
                <p className="detalle-peli-synopsis-body">{peli.synopsis}</p>
              </div>

              <div className="detalle-peli-col-poster">
                {peli.posterUrl ? (
                  <img className="detalle-peli-fixed-poster" src={peli.posterUrl} alt={`Póster de ${peli.title}`} />
                ) : (
                  <div className="detalle-peli-fixed-poster placeholder">{peli.title.charAt(0)}</div>
                )}
              </div>
            </div>
          </article>
        </div>
      </section>

      {/* 2. Selector de fechas: los días sin pases aparecen desactivados */}
      <section className="detalle-cine-fechas-panel">
        <div className="detalle-cine-container">
          <h2 className="detalle-fechas-subtitulo">Selecciona fecha de proyección</h2>
          <div className="detalle-dias-scroll">
            {dias.map((d, i) => {
              const info = formatDia(d, i);
              const tienePases = diasConPases.has(fechaIso(d));
              return (
                <button
                  key={d.toISOString()}
                  type="button"
                  className={`detalle-dia-pill${diaIdx === i ? ' active' : ''}`}
                  onClick={() => { setDiaElegido(i); setSeleccion(null); }}
                  disabled={!tienePases}
                  title={tienePases ? undefined : 'Sin pases este día'}
                  style={tienePases ? undefined : { opacity: 0.4, cursor: 'not-allowed' }}
                >
                  <span className="detalle-dia-principal">{info.label}</span>
                  <span className="detalle-dia-secundario">{info.sub}</span>
                </button>
              );
            })}
          </div>
        </div>
      </section>

      {/* 3. Pases del día, agrupados por cine */}
      <section className="detalle-cine-cartelera-panel">
        <div className="detalle-cine-container">
          {datos.pases.length === 0 ? (
            <SinDatos>Esta película no tiene pases a la venta en los próximos días.</SinDatos>
          ) : (
            <>
              <p className="detalle-cartelera-intro">
                Pases de <strong>{peli.title}</strong> para <strong>{infoDia.label} ({infoDia.sub})</strong>
              </p>
              {grupos.length === 0 && <SinDatos>No hay pases a la venta para este día.</SinDatos>}

              <div className="detalle-peliculas-list">
                {grupos.map((grupo) => {
                  const paseActual = seleccion?.cinemaId === grupo.cinemaId ? seleccion.pase : null;
                  return (
                    <article key={grupo.cinemaId} className="detalle-peli-card">
                      <span className="home-eyebrow">{grupo.cine?.city || ''}</span>
                      <h2 className="detalle-peli-heading" style={{ marginTop: '4px' }}>
                        {grupo.cine ? (
                          <Link to={`/cines/${grupo.cinemaId}?pelicula=${peli.id}`} style={{ color: 'inherit' }}>
                            {grupo.cine.name}
                          </Link>
                        ) : 'Cine'}
                      </h2>
                      {grupo.cine?.address && <p className="detalle-peli-synopsis-body">{grupo.cine.address}</p>}

                      <div className="detalle-sesiones-bloque">
                        <h3 className="detalle-sesiones-titulo">Horarios disponibles:</h3>
                        <div className="detalle-sesiones-fila-accion">
                          <div className="detalle-sesiones-grid">
                            {grupo.pases.map((pase) => {
                              const isSelected = paseActual?.showId === pase.showId;
                              return (
                                <button
                                  key={pase.showId}
                                  type="button"
                                  className={`detalle-hora-card${isSelected ? ' selected' : ''}`}
                                  onClick={() => setSeleccion(isSelected ? null : { cinemaId: grupo.cinemaId, pase })}
                                >
                                  <span className="detalle-hora-time">{pase.hora}</span>
                                  <span className="detalle-hora-meta">
                                    {[pase.formato, pase.salaNombre].filter(Boolean).join(', ') || 'Sala de proyección'}
                                  </span>
                                  <span className="detalle-hora-price">{pase.precio.toFixed(2)} €</span>
                                </button>
                              );
                            })}
                          </div>

                          {paseActual && (
                            <button
                              className="home-button detalle-btn-comprar-inline"
                              type="button"
                              onClick={() => handleComprar(grupo, paseActual)}
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
            </>
          )}
        </div>
      </section>
    </div>
  );
}
