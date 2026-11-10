import { createContext, useContext, useState } from 'react';

/* =========================================================
   Usuarios ficticios guardados para la demo
   ========================================================= */
const USUARIOS_FICTICIOS = [
  {
    email: 'admin@cinasee.es',
    passwords: ['admin123', 'admin'],
    name: 'Administrador CinASEE',
    rol: 'ADMIN',
  },
  {
    email: 'admin@cinasee.com',
    passwords: ['admin123', 'admin'],
    name: 'Administrador CinASEE',
    rol: 'ADMIN',
  },
  {
    email: 'staff@cinasee.es',
    passwords: ['staff123', 'staff'],
    name: 'Personal de Cine',
    rol: 'STAFF',
  },
  {
    email: 'staff@cinasee.com',
    passwords: ['staff123', 'staff'],
    name: 'Personal de Cine',
    rol: 'STAFF',
  },
  {
    email: 'usuario@cinasee.es',
    passwords: ['user123', 'usuario123', 'user', 'usuario'],
    name: 'Usuario CinASEE',
    rol: 'USER',
  },
  {
    email: 'user@cinasee.es',
    passwords: ['user123', 'usuario123', 'user', 'usuario'],
    name: 'Usuario CinASEE',
    rol: 'USER',
  },
  {
    email: 'user@cinasee.com',
    passwords: ['user123', 'usuario123', 'user', 'usuario'],
    name: 'Usuario CinASEE',
    rol: 'USER',
  },
];

const LS_KEY = 'cinasee_user';
const LS_REG_KEY = 'cinasee_registered_users';

function cargarUsuario() {
  try {
    const raw = localStorage.getItem(LS_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

/* =========================================================
   Contexto
   ========================================================= */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(cargarUsuario);

  /**
   * @returns {{ ok: boolean, user?: object, error?: string }}
   */
  function login(email, password) {
    const normEmail = (email || '').trim().toLowerCase();
    const normPass = (password || '').trim();

    // 1. Buscar en usuarios fijos del sistema
    let found = USUARIOS_FICTICIOS.find(
      (u) => u.email.toLowerCase() === normEmail && u.passwords.includes(normPass)
    );

    // 2. Si no coincide, buscar en usuarios registrados dinámicamente
    if (!found) {
      try {
        const regUsers = JSON.parse(localStorage.getItem(LS_REG_KEY) || '[]');
        found = regUsers.find(
          (u) => u.email.toLowerCase() === normEmail && u.password === normPass
        );
      } catch {
        // ignore
      }
    }

    if (!found) {
      return { ok: false, error: 'Correo electrónico o contraseña incorrectos. Comprueba los datos de prueba.' };
    }

    const userData = { name: found.name, email: found.email, rol: found.rol };
    localStorage.setItem(LS_KEY, JSON.stringify(userData));
    setUser(userData);
    return { ok: true, user: userData };
  }

  /**
   * Registro ficticio con selección de rol.
   * @returns {{ ok: boolean, user?: object, error?: string }}
   */
  function register(name, email, password, rol = 'USER') {
    if (!name || !email || !password) {
      return { ok: false, error: 'Todos los campos son obligatorios.' };
    }
    const normEmail = email.trim().toLowerCase();
    const yaFijo = USUARIOS_FICTICIOS.find((u) => u.email.toLowerCase() === normEmail);
    if (yaFijo) {
      return { ok: false, error: 'Ese correo ya está registrado en el sistema.' };
    }

    let regUsers = [];
    try {
      regUsers = JSON.parse(localStorage.getItem(LS_REG_KEY) || '[]');
    } catch {
      regUsers = [];
    }

    if (regUsers.some((u) => u.email.toLowerCase() === normEmail)) {
      return { ok: false, error: 'Ese correo ya está registrado.' };
    }

    const newUser = { name: name.trim(), email: normEmail, password, rol: rol || 'USER' };
    regUsers.push(newUser);
    localStorage.setItem(LS_REG_KEY, JSON.stringify(regUsers));

    const userData = { name: newUser.name, email: newUser.email, rol: newUser.rol };
    localStorage.setItem(LS_KEY, JSON.stringify(userData));
    setUser(userData);
    return { ok: true, user: userData };
  }

  function logout() {
    localStorage.removeItem(LS_KEY);
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}

