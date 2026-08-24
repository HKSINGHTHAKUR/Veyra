import { Eyebrow, Button, ArrowRight, Logo } from "./primitives";
import Reveal from "./Reveal";

/* ------------------------------------------------------------------ */
/* How it works — 3 steps with connectors                             */
/* ------------------------------------------------------------------ */
const how = [
  { n: "01", t: "Find your doctor", d: "Search doctors and check availability." },
  { n: "02", t: "Book your appointment", d: "Choose a convenient slot and confirm your visit." },
  { n: "03", t: "Manage your care", d: "Track appointments, visit details and follow-ups." },
];

export function HowItWorks() {
  return (
    <section id="how-it-works" className="border-t border-line bg-mist/40">
      <div className="mx-auto max-w-[1200px] px-5 py-16 sm:px-8 lg:py-20">
        <div className="mx-auto max-w-[620px] text-center">
          <Reveal>
            <Eyebrow>How it works</Eyebrow>
          </Reveal>
          <Reveal delay={80}>
            <h2 className="mt-3 font-display text-[28px] font-bold leading-[1.08] tracking-tight text-ink sm:text-[38px]">
              Three steps to organized care.
            </h2>
          </Reveal>
        </div>

        <div className="relative mt-10 grid gap-8 md:grid-cols-3">
          <div aria-hidden className="absolute left-[16%] right-[16%] top-7 hidden h-px bg-line md:block" />
          {how.map((s, i) => (
            <Reveal key={s.n} delay={i * 120} className="relative text-center">
              <div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl border border-line bg-white font-display text-[16px] font-bold text-ink shadow-[0_10px_30px_-18px_rgba(11,21,18,0.4)]">
                {s.n}
              </div>
              <h3 className="mt-4 font-display text-[18px] font-bold tracking-tight text-ink">{s.t}</h3>
              <p className="mx-auto mt-1.5 max-w-[260px] text-[14px] leading-relaxed text-ink-soft">{s.d}</p>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* Smart care (AI) — compact                                          */
/* ------------------------------------------------------------------ */
export function SmartCareSection() {
  return (
    <section className="border-t border-line bg-ink text-white">
      <div className="mx-auto grid max-w-[1200px] items-center gap-10 px-5 py-16 sm:px-8 lg:grid-cols-[1fr_0.9fr] lg:py-20">
        <Reveal>
          <span className="font-mono text-[11px] font-semibold tracking-[0.2em] text-teal-wash">SMART CARE · COMING SOON</span>
          <h2 className="mt-3 font-display text-[28px] font-bold leading-[1.08] tracking-tight sm:text-[38px]">
            Better prepared for every visit.
          </h2>
          <p className="mt-4 max-w-[480px] text-[15.5px] leading-relaxed text-white/70">
            Veyra can organize symptoms and visit context before an appointment, helping patients arrive
            prepared and doctors start with clearer context.
          </p>
        </Reveal>

        <Reveal delay={140}>
          <div className="rounded-[14px] border border-white/12 bg-white/[0.04] p-5 backdrop-blur">
            <p className="font-mono text-[10px] uppercase tracking-[0.2em] text-white/50">Pre-visit summary</p>
            <div className="mt-3 space-y-2.5">
              {[
                { k: "Chief concern", v: "Fever, mild headache — 2 days" },
                { k: "Context", v: "No prior visits this month" },
                { k: "For the doctor", v: "Wants to review symptoms and next steps" },
              ].map((r) => (
                <div key={r.k} className="rounded-xl border border-white/10 bg-white/[0.03] px-4 py-2.5">
                  <p className="font-mono text-[10px] uppercase tracking-wider text-teal-wash/80">{r.k}</p>
                  <p className="mt-0.5 text-[13.5px] text-white/85">{r.v}</p>
                </div>
              ))}
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* Final CTA                                                          */
/* ------------------------------------------------------------------ */
export function FinalCTA() {
  return (
    <section className="mx-auto max-w-[1200px] px-5 py-16 sm:px-8 lg:py-20">
      <Reveal>
        <div className="relative overflow-hidden rounded-[24px] border border-line bg-mist px-6 py-14 text-center sm:px-8">
          <div
            aria-hidden
            className="pointer-events-none absolute inset-0 opacity-60"
            style={{ backgroundImage: "radial-gradient(circle at 50% 0%, var(--color-teal-wash), transparent 60%)" }}
          />
          <div className="relative">
            <h2 className="mx-auto max-w-[620px] font-display text-[30px] font-bold leading-[1.08] tracking-tight text-ink sm:text-[42px]">
              Ready to make your next appointment simpler?
            </h2>
            <p className="mx-auto mt-4 max-w-[520px] text-[16px] leading-relaxed text-ink-soft">
              Find the right doctor, choose a convenient time, and keep your appointments organized with Veyra.
            </p>
            <div className="mt-7 flex flex-wrap justify-center gap-3">
              <Button href="/patient" variant="primary" className="px-6 py-3">
                Book an Appointment <ArrowRight />
              </Button>
              <Button href="/login" variant="outline" className="px-6 py-3">
                Sign in
              </Button>
            </div>
            <p className="mt-6 font-mono text-[11px] tracking-wider text-ink-faint">
              Role-based access · Appointment management · Patient &amp; doctor portals
            </p>
          </div>
        </div>
      </Reveal>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* Footer — minimal                                                   */
/* ------------------------------------------------------------------ */
export function Footer() {
  const links = [
    { label: "Features", href: "#features" },
    { label: "How it works", href: "#how-it-works" },
    { label: "Sign in", href: "/login" },
  ];
  return (
    <footer className="border-t border-line bg-white">
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-5 py-10 sm:px-8 md:flex-row md:items-center md:justify-between">
        <div>
          <Logo />
          <p className="mt-3 text-[13.5px] text-ink-soft">Healthcare Appointment &amp; Follow-up Manager</p>
        </div>
        <nav className="flex flex-wrap items-center gap-6">
          {links.map((l) => (
            <a key={l.label} href={l.href} className="text-[14px] text-ink-soft transition-colors hover:text-ink">
              {l.label}
            </a>
          ))}
        </nav>
      </div>
      <div className="border-t border-line">
        <p className="mx-auto max-w-[1200px] px-5 py-4 font-mono text-[12px] text-ink-faint sm:px-8">© 2026 Veyra</p>
      </div>
    </footer>
  );
}