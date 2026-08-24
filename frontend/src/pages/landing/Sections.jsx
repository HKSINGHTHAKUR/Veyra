import { Eyebrow, Button, ArrowRight } from "./primitives";
import { PatientPreview, DoctorPreview } from "./mockups";
import Reveal from "./Reveal";

/* ------------------------------------------------------------------ */
/* Value strip — one platform, three experiences                       */
/* ------------------------------------------------------------------ */
const roles = [
  { tag: "PATIENTS", body: "Find doctors, book appointments, and manage every visit in one place." },
  { tag: "DOCTORS", body: "Manage schedules, upcoming appointments, and patient visits with clarity." },
  { tag: "ADMINISTRATORS", body: "Oversee users, appointments, and platform activity across the system." },
];

export function ValueStrip() {
  return (
    <section className="border-y border-line bg-mist/50">
      <div className="mx-auto max-w-[1200px] px-5 py-12 sm:px-8 lg:py-16">
        <Reveal>
          <h2 className="font-display text-[24px] font-bold tracking-tight text-ink sm:text-[30px]">
            One platform. Three experiences.
          </h2>
        </Reveal>
        <div className="mt-7 grid gap-4 md:grid-cols-3">
          {roles.map((r, i) => (
            <Reveal key={r.tag} delay={i * 90}>
              <div className="h-full rounded-[14px] border border-line bg-white p-5 transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_20px_50px_-30px_rgba(11,21,18,0.4)]">
                <span className="font-mono text-[11px] font-semibold tracking-[0.2em] text-teal">{r.tag}</span>
                <p className="mt-2.5 text-[14.5px] leading-relaxed text-ink-soft">{r.body}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* Features grid — 3 x 2                                                */
/* ------------------------------------------------------------------ */
const icons = {
  calendar: (
    <path d="M4 2v3M14 2v3M2.5 7h13M4 4h10a1.5 1.5 0 011.5 1.5V15A1.5 1.5 0 0114 16.5H4A1.5 1.5 0 012.5 15V5.5A1.5 1.5 0 014 4z" />
  ),
  clock: <><circle cx="9" cy="9" r="6.5" /><path d="M9 5.5V9l2.5 1.5" /></>,
  edit: <path d="M12 3l3 3-8.5 8.5-3.5 1 1-3.5L12 3z" />,
  users: <><circle cx="6.5" cy="6" r="2.5" /><path d="M2.5 15c0-2.2 1.8-4 4-4s4 1.8 4 4M12 4.5a2.5 2.5 0 010 5M15.5 15c0-1.7-1-3.1-2.5-3.7" /></>,
  bell: <path d="M5 8a4 4 0 018 0c0 3 1.5 4 1.5 4h-11S5 11 5 8zM7.5 15a1.6 1.6 0 003 0" />,
  spark: <path d="M9 2l1.6 4.4L15 8l-4.4 1.6L9 14l-1.6-4.4L3 8l4.4-1.6L9 2z" />,
};

function Icon({ path }) {
  return (
    <span className="grid h-10 w-10 place-items-center rounded-xl border border-line bg-mist/60 text-teal-deep">
      <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        {path}
      </svg>
    </span>
  );
}

const features = [
  { title: "Smart appointment booking", body: "Patients find doctors and book available slots.", icon: <Icon path={icons.calendar} /> },
  { title: "Doctor scheduling", body: "Doctors manage availability and their daily schedule.", icon: <Icon path={icons.clock} /> },
  { title: "Appointment management", body: "View, reschedule, or cancel without back-and-forth.", icon: <Icon path={icons.edit} /> },
  { title: "Patient & doctor portals", body: "Role-based experiences, including admin oversight.", icon: <Icon path={icons.users} /> },
  { title: "Centralized notifications", body: "Confirmations, reminders, and updates in one place.", icon: <Icon path={icons.bell} /> },
  { title: "AI pre-visit preparation", body: "Organize symptoms and context ahead of a visit.", soon: true, icon: <Icon path={icons.spark} /> },
];

export function Features() {
  return (
    <section id="features" className="border-t border-line bg-mist/40">
      <div className="mx-auto max-w-[1200px] px-5 py-16 sm:px-8 lg:py-20">
        <Reveal>
          <Eyebrow>Features</Eyebrow>
        </Reveal>
        <Reveal delay={80}>
          <h2 className="mt-3 max-w-[620px] font-display text-[28px] font-bold leading-[1.08] tracking-tight text-ink sm:text-[38px]">
            Everything the visit needs — and nothing it doesn&apos;t.
          </h2>
        </Reveal>

        <div className="mt-9 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((f, i) => (
            <Reveal key={f.title} delay={(i % 3) * 90} className="h-full">
              <article className="flex h-full flex-col rounded-[14px] border border-line bg-white p-5 transition-all duration-300 hover:-translate-y-1 hover:border-ink/15 hover:shadow-[0_24px_60px_-38px_rgba(11,21,18,0.45)]">
                <div className="flex items-center justify-between">
                  {f.icon}
                  {f.soon && (
                    <span className="rounded-full bg-mist-2 px-2.5 py-1 font-mono text-[10px] font-semibold tracking-wider text-ink-faint">
                      COMING SOON
                    </span>
                  )}
                </div>
                <h3 className="mt-4 font-display text-[16px] font-bold tracking-tight text-ink">{f.title}</h3>
                <p className="mt-1.5 text-[13.5px] leading-relaxed text-ink-soft">{f.body}</p>
              </article>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* Patient + Doctor — combined, two columns                            */
/* ------------------------------------------------------------------ */
const patientItems = ["Find doctors", "Check availability", "Book / reschedule / cancel", "View appointments", "Notifications"];
const doctorItems = ["View schedule", "Review appointments", "Manage visits", "View patient context", "Notifications"];

function Check() {
  return (
    <span className="mt-0.5 grid shrink-0 place-items-center rounded-full" style={{ background: "var(--color-teal-wash)", height: "18px", width: "18px" }}>
      <svg width="10" height="10" viewBox="0 0 12 12" fill="none" aria-hidden="true">
        <path d="M2.5 6.2l2.3 2.3L9.5 3.5" stroke="var(--color-teal-deep)" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </span>
  );
}

function SideColumn({
  tag,
  items,
  cta,
  href,
  preview,
}) {
  return (
    <div className="flex h-full flex-col rounded-[14px] border border-line bg-white p-6">
      <span className="font-mono text-[11px] font-semibold tracking-[0.2em] text-teal">{tag}</span>
      <div className="mt-5">{preview}</div>
      <ul className="mt-5 grid gap-2">
        {items.map((t) => (
          <li key={t} className="flex items-start gap-2.5 text-[14px] text-ink-soft">
            <Check /> {t}
          </li>
        ))}
      </ul>
      <div className="mt-6">
        <Button href={href} variant="outline" className="w-full">
          {cta} <ArrowRight />
        </Button>
      </div>
    </div>
  );
}

export function PatientDoctorSection() {
  return (
    <section id="patients" className="mx-auto max-w-[1200px] px-5 py-16 sm:px-8 lg:py-20">
      <div className="max-w-[620px]">
        <Reveal>
          <Eyebrow>The product</Eyebrow>
        </Reveal>
        <Reveal delay={80}>
          <h2 className="mt-3 font-display text-[28px] font-bold leading-[1.08] tracking-tight text-ink sm:text-[38px]">
            Built for both sides of the appointment.
          </h2>
        </Reveal>
      </div>

      <div id="doctors" className="mt-9 grid gap-4 lg:grid-cols-2">
        <Reveal>
          <SideColumn
            tag="PATIENTS"
            items={patientItems}
            cta="Book your next appointment"
            href="/patient"
            preview={<PatientPreview />}
          />
        </Reveal>
        <Reveal delay={120}>
          <SideColumn
            tag="DOCTORS"
            items={doctorItems}
            cta="Explore the Doctor Portal"
            href="/doctor"
            preview={<DoctorPreview />}
          />
        </Reveal>
      </div>
    </section>
  );
}