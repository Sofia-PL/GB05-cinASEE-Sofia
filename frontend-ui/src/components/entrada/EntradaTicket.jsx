import { useId, useMemo, useRef, useState } from 'react';
import QRCode from 'qrcode';
import { temaDeSala, cintasDeEntrada, partirTexto } from './temasEntrada';

/* =========================================================
   Entrada de cine
   ---------------------------------------------------------
   Se dibuja en SVG para que se vea igual en pantalla, al imprimir
   y al descargarla como imagen (PNG). El color de la cabecera
   depende del tipo de sala; las cintas, del descuento y del día y
   la hora de la sesión. Incluye el mapa de la sala con la butaca
   marcada y el QR que se escanea en la puerta.
   ========================================================= */

const W = 380;
const H = 880;
const ALTO_CABECERA = 214;
const Y_TROQUEL = 664; // línea de corte entre la entrada y la matriz con el QR
const SERIF = "Georgia, 'Times New Roman', serif";
const SANS = "'Segoe UI', Arial, sans-serif";
const MONO = "'Courier New', monospace";

function letraFila(fila) {
  return fila ? String.fromCharCode(64 + fila) : '';
}

/** Perforaciones sutiles arriba y abajo de la cabecera, como una tira de película */
function Perforaciones() {
  return (
    <g fill="rgba(255,255,255,0.13)">
      {Array.from({ length: 16 }, (_, i) => (
        <g key={i}>
          <rect x={14 + i * 23} y={14} width={12} height={8} rx={2} />
          <rect x={14 + i * 23} y={ALTO_CABECERA - 22} width={12} height={8} rx={2} />
        </g>
      ))}
    </g>
  );
}

/** Mapa de la sala con la butaca de la entrada resaltada */
function MapaSala({ entrada, tema, y }) {
  const { filas, columnas, fila, columna } = entrada;
  if (!filas || !columnas) return null;
  const anchoDisponible = 320;
  // El mapa ocupa el hueco que queda hasta la línea de corte (menos si hay cintas)
  const altoDisponible = Math.max(30, Y_TROQUEL - 16 - (y + 40));
  const paso = Math.min(anchoDisponible / columnas, altoDisponible / filas);
  const lado = Math.max(3, paso - 2.5);
  const x0 = (W - columnas * paso) / 2;
  const y0 = y + 34;
  return (
    <g>
      <text x={W / 2} y={y} textAnchor="middle" fontFamily={SANS} fontSize={12} letterSpacing={3} fill={tema.principal} fontWeight={700}>
        TU BUTACA EN LA SALA
      </text>
      <path d={`M ${W / 2 - 120} ${y + 20} Q ${W / 2} ${y + 9} ${W / 2 + 120} ${y + 20}`} fill="none" stroke={tema.principal} strokeWidth={2.5} strokeLinecap="round" />
      <text x={W / 2} y={y + 32} textAnchor="middle" fontFamily={SANS} fontSize={9} letterSpacing={2} fill={tema.tinta} opacity={0.6}>
        PANTALLA
      </text>
      {Array.from({ length: filas }, (_, f) => Array.from({ length: columnas }, (_, c) => {
        const esLaMia = f + 1 === fila && c + 1 === columna;
        return (
          <rect
            key={`${f}-${c}`}
            x={x0 + c * paso}
            y={y0 + 8 + f * paso}
            width={lado}
            height={lado}
            rx={Math.min(2, lado / 3)}
            fill={esLaMia ? tema.acento : 'none'}
            stroke={esLaMia ? tema.principal : tema.tinta}
            strokeOpacity={esLaMia ? 1 : 0.28}
            strokeWidth={esLaMia ? 1.6 : 0.8}
          />
        );
      }))}
    </g>
  );
}

/** Caja con etiqueta y valor (fecha, hora, cine...) */
function Casilla({ x, y, w, h = 56, etiqueta, valor, tema, tamano = 17 }) {
  return (
    <g>
      <rect x={x} y={y} width={w} height={h} rx={6} fill="none" stroke={tema.principal} strokeWidth={1.4} />
      <text x={x + 11} y={y + 18} fontFamily={SANS} fontSize={11} letterSpacing={2} fill={tema.principal} fontWeight={700}>
        {etiqueta}
      </text>
      <text x={x + 11} y={y + 43} fontFamily={SANS} fontSize={tamano} fill={tema.tinta} fontWeight={700}>
        {valor}
      </text>
    </g>
  );
}

/**
 * QR dibujado como trazado vectorial dentro del SVG (nítido al descargar o imprimir).
 * Devuelve { d, modulos } o null si el código no se puede codificar.
 */
function trazadoQr(texto) {
  try {
    const { modules } = QRCode.create(String(texto || ''), { errorCorrectionLevel: 'M' });
    let d = '';
    for (let fila = 0; fila < modules.size; fila++) {
      for (let col = 0; col < modules.size; col++) {
        if (modules.get(fila, col)) d += `M${col} ${fila}h1v1h-1z`;
      }
    }
    return { d, modulos: modules.size };
  } catch {
    return null;
  }
}

