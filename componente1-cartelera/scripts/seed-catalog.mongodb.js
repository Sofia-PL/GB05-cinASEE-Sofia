const expectedDatabase = "cine_catalog_db";

if (db.getName() !== expectedDatabase) {
  throw new Error(
    `Refusing to seed database "${db.getName()}"; expected "${expectedDatabase}".`,
  );
}

const cinemaIds = {
  madrid: "550e8400-e29b-41d4-a716-446655440001",
  barcelona: "550e8400-e29b-41d4-a716-446655440002",
  caceres: "550e8400-e29b-41d4-a716-446655440003",
  navatrasierra: "550e8400-e29b-41d4-a716-446655440004",
  madronera: "550e8400-e29b-41d4-a716-446655440005",
  malaga: "550e8400-e29b-41d4-a716-446655440006",
  cuenca: "550e8400-e29b-41d4-a716-446655440007",
};

const movieIds = {
  ponyo: "00000000-0000-4000-8000-000000000101",
  howlsMovingCastle: "00000000-0000-4000-8000-000000000102",
  everythingEverywhereAllAtOnce: "00000000-0000-4000-8000-000000000103",
  interstellar: "00000000-0000-4000-8000-000000000104",
  prideAndPrejudice: "00000000-0000-4000-8000-000000000105",
  harryPotterAzkaban: "00000000-0000-4000-8000-000000000106",
  velocipastor: "00000000-0000-4000-8000-000000000107",
  hungerGamesSunrise: "00000000-0000-4000-8000-000000000108",
};

const roomIds = {
  madridStandard: "00000000-0000-4000-8000-000000000201",
  madrid3d: "00000000-0000-4000-8000-000000000202",
  madridVip: "00000000-0000-4000-8000-000000000203",
  barcelonaStandard: "00000000-0000-4000-8000-000000000204",
  barcelona3d: "00000000-0000-4000-8000-000000000205",
  caceresStandard: "00000000-0000-4000-8000-000000000206",
  caceres3d: "00000000-0000-4000-8000-000000000207",
  navatrasierraStandard: "00000000-0000-4000-8000-000000000208",
  madroneraStandard: "00000000-0000-4000-8000-000000000209",
  malagaStandard: "00000000-0000-4000-8000-000000000210",
  malaga3d: "00000000-0000-4000-8000-000000000211",
  cuencaStandard: "00000000-0000-4000-8000-000000000212",
};

const cinemas = [
  {
    _id: cinemaIds.madrid,
    name: "CinAsee Madrid",
    address: "Gran Vía 1",
    city: "Madrid",
    phone: "910000001",
  },
  {
    _id: cinemaIds.barcelona,
    name: "CinAsee Barcelona",
    address: "Passeig de Gràcia 2",
    city: "Barcelona",
    phone: "930000002",
  },
  {
    _id: cinemaIds.caceres,
    name: "CinAsee Cáceres",
    address: "Avenida de España 12",
    city: "Cáceres",
    phone: "927000003",
  },
  {
    _id: cinemaIds.navatrasierra,
    name: "CinAsee Navatrasierra",
    address: "Calle Real 5",
    city: "Navatrasierra",
    phone: "927000004",
  },
  {
    _id: cinemaIds.madronera,
    name: "CinAsee Madroñera",
    address: "Plaza Mayor 3",
    city: "Madroñera",
    phone: "927000005",
  },
  {
    _id: cinemaIds.malaga,
    name: "CinAsee Málaga",
    address: "Calle Marqués de Larios 8",
    city: "Málaga",
    phone: "952000006",
  },
  {
    _id: cinemaIds.cuenca,
    name: "CinAsee Cuenca",
    address: "Calle Carretería 14",
    city: "Cuenca",
    phone: "969000007",
  },
];

