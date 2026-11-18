import { Link } from 'react-router-dom';

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="site-footer-container">
        {/* Columna 1: Marca y descripción */}
        <div className="site-footer-col">
          <Link to="/" className="site-footer-logo">CinASEE</Link>
          <p className="site-footer-desc">
            Red de cines líder en España con la última tecnología en proyección láser digital, salas IMAX, 3D RealD y sonido envolvente Dolby Atmos.
          </p>
          <div className="site-footer-company-data">
            <span>CinASEE Exhibición Cinematográfica S.L.</span>
            <span>NIF: B-88492015</span>
            <span>Sede Central: Gran Vía 42, 28013 Madrid</span>
          </div>
        </div>

        {/* Columna 2: Navegación rápida */}
        <div className="site-footer-col">
          <h4 className="site-footer-heading">Navegación</h4>
          <ul className="site-footer-links">
            <li><Link to="/cines">Nuestros cines</Link></li>
            <li><Link to="/cartelera">Cartelera y horarios</Link></li>
            <li><Link to="/cartelera">Próximos estrenos</Link></li>
            <li><Link to="/cines">Salas IMAX y VIP</Link></li>
            <li><Link to="/login">Acceso a mi cuenta</Link></li>
          </ul>
        </div>

        {/* Columna 3: Contacto y Atención al Cliente */}
        <div className="site-footer-col">
          <h4 className="site-footer-heading">Atención al cliente</h4>
          <ul className="site-footer-contact">
            <li>
              <strong>Teléfono gratuito:</strong>
              <a href="tel:900842901">900 842 901</a>
            </li>
            <li>
              <strong>Correo electrónico:</strong>
              <a href="mailto:contacto@cinasee.es">contacto@cinasee.es</a>
            </li>
            <li>
              <strong>Horario de atención:</strong>
              <span>Lunes a Domingo: 11:00 a 23:00 h</span>
            </li>
            <li>
              <strong>Incidencias de taquilla:</strong>
              <span>soporte@cinasee.es</span>
            </li>
          </ul>
        </div>

        {/* Columna 4: Legal y Políticas */}
        <div className="site-footer-col">
          <h4 className="site-footer-heading">Información legal</h4>
          <ul className="site-footer-links">
            <li><a href="#aviso-legal" onClick={(e) => e.preventDefault()}>Aviso legal</a></li>
            <li><a href="#privacidad" onClick={(e) => e.preventDefault()}>Política de privacidad</a></li>
            <li><a href="#cookies" onClick={(e) => e.preventDefault()}>Política de cookies</a></li>
            <li><a href="#condiciones" onClick={(e) => e.preventDefault()}>Condiciones de compra de entradas</a></li>
            <li><a href="#accesibilidad" onClick={(e) => e.preventDefault()}>Accesibilidad de salas</a></li>
          </ul>
        </div>
      </div>

      {/* Franja inferior de copyright */}
      <div className="site-footer-bottom">
        <div className="site-footer-bottom-inner">
          <p>© 2026 CinASEE España S.L. Todos los derechos reservados.</p>
          <div className="site-footer-badges">
            <span>Pago Seguro 256-bit SSL</span>
            <span>Entradas Oficiales Garantizadas</span>
          </div>
        </div>
      </div>
    </footer>
  );
}

