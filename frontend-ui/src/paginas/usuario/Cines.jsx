import { Link } from 'react-router-dom';
import cines from '../../assets/cines.json';

export default function Cines() {
  return (
    <section className="cines-page">
      <header className="cines-header">
        <p className="home-eyebrow">CinASEE en España</p>
        <h1>Nuestros cines</h1>
        <p>Selecciona un cine para consultar su cartelera y sus sesiones.</p>
      </header>

      <div className="cines-grid">
        {cines.map((cine) => (
          <article className="cine-card" key={cine.id}>
            <p className="home-eyebrow">{cine.city}</p>
            <h2>{cine.name}</h2>
            <p>{cine.address}, {cine.postalCode}</p>
            <p>{cine.rooms} salas · {cine.formats.join(' · ')}</p>
            <Link className="home-button home-button-dark" to={`/cines/${cine.id}`}>Ver cine</Link>
          </article>
        ))}
      </div>
    </section>
  );
}