const movies = [
  {
    _id: movieIds.ponyo,
    title: "Ponyo en el acantilado",
    synopsis: "Ponyo, una pez mágica que sueña con convertirse en humana, conoce a un niño llamado Sosuke. Su amistad desencadena una aventura que pondrá en peligro el equilibrio entre el mar y la tierra.",
    durationMinutes: 101,
    genre: "ANIMACION",
    ageRating: "TP",
    posterUrl: "http://localhost:8080/media/posters/ponyo.jpg",
    trailerUrl: "https://www.youtube.com/watch?v=CsR3KVgBzSM",
  },
  {
    _id: movieIds.howlsMovingCastle,
    title: "El castillo ambulante",
    synopsis: "La joven Sophie es víctima de una maldición que la convierte en una anciana. En su búsqueda por romper el hechizo, encuentra refugio en el misterioso castillo ambulante del mago Howl, donde deberá enfrentarse a una guerra, descubrir secretos mágicos y encontrar su verdadera fortaleza.",
    durationMinutes: 119,
    genre: "FANTASIA",
    ageRating: "+7",
    posterUrl: "http://localhost:8080/media/posters/castillo-ambulante.jpg",
    trailerUrl: "https://www.youtube.com/watch?v=iwROgK94zcM",
  },
  {
    _id: movieIds.everythingEverywhereAllAtOnce,
    title: "Todo a la vez en todas partes",
    synopsis: "Evelyn, una inmigrante china que dirige una lavandería, descubre que debe conectar con versiones alternativas de sí misma a través del multiverso para detener una amenaza que podría destruir todas las realidades.",
    durationMinutes: 139,
    genre: "CIENCIA_FICCION",
    ageRating: "+16",
    posterUrl: "http://localhost:8080/media/posters/todo-a-la-vez.jpg",
    trailerUrl: "https://youtu.be/U3cKYWgl2dU?si=OAk3oCJ-g4xQc5xl",
  },
  {
    _id: movieIds.interstellar,
    title: "Interstellar",
    synopsis: "Un grupo de exploradores viaja a través de un agujero de gusano en el espacio en un intento por garantizar la supervivencia de la humanidad.",
    durationMinutes: 169,
    genre: "CIENCIA_FICCION",
    ageRating: "+12",
    posterUrl: "http://localhost:8080/media/posters/interstellar.jpg",
    trailerUrl: "https://youtu.be/UoSSbmD9vqc?si=IfXv_XxghC1jfzzt",
  },
  {
    _id: movieIds.prideAndPrejudice,
    title: "Orgullo y prejuicio",
    synopsis: "En la Inglaterra del siglo XVIII, las cinco hermanas Bennet lidian con cuestiones de matrimonio, moralidad y prejuicios cuando el adinerado Sr. Bingley y el orgulloso Sr. Darcy llegan a su vecindario.",
    durationMinutes: 129,
    genre: "ROMANCE",
    ageRating: "TP",
    posterUrl: "http://localhost:8080/media/posters/orgullo-y-prejuicio.jpg",
    trailerUrl: "https://www.youtube.com/watch?v=1dYv5u6v55Y",
  },
  {
    _id: movieIds.harryPotterAzkaban,
    title: "Harry Potter y el prisionero de Azkaban",
    synopsis: "En su tercer año en Hogwarts, Harry Potter se enfrenta a la amenaza de Sirius Black, un peligroso prisionero que ha escapado de la fortaleza de Azkaban y parece ir tras él.",
    durationMinutes: 142,
    genre: "FANTASIA",
    ageRating: "+7",
    posterUrl: "http://localhost:8080/media/posters/harry-potter-azkaban.jpg",
    trailerUrl: "https://www.youtube.com/watch?v=lAxgztbYDbs",
  },
  {
    _id: movieIds.velocipastor,
    title: "The VelociPastor",
    synopsis: "Tras perder a sus padres, un sacerdote viaja a China, donde hereda una misteriosa habilidad que le permite convertirse en dinosaurio para luchar contra el crimen y los ninjas.",
    durationMinutes: 75,
    genre: "COMEDIA",
    ageRating: "+16",
    posterUrl: "http://localhost:8080/media/posters/velocipastor.jpg",
    trailerUrl: "https://youtu.be/D7wwoCBtvaA?si=QzRVMl1EPrNtQ8AF",
  },
  {
    _id: movieIds.hungerGamesSunrise,
    title: "Los juegos del hambre: Amanecer en la cosecha",
    synopsis: "Veinticuatro años antes de los eventos de la trilogía original, en la mañana de la cosecha de los Quincuagésimos Juegos del Hambre, un joven Haymitch Abernathy es seleccionado como tributo del Distrito 12 para el Segundo Vasallaje de los Veinticinco.",
    durationMinutes: 145,
    genre: "CIENCIA_FICCION",
    ageRating: "+12",
    posterUrl: "http://localhost:8080/media/posters/juegos-del-hambre.jpg",
    trailerUrl: "https://youtu.be/6jWXR47FCUQ?si=XLlzjCMqx1-nozNd",
  },
];

