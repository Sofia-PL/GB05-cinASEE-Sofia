import { useState } from 'react';
import cines from '../assets/cines.json';
import { CineContext } from './useCine';

/* =========================================================
   Contexto global: cine seleccionado por el usuario
   ========================================================= */

export function CineProvider({ children }) {
  const [cineSeleccionado, setCineSeleccionado] = useState(() => {
    try {
      const rawUser = localStorage.getItem('cinasee_user');
      if (rawUser) {
        const u = JSON.parse(rawUser);
        if (u.cineHabitualId) {
          const match = cines.find((c) => c.id === Number(u.cineHabitualId));
          if (match) return match;
        }
      }
    } catch {
      // ignorar
    }
    return null;
  });

  return (
    <CineContext.Provider value={{ cineSeleccionado, setCineSeleccionado }}>
      {children}
    </CineContext.Provider>
  );
}


