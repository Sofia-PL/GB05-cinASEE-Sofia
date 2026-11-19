import { useCallback, useEffect, useState } from 'react';
import { obtenerCines } from '../api/cartelera';
import { CineContext } from './useCine';
import { useAuth } from './useAuth';

/* =========================================================
   Contexto global: cines de Cartelera (Comp. 1) y cine elegido
   ---------------------------------------------------------
   La lista de cines se pide una vez al backend. El cine elegido
   por defecto es el cine habitual del perfil (Comp. 3, homeCinema).
   ========================================================= */

export function CineProvider({ children }) {
  const { user } = useAuth();
  const [estado, setEstado] = useState({ cines: [], cargando: true, error: '' });
  const [recarga, setRecarga] = useState(0);
  const [cineElegidoId, setCineElegidoId] = useState(null);

  useEffect(() => {
    let cancelado = false;
    obtenerCines()
      .then((lista) => {
        if (!cancelado) setEstado({ cines: Array.isArray(lista) ? lista : [], cargando: false, error: '' });
      })
      .catch((err) => {
        if (!cancelado) setEstado({ cines: [], cargando: false, error: err.message });
      });
    return () => { cancelado = true; };
  }, [recarga]);

  const idActivo = cineElegidoId ?? user?.homeCinema ?? null;
  const cineSeleccionado = estado.cines.find((c) => c.id === idActivo) || null;

  const recargarCines = useCallback(() => setRecarga((n) => n + 1), []);
  const setCineSeleccionado = useCallback((cine) => setCineElegidoId(cine?.id ?? null), []);

  const valor = {
    cines: estado.cines,
    cargandoCines: estado.cargando,
    errorCines: estado.error,
    recargarCines,
    cineSeleccionado,
    setCineSeleccionado,
  };

  return <CineContext.Provider value={valor}>{children}</CineContext.Provider>;
}
