import { useLocation, useNavigate } from 'react-router-dom';
import cines from '../../assets/cines.json';
import peliculas from '../../assets/peliculas.json';
import { useCine } from '../../context/useCine';

export default function Cines() {
  const location = useLocation();
  const navigate = useNavigate();
  const { setCineSeleccionado } = useCine();

  const peliculaId = location.state?.peliculaId;
  const peliSeleccionada = peliculaId ? peliculas.find((p) => p.id === Number(peliculaId)) : null;

  function handleElegirCine(cine) {
    setCineSeleccionado(cine);
    if (peliculaId) {
      navigate(`/cines/${cine.id}?pelicula=${peliculaId}`);
    } else {
      navigate(`/cines/${cine.id}`);
    }
  }

  return (
    <section className="cines-page">
      <header className="cines-header">
        <p className="home-eyebrow">CinASEE en España</p>
        <h1>Nuestros cines</h1>
        {peliSeleccionada ? (
          <div className="cines-aviso-pelicula">
            Película seleccionada: <strong>{peliSeleccionada.title}</strong>. Elige a continuación el cine donde deseas verla:
          </div>
        ) : (
          <p className="cines-header-desc">
            Encuentra tu cine más cercano y consulta la cartelera actualizada con sesiones y formatos disponibles.
          </p>
        )}
      </header>

      <div className="cines-grid">
        {cines.map((cine) => (
          <article className="cine-card" key={cine.id}>
            <div className="cine-card-header">
              <span className="cine-card-city">{cine.city}</span>
              <span className="cine-card-rooms">{cine.rooms} salas</span>
            </div>
            <h2 className="cine-card-title">{cine.name}</h2>
            <p className="cine-card-address">{cine.address}, {cine.postalCode}</p>
            <div className="cine-card-formats">
              {cine.formats.map((f) => (
                <span key={f} className="cine-format-pill">{f}</span>
              ))}
            </div>
            <button
              className="home-button home-button-dark cine-card-btn"
              type="button"
              onClick={() => handleElegirCine(cine)}
            >
              {peliSeleccionada ? 'Elegir este cine' : 'Ver sesiones'}
            </button>
          </article>
        ))}
      </div>
    </section>
  );
}
