import { createContext, useContext } from 'react';

/* Contexto del cine seleccionado. El proveedor (CineProvider) está en CineContext.jsx. */
export const CineContext = createContext(null);

export function useCine() {
  return useContext(CineContext);
}
