import { Link, useParams } from 'react-router-dom';
import cines from '../../assets/cines.json';

export default function CineDetalle() {
  const { cineId } = useParams();
  const cine = cines.find((item) => String(item.id) === cineId);

  if (!cine) {
    return (
      <section className="placeholder-page">
        <h1>Cine no encontrado</h1>
        <p>El cine que buscas no está disponible.</p>
        <Link className="home-button" to="/cines">Volver a cines</Link>
      </section>
    );
  }

  return (
    <section className="cines-page cine-detail-page">
      <Link className="cine-detail-back" to="/cines">← Volver a cines</Link>
      <header className="cines-header">
        <p className="home-eyebrow">{cine.city}, {cine.province}</p>
        <h1>{cine.name}</h1>
        <p>{cine.address}, {cine.postalCode}</p>
      </header>

      <div className="cine-detail-grid">
        <article className="cine-detail-card">
          <p className="home-eyebrow">Información</p>
          <h2>Tu cine CineASEE</h2>
          <p>{cine.rooms} salas disponibles</p>
          <p>Formatos: {cine.formats.join(' · ')}</p>
        </article>
        <article className="cine-detail-card">
          <p className="home-eyebrow">Próximamente</p>
          <h2>Cartelera y sesiones</h2>
          <p>Consulta las películas y horarios disponibles en este cine.</p>
          <Link className="home-button" to="/cartelera">Ver cartelera</Link>
        </article>
      </div>
    </section>
  );
}