/** Coloca las cintas en una o dos filas debajo de la cabecera */
function colocarCintas(cintas) {
  const colocadas = [];
  let x = 24;
  let y = ALTO_CABECERA + 16;
  for (const c of cintas) {
    const ancho = c.texto.length * 7.4 + 22;
    if (x + ancho > W - 24) {
      x = 24;
      y += 28;
    }
    colocadas.push({ ...c, x, y, ancho });
    x += ancho + 6;
  }
  return colocadas;
}

/** Dibuja el SVG en un lienzo y lo descarga como PNG */
async function descargarComoPng(svg, nombreFichero) {
  const xml = new XMLSerializer().serializeToString(svg);
  const url = URL.createObjectURL(new Blob([xml], { type: 'image/svg+xml;charset=utf-8' }));
  try {
    const imagen = new Image();
    await new Promise((ok, fallo) => {
      imagen.onload = ok;
      imagen.onerror = fallo;
      imagen.src = url;
    });
    const escala = 3; // buena resolución para imprimir o enseñar en el móvil
    const lienzo = document.createElement('canvas');
    lienzo.width = W * escala;
    lienzo.height = H * escala;
    lienzo.getContext('2d').drawImage(imagen, 0, 0, lienzo.width, lienzo.height);
    const png = await new Promise((ok) => lienzo.toBlob(ok, 'image/png'));
    const enlace = document.createElement('a');
    enlace.href = URL.createObjectURL(png);
    enlace.download = nombreFichero;
    enlace.click();
    setTimeout(() => URL.revokeObjectURL(enlace.href), 1000);
  } finally {
    URL.revokeObjectURL(url);
  }
}

/**
 * @param {object}  entrada     datos de construirEntrada()
 * @param {boolean} descargable muestra el botón "Descargar entrada"
 * @param {number}  ancho       ancho en pantalla (px)
 */
