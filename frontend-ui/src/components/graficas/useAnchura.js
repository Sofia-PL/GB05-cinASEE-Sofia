import { useEffect, useRef, useState } from 'react';

/** Anchura del contenedor en píxeles, actualizada al redimensionar */
export function useAnchura(inicial = 600) {
  const ref = useRef(null);
  const [anchura, setAnchura] = useState(inicial);

  useEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    const ro = new ResizeObserver(([entrada]) => {
      setAnchura(Math.max(240, Math.floor(entrada.contentRect.width)));
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  return [ref, anchura];
}

/** Valor "redondo" por encima del máximo, para el final del eje */
export function techoEje(max) {
  if (!(max > 0)) return 1;
  const potencia = 10 ** Math.floor(Math.log10(max));
  const pasos = [1, 2, 2.5, 5, 10];
  const paso = pasos.find((p) => p * potencia >= max) * potencia;
  return paso;
}
