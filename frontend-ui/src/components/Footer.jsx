import { Link } from 'react-router-dom';
import { useCine } from '../context/useCine';

export default function Footer() {
  const { cines } = useCine();

  return (
    <footer className="site-footer">
      <div className="site-footer-container">
        {/* Columna 1: Marca */}
        <div className="site-footer-col">
          <Link to="/" className="site-footer-logo">CinASEE</Link>
          <p className="site-footer-desc">
            Consulta la cartelera, elige tu butaca y compra tus entradas online en cualquiera de nuestros cines.
          </p>
        </div>

        {/* Columna 2: Navegación */}
        <div className="site-footer-col">
          <h4 className="site-footer-heading">Navegación</h4>
          <ul className="site-footer-links">
            <li><Link to="/cines">Nuestros cines</Link></li>
            <li><Link to="/cartelera">Cartelera</Link></li>
            <li><Link to="/perfil">Mi cuenta y entradas</Link></li>
          </ul>
        </div>

        {/* Columna 3: Contacto de cada cine (datos de Cartelera) */}
        {cines.length > 0 && (
          <div className="site-footer-col">
            <h4 className="site-footer-heading">Contacto</h4>
            <ul className="site-footer-contact">
              {cines.map((c) => (
                <li key={c.id}>
                  <strong>{c.name}</strong>
                  {c.phone ? (
                    <a href={`tel:${c.phone.replace(/\s+/g, '')}`}>{c.phone}</a>
                  ) : (
                    <span>{c.address}, {c.city}</span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* Franja inferior */}
      <div className="site-footer-bottom">
        <div className="site-footer-bottom-inner">
          <p>© {new Date().getFullYear()} CinASEE</p>
          <div className="site-footer-badges">
            <span>Pago seguro con Redsys</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
