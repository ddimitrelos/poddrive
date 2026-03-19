import Image from "next/image";

// ─────────────────────────────────────────────────────────────────────────────
// EDITABLE CONTENT — update the values below to personalise the site.
// No need to touch any of the JSX further down.
// ─────────────────────────────────────────────────────────────────────────────

const NAME = "Dimitris Dimitrelos"; // ← EDIT: your full name

const PHOTO = "https://scrumorg-website-prod.s3.amazonaws.com/drupal/pictures/2020-08/DD_smaller_size-whitebg.png"; // ← EDIT: replace with your own photo URL or "/photo.jpg" for a local file

const ROLE = "Business Agility Practice Lead · Professional Scrum Trainer"; // ← EDIT: your title / tagline

const ABOUT: string[] = [
  // ← EDIT: replace or add paragraphs as needed
  "I have spent more than 20 years in software — starting as a developer, moving through project management, and eventually finding my home in Agile coaching and organisational transformation.",
  "Since 2011 I have been practising, teaching, and advocating for Scrum and Agile ways of working. At Accenture Greece, where I joined in 2016, I lead the Business Agility practice, helping organisations in Finance, Telecoms, and beyond shift to product-based operating models.",
  "I am a Professional Scrum Trainer (PST) with Scrum.org, a SAFe Practice Consultant, and a co-organiser of the Agile Greece Summit and Agile Greece Retreat. I have delivered 200+ training courses and spoken at events across the Greek Agile community.",
];

const CONTACT = {
  email: "your.email@example.com", // ← EDIT: your email address
  linkedin: "https://gr.linkedin.com/in/dimitrisdimitrelos", // ← EDIT: your LinkedIn URL
  location: "Athens, Greece", // ← EDIT: your city / country
};


export default function Home() {
  return (
    <div className="min-h-screen flex flex-col font-[var(--font-geist-sans)]">
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <header className="sticky top-0 z-10 bg-[var(--background)]/90 backdrop-blur-sm border-b border-gray-100 dark:border-gray-800">
        <div className="max-w-3xl mx-auto px-6 py-4 flex items-center justify-between">
          <span className="font-semibold tracking-tight text-[var(--foreground)]">
            {NAME}
          </span>
          <nav className="flex gap-6 text-sm text-gray-500 dark:text-gray-400">
            <a
              href="#about"
              className="hover:text-[var(--foreground)] transition-colors"
            >
              About
            </a>
            <a
              href="#contact"
              className="hover:text-[var(--foreground)] transition-colors"
            >
              Contact
            </a>
          </nav>
        </div>
      </header>

      <main className="flex-1">
        {/* ── Hero ───────────────────────────────────────────────────────── */}
        <section className="max-w-3xl mx-auto px-6 py-20">
          <div className="flex flex-col sm:flex-row items-center sm:items-start gap-8">
            <Image
              src={PHOTO}
              alt={NAME}
              width={112}
              height={112}
              className="w-28 h-28 shrink-0 rounded-full object-cover"
              priority
            />

            <div className="text-center sm:text-left">
              <h1 className="text-3xl sm:text-4xl font-bold tracking-tight text-[var(--foreground)] leading-tight">
                {NAME}
              </h1>
              <p className="mt-2 text-base text-gray-500 dark:text-gray-400 leading-relaxed">
                {ROLE}
              </p>
              <p className="mt-1 text-sm text-gray-400 dark:text-gray-500">
                {CONTACT.location}
              </p>
            </div>
          </div>
        </section>

        {/* ── About ──────────────────────────────────────────────────────── */}
        <section id="about" className="max-w-3xl mx-auto px-6 py-12 border-t border-gray-100 dark:border-gray-800">
          <h2 className="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-500 mb-6">
            About
          </h2>
          <div className="space-y-4 text-[var(--foreground)] leading-relaxed text-[0.95rem]">
            {ABOUT.map((paragraph, i) => (
              <p key={i}>{paragraph}</p>
            ))}
          </div>
        </section>

        {/* ── Contact ────────────────────────────────────────────────────── */}
        <section id="contact" className="max-w-3xl mx-auto px-6 py-12 border-t border-gray-100 dark:border-gray-800">
          <h2 className="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-500 mb-6">
            Contact
          </h2>
          <ul className="space-y-3 text-sm text-[var(--foreground)]">
            <li className="flex items-center gap-3">
              <span className="text-gray-400 dark:text-gray-500 w-5 text-center" aria-hidden>
                ✉
              </span>
              <a
                href={`mailto:${CONTACT.email}`}
                className="text-indigo-700 dark:text-indigo-400 hover:underline"
              >
                {CONTACT.email}
              </a>
            </li>
            <li className="flex items-center gap-3">
              <span className="text-gray-400 dark:text-gray-500 w-5 text-center" aria-hidden>
                in
              </span>
              <a
                href={CONTACT.linkedin}
                target="_blank"
                rel="noopener noreferrer"
                className="text-indigo-700 dark:text-indigo-400 hover:underline"
              >
                LinkedIn
              </a>
            </li>
            <li className="flex items-center gap-3">
              <span className="text-gray-400 dark:text-gray-500 w-5 text-center" aria-hidden>
                ⌖
              </span>
              <span>{CONTACT.location}</span>
            </li>
          </ul>
        </section>
      </main>

      {/* ── Footer ─────────────────────────────────────────────────────── */}
      <footer className="max-w-3xl mx-auto w-full px-6 py-6 border-t border-gray-100 dark:border-gray-800">
        <p className="text-xs text-gray-400 dark:text-gray-500">
          &copy; {new Date().getFullYear()} {NAME}
        </p>
      </footer>
    </div>
  );
}
