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

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(cargarUsuario);

  /**
   * Inicio de sesión local con control de errores
   */
  function login(email, password) {
    const normEmail = (email || '').trim().toLowerCase();
    const normPass = (password || '').trim();

    if (!normEmail || !normPass) {
      return { ok: false, error: 'Por favor, introduce tu correo electrónico y contraseña.' };
    }

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
      return {
        ok: false,
        error: 'Correo electrónico o contraseña incorrectos. Comprueba los datos o usa el acceso rápido.',
      };
    }

    const userData = { name: found.name, email: found.email, rol: found.rol };
    localStorage.setItem(LS_KEY, JSON.stringify(userData));
    setUser(userData);
    return { ok: true, user: userData };
  }

  /**
   * Registro local con validaciones
   */
  function register(name, email, password, rol = 'USER') {
    const cleanName = (name || '').trim();
    const cleanEmail = (email || '').trim().toLowerCase();
    const cleanPass = (password || '').trim();

    if (!cleanName || !cleanEmail || !cleanPass) {
      return { ok: false, error: 'Todos los campos son obligatorios.' };
    }
    if (cleanPass.length < 8) {
      return { ok: false, error: 'La contraseña debe tener un mínimo de 8 caracteres.' };
    }

    const yaFijo = USUARIOS_FICTICIOS.find((u) => u.email.toLowerCase() === cleanEmail);
    if (yaFijo) {
      return { ok: false, error: 'Ese correo electrónico ya está registrado en el sistema.' };
    }

    let regUsers = [];
    try {
      regUsers = JSON.parse(localStorage.getItem(LS_REG_KEY) || '[]');
    } catch {
      regUsers = [];
    }

    if (regUsers.some((u) => u.email.toLowerCase() === cleanEmail)) {
      return { ok: false, error: 'Ese correo ya está registrado.' };
    }

    const newUser = { name: cleanName, email: cleanEmail, password: cleanPass, rol: rol || 'USER' };
    regUsers.push(newUser);
    localStorage.setItem(LS_REG_KEY, JSON.stringify(regUsers));

    const userData = { name: newUser.name, email: newUser.email, rol: newUser.rol };
    localStorage.setItem(LS_KEY, JSON.stringify(userData));
    setUser(userData);
    return { ok: true, user: userData };
  }

  function updateUser(newData) {
    if (!user) return null;
    const updated = { ...user, ...newData };
    localStorage.setItem(LS_KEY, JSON.stringify(updated));
    try {
      const regUsers = JSON.parse(localStorage.getItem(LS_REG_KEY) || '[]');
      const idx = regUsers.findIndex((u) => u.email.toLowerCase() === user.email.toLowerCase());
      if (idx !== -1) {
        regUsers[idx] = { ...regUsers[idx], ...newData };
        localStorage.setItem(LS_REG_KEY, JSON.stringify(regUsers));
      }
    } catch {
      // ignorar
    }
    setUser(updated);
    return updated;
  }

  function logout() {
    localStorage.removeItem(LS_KEY);
    setUser(null);
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: Boolean(user),
        login,
        register,
        logout,
        updateUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