const rooms = [
  {
    _id: roomIds.madridStandard,
    cinemaId: cinemaIds.madrid,
    name: "Sala 1 - Estándar",
    roomType: "ESTANDAR_2D",
    rowsCount: 10,
    columnsCount: 12,
    capacity: 120,
    wheelchairAccessible: true,
    basePrice: 8.5,
  },
  {
    _id: roomIds.madrid3d,
    cinemaId: cinemaIds.madrid,
    name: "Sala 2 - 3D",
    roomType: "DIGITAL_3D",
    rowsCount: 8,
    columnsCount: 10,
    capacity: 80,
    wheelchairAccessible: true,
    basePrice: 10.5,
  },
  {
    _id: roomIds.madridVip,
    cinemaId: cinemaIds.madrid,
    name: "Sala 3 - VIP",
    roomType: "VIP",
    rowsCount: 6,
    columnsCount: 8,
    capacity: 48,
    wheelchairAccessible: true,
    basePrice: 14,
  },
  {
    _id: roomIds.barcelonaStandard,
    cinemaId: cinemaIds.barcelona,
    name: "Sala 1 - Estándar",
    roomType: "ESTANDAR_2D",
    rowsCount: 10,
    columnsCount: 12,
    capacity: 120,
    wheelchairAccessible: true,
    basePrice: 8.5,
  },
  {
    _id: roomIds.barcelona3d,
    cinemaId: cinemaIds.barcelona,
    name: "Sala 2 - 3D",
    roomType: "DIGITAL_3D",
    rowsCount: 8,
    columnsCount: 10,
    capacity: 80,
    wheelchairAccessible: true,
    basePrice: 10.5,
  },
  {
    _id: roomIds.caceresStandard,
    cinemaId: cinemaIds.caceres,
    name: "Sala 1 - Estándar",
    roomType: "ESTANDAR_2D",
    rowsCount: 9,
    columnsCount: 10,
    capacity: 90,
    wheelchairAccessible: true,
    basePrice: 8.0,
  },
  {
    _id: roomIds.caceres3d,
    cinemaId: cinemaIds.caceres,
    name: "Sala 2 - 3D",
    roomType: "DIGITAL_3D",
    rowsCount: 7,
    columnsCount: 10,
    capacity: 70,
    wheelchairAccessible: true,
    basePrice: 10.0,
  },
  {
    _id: roomIds.navatrasierraStandard,
    cinemaId: cinemaIds.navatrasierra,
    name: "Sala 1 - Estándar",
    roomType: "ESTANDAR_2D",
    rowsCount: 6,
    columnsCount: 8,
    capacity: 48,
    wheelchairAccessible: true,
    basePrice: 7.0,
  },
  {
    _id: roomIds.madroneraStandard,
    cinemaId: cinemaIds.madronera,
    name: "Sala 1 - Estándar",
    roomType: "ESTANDAR_2D",
    rowsCount: 6,
    columnsCount: 8,
    capacity: 48,
    wheelchairAccessible: true,
    basePrice: 7.0,
  },
  {
    _id: roomIds.malagaStandard,
    cinemaId: cinemaIds.malaga,
    name: "Sala 1 - Estándar",
    roomType: "ESTANDAR_2D",
    rowsCount: 10,
    columnsCount: 12,
    capacity: 120,
    wheelchairAccessible: true,
    basePrice: 8.5,
  },
  {
    _id: roomIds.malaga3d,
    cinemaId: cinemaIds.malaga,
    name: "Sala 2 - 3D",
    roomType: "DIGITAL_3D",
    rowsCount: 8,
    columnsCount: 10,
    capacity: 80,
    wheelchairAccessible: true,
    basePrice: 10.5,
  },
  {
    _id: roomIds.cuencaStandard,
    cinemaId: cinemaIds.cuenca,
    name: "Sala 1 - Estándar",
    roomType: "ESTANDAR_2D",
    rowsCount: 8,
    columnsCount: 10,
    capacity: 80,
    wheelchairAccessible: true,
    basePrice: 8.0,
  },
];

