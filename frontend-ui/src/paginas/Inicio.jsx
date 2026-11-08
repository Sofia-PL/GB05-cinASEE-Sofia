import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import peliculas from '../assets/peliculas.json';
import cines from '../assets/cines.json';

export default function Inicio() {
  const availableMovies = peliculas.filter((movie) => movie.inShowcase);
  const [activeMovie, setActiveMovie] = useState(0);
  const movie = availableMovies[activeMovie];

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

  return (
    <div className="home-page">
      <section className="home-carousel" aria-label="Películas disponibles">
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

      <section className="home-cinemas-section">
        <p className="home-eyebrow">CinASEE España</p>
        <h2>Encuentra tu cine</h2>
        <p className="home-section-description">
          Consulta nuestros cines y elige dónde quieres disfrutar de tu próxima película.
        </p>
        <div className="cines-preview">
          <div className="cines-grid">
            {cines.slice(0, 10).map((cine) => (
              <article className="cine-card" key={cine.id}>
                <p className="home-eyebrow">{cine.city}</p>
                <h3>{cine.name}</h3>
                <p>{cine.address}, {cine.postalCode}</p>
                <Link className="home-button" to={`/cines/${cine.id}`}>Ver cine</Link>
              </article>
            ))}
          </div>
          <Link className="home-button home-button-dark" to="/cines">Ver todos los cines</Link>
        </div>
      </section>
    </div>
  );
}