export default function EntradaTicket({ entrada, descargable = true, ancho = 300 }) {
  const tema = temaDeSala(entrada.roomType);
  const idMascara = `troquel-${useId().replace(/[^a-zA-Z0-9]/g, '')}`;
  const svgRef = useRef(null);
  const [descargando, setDescargando] = useState(false);
  const qr = useMemo(() => trazadoQr(entrada.codigo), [entrada.codigo]);

  const titulo = partirTexto(entrada.titulo, 19, 2);
  const meta = [entrada.genero, entrada.edad, entrada.duracion].filter(Boolean).join(', ');
  const cintasColocadas = colocarCintas(cintasDeEntrada(entrada));
  const yInfo = cintasColocadas.length > 0 ? cintasColocadas[cintasColocadas.length - 1].y + 34 : ALTO_CABECERA + 22;
  const butacaCorta = entrada.fila ? `${letraFila(entrada.fila)}${entrada.columna}` : '-';
  const precio = entrada.precio !== null ? `${entrada.precio.toFixed(2).replace('.', ',')} €` : '-';
  const anchoFormato = tema.formato.length * 16 + 28;

  async function descargar() {
    setDescargando(true);
    try {
      await descargarComoPng(svgRef.current, `entrada-cinasee-${entrada.codigo}.png`);
    } finally {
      setDescargando(false);
    }
  }

  return (
    <figure className="entrada-ticket" style={{ margin: 0, display: 'inline-flex', flexDirection: 'column', alignItems: 'center', gap: '10px' }}>
      <svg
        ref={svgRef}
        xmlns="http://www.w3.org/2000/svg"
        viewBox={`0 0 ${W} ${H}`}
        width={ancho}
        height={(ancho * H) / W}
        role="img"
        aria-label={`Entrada de ${entrada.titulo}, ${entrada.fechaTexto} a las ${entrada.hora}, butaca ${butacaCorta}`}
        style={{ filter: 'drop-shadow(0 6px 14px rgba(43,18,21,0.18))', maxWidth: '100%', height: 'auto' }}
      >
        <defs>
          {/* Troquel: borde dentado arriba y abajo y muescas a los lados en la línea de corte */}
          <mask id={idMascara}>
            <rect x={0} y={0} width={W} height={H} rx={14} fill="#fff" />
            {Array.from({ length: Math.ceil(W / 20) + 1 }, (_, i) => (
              <g key={i}>
                <circle cx={i * 20} cy={0} r={6.5} fill="#000" />
                <circle cx={i * 20} cy={H} r={6.5} fill="#000" />
              </g>
            ))}
            <circle cx={0} cy={Y_TROQUEL} r={15} fill="#000" />
            <circle cx={W} cy={Y_TROQUEL} r={15} fill="#000" />
          </mask>
        </defs>

        <g mask={`url(#${idMascara})`}>
          <rect x={0} y={0} width={W} height={H} fill={tema.papel} />

          {/* Cabecera: solo el color del tipo de sala */}
          <rect x={0} y={0} width={W} height={ALTO_CABECERA} fill={tema.principal} />
          <Perforaciones />

          <text x={24} y={56} fontFamily={SERIF} fontSize={20} letterSpacing={5} fill={tema.papel} fontWeight={700}>CinASEE</text>
          <text x={24} y={78} fontFamily={SANS} fontSize={13} letterSpacing={2} fill={tema.papel} opacity={0.85}>
            {tema.nombre.toUpperCase()}
          </text>

          {/* Formato de la sala */}
          <rect x={W - 24 - anchoFormato} y={34} width={anchoFormato} height={42} rx={8} fill={tema.acento} />
          <text x={W - 24 - anchoFormato / 2} y={63} textAnchor="middle" fontFamily={SANS} fontSize={24} fontWeight={800} fill={tema.principal}>
            {tema.formato}
          </text>

          {/* Título y datos de la película */}
          {titulo.map((linea, i) => (
            <text key={linea} x={24} y={132 + i * 34} fontFamily={SERIF} fontSize={30} fontWeight={700} fill="#ffffff">{linea}</text>
          ))}
          <text x={24} y={titulo.length > 1 ? 188 : 162} fontFamily={SANS} fontSize={14} fill={tema.papel} opacity={0.9}>{meta}</text>

          {/* Cintas: descuento, sesión de noche, fin de semana */}
          {cintasColocadas.map((c) => (
            <g key={c.texto}>
              <rect x={c.x} y={c.y} width={c.ancho} height={22} rx={11} fill={c.color} />
              <text x={c.x + c.ancho / 2} y={c.y + 15} textAnchor="middle" fontFamily={SANS} fontSize={11} fontWeight={700} letterSpacing={0.6} fill="#ffffff">
                {c.texto}
              </text>
            </g>
          ))}

          {/* Datos de la sesión */}
          <Casilla x={24} y={yInfo} w={160} etiqueta="FECHA" valor={entrada.fechaTexto || '-'} tema={tema} tamano={15} />
          <Casilla x={196} y={yInfo} w={160} etiqueta="HORA" valor={entrada.hora || '-'} tema={tema} tamano={22} />
          <Casilla x={24} y={yInfo + 64} w={332} etiqueta="CINE" valor={entrada.cine || '-'} tema={tema} />
          <Casilla x={24} y={yInfo + 128} w={160} etiqueta="SALA" valor={partirTexto(entrada.salaNombre, 19, 1)[0]} tema={tema} tamano={14} />
          <Casilla
            x={196}
            y={yInfo + 128}
            w={160}
            etiqueta="BUTACA"
            valor={entrada.fila ? `Fila ${letraFila(entrada.fila)}, asiento ${entrada.columna}` : '-'}
            tema={tema}
            tamano={14}
          />

          <MapaSala entrada={entrada} tema={tema} y={yInfo + 214} />

          {/* Línea de corte */}
          <line x1={24} y1={Y_TROQUEL} x2={W - 24} y2={Y_TROQUEL} stroke={tema.principal} strokeWidth={1.6} strokeDasharray="6 6" opacity={0.6} />

          {/* Matriz: QR, butaca, precio y código */}
          <rect x={24} y={Y_TROQUEL + 24} width={164} height={164} rx={10} fill="#ffffff" stroke={tema.principal} strokeOpacity={0.25} />
          {qr && (
            <path
              d={qr.d}
              fill={tema.tinta}
              transform={`translate(32 ${Y_TROQUEL + 32}) scale(${148 / qr.modulos})`}
              shapeRendering="crispEdges"
            />
          )}
          <text x={202} y={Y_TROQUEL + 46} fontFamily={SANS} fontSize={11} letterSpacing={2} fill={tema.principal} fontWeight={700}>ADMITE 1 PERSONA</text>
          <text x={200} y={Y_TROQUEL + 104} fontFamily={SANS} fontSize={54} fontWeight={800} fill={tema.principal}>{butacaCorta}</text>
          <text x={202} y={Y_TROQUEL + 132} fontFamily={SANS} fontSize={21} fontWeight={800} fill={tema.tinta}>{precio}</text>
          <text x={202} y={Y_TROQUEL + 152} fontFamily={MONO} fontSize={10.5} fill={tema.tinta}>{entrada.codigo}</text>
          {entrada.reserva && (
            <text x={202} y={Y_TROQUEL + 171} fontFamily={SANS} fontSize={13} fill={tema.tinta} opacity={0.8}>Reserva nº {entrada.reserva}</text>
          )}
          <text x={202} y={Y_TROQUEL + 189} fontFamily={SANS} fontSize={11} fill={tema.tinta} opacity={0.65}>Enseña este QR en la puerta</text>
        </g>
      </svg>

      {descargable && (
        <button type="button" className="home-button home-button-dark no-imprimir" onClick={descargar} disabled={descargando} style={{ padding: '0.5em 1.1em', fontSize: '0.88rem' }}>
          {descargando ? 'Preparando…' : 'Descargar entrada'}
        </button>
      )}
    </figure>
  );
}