const sessionIds = [
  "01M7SR8QHR0000000000000001",
  "01M7SR8QHR0000000000000002",
  "01M7SR8QHR0000000000000003",
  "01M7SR8QHR0000000000000004",
  "01M7SR8QHR0000000000000005",
  "01M7SR8QHR0000000000000006",
  "01M7SR8QHR0000000000000007",
  "01M7SR8QHR0000000000000008",
  "01M7SR8QHR0000000000000009",
  "01M7SR8QHR000000000000000A",
  "01M7SR8QHR000000000000000B",
  "01M7SR8QHR000000000000000C",
  "01M7SR8QHR000000000000000D",
  "01M7SR8QHR000000000000000E",
  "01M7SR8QHR000000000000000F",
  "01M7SR8QHR000000000000000G",
  "01M7SR8QHR000000000000000H",
  "01M7SR8QHR000000000000000J",
  "01M7SR8QHR000000000000000K",
  "01M7SR8QHR000000000000000M",
  "01M7SR8QHR000000000000000N",
  "01M7SR8QHR000000000000000P",
  "01M7SR8QHR000000000000000Q",
  "01M7SR8QHR000000000000000R",
  "01M7SR8QHR000000000000000S",
  "01M7SR8QHR000000000000000T",
  "01M7SR8QHR000000000000000V",
  "01M7SR8QHR000000000000000W",
  "01M7SR8QHR000000000000000X",
  "01M7SR8QHR000000000000000Y",
  "01M7SR8QHR000000000000000Z",
  "01M7SR8QHR0000000000000010",
  "01M7SR8QHR0000000000000011",
  "01M7SR8QHR0000000000000012",
  "01M7SR8QHR0000000000000013",
  "01M7SR8QHR0000000000000014",
  "01M7SR8QHR0000000000000015",
  "01M7SR8QHR0000000000000016",
  "01M7SR8QHR0000000000000017",
  "01M7SR8QHR0000000000000018",
  "01M7SR8QHR0000000000000019",
  "01M7SR8QHR000000000000001A",
  "01M7SR8QHR000000000000001B",
  "01M7SR8QHR000000000000001C",
  "01M7SR8QHR000000000000001D",
  "01M7SR8QHR000000000000001E",
  "01M7SR8QHR000000000000001F",
  "01M7SR8QHR000000000000001G",
];

const movieById = new Map(movies.map((movie) => [movie._id, movie]));
const roomById = new Map(rooms.map((room) => [room._id, room]));

