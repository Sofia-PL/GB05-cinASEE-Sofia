import { createContext, useContext } from 'react';

/* Contexto de autenticación. El proveedor (AuthProvider) está en AuthContext.jsx;
   aquí solo el objeto de contexto y el hook, para que la recarga en caliente de
   Vite funcione (un fichero de componentes solo debe exportar componentes). */
export const AuthContext = createContext(null);

export function useAuth() {
  return useContext(AuthContext);
}
