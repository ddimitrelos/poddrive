import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Νέες Ταινίες | Cinema Guide",
  description: "Κινηματογραφικές κυκλοφορίες αυτής της εβδομάδας",
};

// ─── Types ────────────────────────────────────────────────────────────────────

interface MovieData {
  title: string;
  alternateName: string;
  duration: string;
  year: string;
  rating: number; // 0–5, half-star precision
  url: string;
  country: string;
}

// ─── JSON-LD Parser ───────────────────────────────────────────────────────────

function parseJsonLd(html: string): unknown[] {
  const results: unknown[] = [];
  const re =
    /<script[^>]+type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(html)) !== null) {
    try {
      const parsed = JSON.parse(m[1]);
      if (Array.isArray(parsed)) results.push(...parsed);
      else results.push(parsed);
    } catch {
      // skip malformed blocks
    }
  }
  return results;
}

// ─── Duration Formatter ───────────────────────────────────────────────────────

function parseDuration(raw: unknown): string {
  if (!raw || typeof raw !== "string") return "—";

  // ISO 8601: PT2H36M / PT99M
  const iso = raw.match(/P(?:\d+D)?T(?:(\d+)H)?(?:(\d+)M)?/i);
  if (iso) {
    const h = iso[1] ? parseInt(iso[1], 10) : 0;
    const min = iso[2] ? parseInt(iso[2], 10) : 0;
    if (h > 0 && min > 0) return `${h}ω ${min}'`;
    if (h > 0) return `${h}ω`;
    if (min > 0) return `${min}'`;
    return "—";
  }

  // Raw minutes: "99'" or "99"
  const rawMin = raw.match(/^(\d+)/);
  if (rawMin) {
    const total = parseInt(rawMin[1], 10);
    const h = Math.floor(total / 60);
    const min = total % 60;
    if (h > 0 && min > 0) return `${h}ω ${min}'`;
    if (h > 0) return `${h}ω`;
    return `${min}'`;
  }

  return raw;
}

// ─── Year Extractor ───────────────────────────────────────────────────────────
// Handles formats: "2026", "19/3/2026 12:00:00 πμ", "2026-03-19", etc.

function extractYear(raw: unknown): string {
  if (!raw || typeof raw !== "string") return "—";
  const match = raw.match(/\b(19|20)\d{2}\b/);
  return match ? match[0] : "—";
}

// ─── Detail Page Fetcher ──────────────────────────────────────────────────────

async function fetchMovieDetail(
  movieUrl: string
): Promise<{ country: string; duration: string }> {
  try {
    const res = await fetch(movieUrl, {
      next: { revalidate: 3600 },
      headers: { "User-Agent": "Mozilla/5.0 (compatible; NextBot/1.0)" },
    });
    if (!res.ok) return { country: "", duration: "—" };
    const html = await res.text();

    for (const block of parseJsonLd(html)) {
      if (!block || typeof block !== "object") continue;
      const obj = block as Record<string, unknown>;
      const candidates: unknown[] = Array.isArray(obj["@graph"])
        ? (obj["@graph"] as unknown[])
        : [obj];

      for (const c of candidates) {
        if (!c || typeof c !== "object") continue;
        const item = c as Record<string, unknown>;
        if (item["@type"] !== "Movie") continue;

        // Duration
        const duration = parseDuration(item["duration"]);

        // Country
        let country = "";
        const coo = item["countryOfOrigin"];
        if (coo && typeof coo === "object" && !Array.isArray(coo)) {
          const name = (coo as Record<string, unknown>)["name"];
          if (typeof name === "string") country = name;
        } else if (Array.isArray(coo) && coo.length > 0) {
          const name = (coo[0] as Record<string, unknown>)["name"];
          if (typeof name === "string") country = name;
        }

        if (duration !== "—" || country) return { duration, country };
      }
    }
  } catch {
    // network / parse failure
  }
  return { country: "", duration: "—" };
}

// ─── Main Data Fetcher ────────────────────────────────────────────────────────

async function getMovies(): Promise<MovieData[]> {
  let html: string;
  try {
    const res = await fetch(
      "https://www.athinorama.gr/cinema/guide/nees_tainies/",
      {
        next: { revalidate: 3600 },
        headers: { "User-Agent": "Mozilla/5.0 (compatible; NextBot/1.0)" },
      }
    );
    if (!res.ok) return [];
    html = await res.text();
  } catch {
    return [];
  }

  // The listing page uses: { "@type": "ItemList", "itemListElement": [ { "@type": "ListItem", "item": { "@type": "Movie", ... } } ] }
  const raw: Record<string, unknown>[] = [];

  for (const block of parseJsonLd(html)) {
    if (!block || typeof block !== "object") continue;
    const obj = block as Record<string, unknown>;

    if (
      obj["@type"] === "ItemList" &&
      Array.isArray(obj["itemListElement"])
    ) {
      for (const element of obj["itemListElement"] as unknown[]) {
        if (!element || typeof element !== "object") continue;
        const listItem = element as Record<string, unknown>;
        const item = listItem["item"];
        if (!item || typeof item !== "object") continue;
        const movie = item as Record<string, unknown>;
        if (movie["@type"] === "Movie") raw.push(movie);
      }
    }
  }

  // Fan-out: fetch all detail pages in parallel
  return Promise.all(
    raw.map(async (item): Promise<MovieData> => {
      const title =
        typeof item["name"] === "string" ? item["name"] : "Άγνωστος τίτλος";

      const alternateName =
        typeof item["alternateName"] === "string" ? item["alternateName"] : "";

      const year = extractYear(
        item["dateCreated"] ?? item["datePublished"]
      );

      let rating = 0;
      if (
        item["aggregateRating"] &&
        typeof item["aggregateRating"] === "object"
      ) {
        const ar = item["aggregateRating"] as Record<string, unknown>;
        const rv = ar["ratingValue"];
        if (typeof rv === "number") rating = Math.round(rv * 2) / 2;
        else if (typeof rv === "string")
          rating = Math.round(parseFloat(rv) * 2) / 2;
      }

      const rawUrl =
        typeof item["url"] === "string"
          ? item["url"]
          : typeof item["@id"] === "string"
          ? item["@id"]
          : "";

      const movieUrl = rawUrl.startsWith("http")
        ? rawUrl
        : rawUrl
        ? `https://www.athinorama.gr${rawUrl}`
        : "";

      const { country, duration } = movieUrl
        ? await fetchMovieDetail(movieUrl)
        : { country: "", duration: "—" };

      return { title, alternateName, duration, year, rating, url: movieUrl, country };
    })
  );
}