const schedule = [
  // Madrid (18 sesiones)
  [1, 16, 0, movieIds.velocipastor, roomIds.madridStandard, 8.5],
  [1, 19, 0, movieIds.interstellar, roomIds.madridStandard, 8.5],
  [1, 17, 0, movieIds.ponyo, roomIds.madrid3d, 10.5],
  [1, 20, 0, movieIds.hungerGamesSunrise, roomIds.madrid3d, 10.5],
  [1, 17, 30, movieIds.howlsMovingCastle, roomIds.madridVip, 14],
  [1, 20, 0, movieIds.everythingEverywhereAllAtOnce, roomIds.madridVip, 14],
  [2, 16, 0, movieIds.howlsMovingCastle, roomIds.madridStandard, 8.5],
  [2, 19, 0, movieIds.prideAndPrejudice, roomIds.madridStandard, 8.5],
  [2, 17, 0, movieIds.velocipastor, roomIds.madrid3d, 10.5],
  [2, 20, 0, movieIds.interstellar, roomIds.madrid3d, 10.5],
  [2, 17, 30, movieIds.ponyo, roomIds.madridVip, 14],
  [2, 20, 0, movieIds.harryPotterAzkaban, roomIds.madridVip, 14],
  [3, 16, 0, movieIds.ponyo, roomIds.madridStandard, 8.5],
  [3, 19, 0, movieIds.everythingEverywhereAllAtOnce, roomIds.madridStandard, 8.5],
  [3, 17, 0, movieIds.prideAndPrejudice, roomIds.madrid3d, 10.5],
  [3, 20, 0, movieIds.harryPotterAzkaban, roomIds.madrid3d, 10.5],
  [3, 17, 30, movieIds.velocipastor, roomIds.madridVip, 14],
  [3, 20, 0, movieIds.hungerGamesSunrise, roomIds.madridVip, 14],

  // Barcelona (8 sesiones)
  [1, 16, 30, movieIds.ponyo, roomIds.barcelonaStandard, 8.5],
  [1, 19, 30, movieIds.hungerGamesSunrise, roomIds.barcelonaStandard, 8.5],
  [1, 17, 0, movieIds.howlsMovingCastle, roomIds.barcelona3d, 10.5],
  [1, 20, 0, movieIds.interstellar, roomIds.barcelona3d, 10.5],
  [2, 17, 0, movieIds.harryPotterAzkaban, roomIds.barcelonaStandard, 8.5],
  [2, 20, 15, movieIds.everythingEverywhereAllAtOnce, roomIds.barcelonaStandard, 8.5],
  [2, 18, 0, movieIds.velocipastor, roomIds.barcelona3d, 10.5],
  [2, 20, 0, movieIds.prideAndPrejudice, roomIds.barcelona3d, 10.5],

  // Cáceres (6 sesiones)
  [1, 17, 0, movieIds.prideAndPrejudice, roomIds.caceresStandard, 8.0],
  [1, 20, 0, movieIds.harryPotterAzkaban, roomIds.caceresStandard, 8.0],
  [1, 18, 0, movieIds.everythingEverywhereAllAtOnce, roomIds.caceres3d, 10.0],
  [2, 17, 30, movieIds.ponyo, roomIds.caceresStandard, 8.0],
  [2, 20, 0, movieIds.interstellar, roomIds.caceresStandard, 8.0],
  [2, 18, 30, movieIds.hungerGamesSunrise, roomIds.caceres3d, 10.0],

  // Navatrasierra (3 sesiones)
  [1, 18, 0, movieIds.ponyo, roomIds.navatrasierraStandard, 7.0],
  [1, 20, 30, movieIds.velocipastor, roomIds.navatrasierraStandard, 7.0],
  [2, 19, 0, movieIds.howlsMovingCastle, roomIds.navatrasierraStandard, 7.0],

  // Madroñera (3 sesiones)
  [1, 18, 30, movieIds.prideAndPrejudice, roomIds.madroneraStandard, 7.0],
  [2, 17, 30, movieIds.velocipastor, roomIds.madroneraStandard, 7.0],
  [2, 19, 30, movieIds.hungerGamesSunrise, roomIds.madroneraStandard, 7.0],

  // Málaga (7 sesiones)
  [1, 16, 30, movieIds.howlsMovingCastle, roomIds.malagaStandard, 8.5],
  [1, 19, 30, movieIds.everythingEverywhereAllAtOnce, roomIds.malagaStandard, 8.5],
  [1, 17, 30, movieIds.harryPotterAzkaban, roomIds.malaga3d, 10.5],
  [1, 20, 45, movieIds.interstellar, roomIds.malaga3d, 10.5],
  [2, 17, 0, movieIds.ponyo, roomIds.malagaStandard, 8.5],
  [2, 19, 30, movieIds.prideAndPrejudice, roomIds.malagaStandard, 8.5],
  [2, 18, 0, movieIds.hungerGamesSunrise, roomIds.malaga3d, 10.5],

  // Cuenca (3 sesiones)
  [1, 17, 0, movieIds.ponyo, roomIds.cuencaStandard, 8.0],
  [1, 19, 30, movieIds.interstellar, roomIds.cuencaStandard, 8.0],
  [2, 18, 0, movieIds.harryPotterAzkaban, roomIds.cuencaStandard, 8.0],
];

