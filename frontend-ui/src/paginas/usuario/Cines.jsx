import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useCine } from '../../context/useCine';
import { obtenerPelicula } from '../../api/cartelera';
import { Cargando, ErrorCarga, SinDatos } from '../../components/EstadoCarga';

export default function Cines() {
  const location = useLocation();
  const navigate = useNavigate();
  const { cines, cargandoCines, errorCines, recargarCines, setCineSeleccionado } = useCine();

  // Si se llega desde la cartelera con una película elegida, se muestra su título
  const peliculaId = location.state?.peliculaId;
  const [peliSeleccionada, setPeliSeleccionada] = useState(null);

  useEffect(() => {
    if (!peliculaId) return undefined;
    let cancelado = false;
    obtenerPelicula(peliculaId)
      .then((p) => { if (!cancelado) setPeliSeleccionada(p); })
      .catch(() => { /* sin título: se puede elegir cine igualmente */ });
    return () => { cancelado = true; };
  }, [peliculaId]);

  function handleElegirCine(cine) {
    setCineSeleccionado(cine);
    navigate(peliculaId ? `/cines/${cine.id}?pelicula=${peliculaId}` : `/cines/${cine.id}`);
  }

  return (
    <section className="cines-page">
      <header className="cines-header">
        <p className="home-eyebrow">CinASEE en España</p>
        <h1>Nuestros cines</h1>
        {peliculaId ? (
          <div className="cines-aviso-pelicula">
            {peliSeleccionada
              ? <>Película seleccionada: <strong>{peliSeleccionada.title}</strong>. </>
              : null}
            Elige a continuación el cine donde deseas verla:
          </div>
        ) : (
          <p className="cines-header-desc">
            Encuentra tu cine más cercano y consulta las sesiones a la venta.
          </p>
        )}
      </header>

      {cargandoCines && <Cargando texto="Cargando cines…" />}
      {errorCines && <ErrorCarga mensaje={`No se pudieron cargar los cines: ${errorCines}`} onReintentar={recargarCines} />}
      {!cargandoCines && !errorCines && cines.length === 0 && <SinDatos>Todavía no hay cines dados de alta.</SinDatos>}

      <div className="cines-grid">
        {cines.map((cine) => (
          <article className="cine-card" key={cine.id}>
            <div className="cine-card-header">
              <span className="cine-card-city">{cine.city}</span>
            </div>
            <h2 className="cine-card-title">{cine.name}</h2>
            <p className="cine-card-address">{cine.address}</p>
            {cine.phone && <p className="cine-card-address">Tel. {cine.phone}</p>}
            <button
              className="home-button home-button-dark cine-card-btn"
              type="button"
              onClick={() => handleElegirCine(cine)}
            >
              {peliculaId ? 'Elegir este cine' : 'Ver sesiones'}
            </button>
          </article>
        ))}
      </div>
    </section>
  );
}