// ─── Star Rating ──────────────────────────────────────────────────────────────

function StarRating({ rating }: { rating: number }) {
  const clamped = Math.max(0, Math.min(5, rating));
  const stars = Array.from({ length: 5 }, (_, i) => {
    const pos = i + 1;
    if (clamped >= pos) return "full";
    if (clamped >= pos - 0.5) return "half";
    return "empty";
  });

  return (
    <div
      className="flex items-center gap-0.5"
      aria-label={`Βαθμολογία: ${rating}/5`}
      title={`${rating}/5`}
    >
      {stars.map((type, i) => (
        <svg key={i} viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true">
          <defs>
            <linearGradient id={`star-g-${i}-${rating}`} x1="0" x2="1" y1="0" y2="0">
              {type === "full" && <stop offset="100%" stopColor="#facc15" />}
              {type === "half" && (
                <>
                  <stop offset="50%" stopColor="#facc15" />
                  <stop offset="50%" stopColor="#3f3f46" />
                </>
              )}
              {type === "empty" && <stop offset="100%" stopColor="#3f3f46" />}
            </linearGradient>
          </defs>
          <path
            d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"
            fill={`url(#star-g-${i}-${rating})`}
          />
        </svg>
      ))}
      <span className="ml-1.5 text-xs text-zinc-400">{rating}/5</span>
    </div>
  );
}

// ─── Movie Card ───────────────────────────────────────────────────────────────

function MovieCard({ movie }: { movie: MovieData }) {
  const inner = (
    <div className="group flex h-full flex-col gap-3 rounded-2xl bg-zinc-900 p-5 shadow-lg transition-colors hover:bg-zinc-800">
      {/* Title */}
      <div>
        <h2 className="text-sm font-semibold leading-snug text-white">
          {movie.title}
        </h2>
        {movie.alternateName && movie.alternateName !== movie.title && (
          <p className="mt-0.5 text-xs text-zinc-500 italic">
            {movie.alternateName}
          </p>
        )}
      </div>

      {/* Meta pills */}
      <div className="flex flex-wrap gap-1.5">
        {movie.country && (
          <span className="rounded-full bg-zinc-800 px-2.5 py-0.5 text-xs text-zinc-300 group-hover:bg-zinc-700">
            {movie.country}
          </span>
        )}
        {movie.year !== "—" && (
          <span className="rounded-full bg-zinc-800 px-2.5 py-0.5 text-xs text-zinc-300 group-hover:bg-zinc-700">
            {movie.year}
          </span>
        )}
        {movie.duration !== "—" && (
          <span className="rounded-full bg-zinc-800 px-2.5 py-0.5 text-xs text-zinc-300 group-hover:bg-zinc-700">
            ⏱ {movie.duration}
          </span>
        )}
      </div>

      {/* Stars */}
      <div className="mt-auto pt-1">
        {movie.rating > 0 ? (
          <StarRating rating={movie.rating} />
        ) : (
          <span className="text-xs text-zinc-600">Χωρίς αξιολόγηση</span>
        )}
      </div>
    </div>
  );

  if (movie.url.startsWith("http")) {
    return (
      <a
        href={movie.url}
        target="_blank"
        rel="noopener noreferrer"
        className="block h-full no-underline"
      >
        {inner}
      </a>
    );
  }
  return <div className="h-full">{inner}</div>;
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default async function MoviesPage() {
  const movies = await getMovies();

  return (
    <main className="min-h-screen bg-zinc-950 px-4 py-12">
      <div className="mx-auto max-w-6xl">
        <div className="mb-10">
          <h1 className="text-3xl font-bold tracking-tight text-white">
            Νέες Ταινίες
          </h1>
          <p className="mt-1 text-sm text-zinc-500">
            Κινηματογραφικές κυκλοφορίες αυτής της εβδομάδας · Πηγή:{" "}
            <a
              href="https://www.athinorama.gr/cinema/guide/nees_tainies/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-zinc-400 underline hover:text-white"
            >
              athinorama.gr
            </a>
          </p>
        </div>

        {movies.length === 0 ? (
          <p className="text-zinc-400">
            Δεν βρέθηκαν ταινίες. Δοκιμάστε ξανά αργότερα.
          </p>
        ) : (
          <>
            <p className="mb-6 text-sm text-zinc-600">
              {movies.length} ταινίες αυτήν την εβδομάδα
            </p>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
              {movies.map((movie, i) => (
                <MovieCard key={`${movie.url}-${i}`} movie={movie} />
              ))}
            </div>
          </>
        )}
      </div>
    </main>
  );
}
