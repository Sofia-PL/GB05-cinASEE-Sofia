import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

/** QR de un código de entrada (lo que lee el portal de validación) */
export default function CodigoQR({ texto, tamano = 120 }) {
  const [imagen, setImagen] = useState({ texto: null, url: '' });

  useEffect(() => {
    let cancelado = false;
    QRCode.toDataURL(texto, { width: tamano, margin: 1, errorCorrectionLevel: 'M' })
      .then((url) => { if (!cancelado) setImagen({ texto, url }); })
      .catch(() => { if (!cancelado) setImagen({ texto, url: '' }); });
    return () => { cancelado = true; };
  }, [texto, tamano]);

  if (imagen.texto !== texto || !imagen.url) {
    return <div style={{ width: tamano, height: tamano }} aria-hidden="true" />;
  }
  return <img src={imagen.url} width={tamano} height={tamano} alt={`Código QR de la entrada ${texto}`} />;
}