if (schedule.length !== sessionIds.length) {
  throw new Error(
    `Mismatch between schedule entries (${schedule.length}) and sessionIds (${sessionIds.length}).`,
  );
}

function utcStartTime(daysFromToday, hour, minute) {
  const start = new Date();
  start.setUTCDate(start.getUTCDate() + daysFromToday);
  start.setUTCHours(hour, minute, 0, 0);
  return start;
}

const sessions = schedule.map(
  ([day, hour, minute, movieId, roomId, ticketPrice], index) => {
    const movie = movieById.get(movieId);
    const room = roomById.get(roomId);
    if (!movie || !room) {
      throw new Error(`Session ${index + 1} references a missing movie or room.`);
    }
    if (ticketPrice < room.basePrice) {
      throw new Error(`Session ${index + 1} ticket price is below the room base price.`);
    }

    const startTime = utcStartTime(day, hour, minute);
    return {
      _id: sessionIds[index],
      movieId,
      roomId,
      startTime,
      endTime: new Date(startTime.getTime() + (movie.durationMinutes + 20) * 60_000),
      status: "A_LA_VENTA",
      ticketPrice,
    };
  },
);

// Cartelera para dos semanas: del día 4 al 14 se repiten los horarios del día 1 y del
// día 2 (alternos), igual que en CatalogSeeder. Cada copia tiene un id fijo (el original
// con el número de día), así que volver a ejecutar el script no duplica nada.
const DIAS_DE_CARTELERA = 14;

function idDeCopia(idOriginal, dia) {
  return `01M7SR8QHS${String(dia).padStart(2, "0")}${idOriginal.substring(12)}`;
}

for (let dia = 4; dia <= DIAS_DE_CARTELERA; dia++) {
  const diaPlantilla = dia % 2 === 0 ? 2 : 1;
  schedule.forEach(([day, hour, minute, movieId, roomId, ticketPrice], index) => {
    if (day !== diaPlantilla) {
      return;
    }
    const movie = movieById.get(movieId);
    const startTime = utcStartTime(dia, hour, minute);
    sessions.push({
      _id: idDeCopia(sessionIds[index], dia),
      movieId,
      roomId,
      startTime,
      endTime: new Date(startTime.getTime() + (movie.durationMinutes + 20) * 60_000),
      status: "A_LA_VENTA",
      ticketPrice,
    });
  });
}

function insertIfMissing(collectionName, documents) {
  const collection = db.getCollection(collectionName);
  let inserted = 0;
  let alreadyPresent = 0;

  for (const document of documents) {
    const result = collection.updateOne(
      { _id: document._id },
      { $setOnInsert: document },
      { upsert: true },
    );
    if (result.upsertedCount === 1) {
      inserted++;
    } else {
      alreadyPresent++;
    }
  }

  print(
    `${collectionName}: inserted ${inserted}, already present ${alreadyPresent}`,
  );
}

insertIfMissing("cinemas", cinemas);
insertIfMissing("movies", movies);
insertIfMissing("rooms", rooms);
insertIfMissing("sessions", sessions);
// Corrige las películas creadas con el seed anterior (géneros con tilde y edad "+0"),
// con las mismas reglas que aplica el Componente 1 al crear o editar una película.
function normalizarGenero(genero) {
  return genero
    .trim()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toUpperCase()
    .replace(/[\s-]+/g, "_");
}

function normalizarEdad(edad) {
  const limpia = edad.trim().toUpperCase();
  return limpia === "+0" || limpia === "0" ? "TP" : limpia;
}

let corregidas = 0;
db.getCollection("movies").find({}).forEach((movie) => {
  const genre = typeof movie.genre === "string" ? normalizarGenero(movie.genre) : movie.genre;
  const ageRating = typeof movie.ageRating === "string" ? normalizarEdad(movie.ageRating) : movie.ageRating;
  if (genre !== movie.genre || ageRating !== movie.ageRating) {
    db.getCollection("movies").updateOne({ _id: movie._id }, { $set: { genre, ageRating } });
    corregidas++;
  }
});
print(`movies: corregidas ${corregidas} (género o edad antiguos)`);
