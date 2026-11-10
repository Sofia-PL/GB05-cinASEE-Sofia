import { createContext, useContext, useState } from 'react';

/* =========================================================
   Contexto global: cine seleccionado por el usuario
   ========================================================= */
const CineContext = createContext(null);

export function CineProvider({ children }) {
  const [cineSeleccionado, setCineSeleccionado] = useState(null);
  // cineSeleccionado: objeto cine de cines.json | null

  return (
    <CineContext.Provider value={{ cineSeleccionado, setCineSeleccionado }}>
      {children}
    </CineContext.Provider>
  );
}

export function useCine() {
  return useContext(CineContext);
}

