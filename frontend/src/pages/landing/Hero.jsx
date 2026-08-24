import { Button, ArrowRight, Eyebrow } from "./primitives";
import { PatientPreview, NotifChip } from "./mockups";
import Reveal from "./Reveal";

export default function Hero() {
  return (
    <section id="top" className="relative overflow-hidden pt-28 sm:pt-32 lg:pt-36">
      {/* subtle background structure */}
      <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
        <div className="absolute left-1/2 top-0 h-[520px] w-[520px] -translate-x-1/2 rounded-full bg-teal-wash/70 blur-[120px]" />
        <div
          className="absolute inset-0 opacity-[0.5]"
          style={{
            backgroundImage:
              "linear-gradient(var(--color-line) 1px, transparent 1px), linear-gradient(90deg, var(--color-line) 1px, transparent 1px)",
            backgroundSize: "64px 64px",
            maskImage: "radial-gradient(ellipse 80% 55% at 50% 30%, #000 30%, transparent 75%)",
            WebkitMaskImage: "radial-gradient(ellipse 80% 55% at 50% 30%, #000 30%, transparent 75%)",
          }}
        />
      </div>

      <div className="mx-auto grid max-w-[1200px] items-center gap-12 px-5 pb-16 sm:px-8 lg:grid-cols-[1.05fr_0.95fr] lg:gap-10 lg:pb-24">
        {/* Copy */}
        <div>
          <Reveal>
            <span className="inline-flex items-center gap-2 rounded-full border border-line bg-white/70 px-3 py-1.5 backdrop-blur">
              <Eyebrow>Healthcare Appointment &amp; Follow-up Manager</Eyebrow>
            </span>
          </Reveal>

          <Reveal delay={80}>
            <h1 className="mt-6 font-display text-[42px] font-extrabold leading-[1.02] tracking-[-0.02em] text-ink sm:text-[56px] lg:text-[64px]">
              Healthcare,
              <br />
              without the <span className="text-teal">waiting.</span>
            </h1>
          </Reveal>

          <Reveal delay={160}>
            <p className="mt-6 max-w-[520px] text-[16px] leading-relaxed text-ink-soft sm:text-[17px]">
              Veyra makes it easier for patients to find doctors and manage appointments, while
              giving doctors a clear view of their schedules and visits — in one connected place.
            </p>
          </Reveal>

          <Reveal delay={240}>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Button href="/patient" variant="primary" className="px-6 py-3">
                Book an Appointment <ArrowRight />
              </Button>
              <Button href="#how-it-works" variant="outline" className="px-6 py-3">
                See how it works
              </Button>
            </div>
          </Reveal>

          <Reveal delay={320}>
            <div className="mt-9 flex items-center gap-6 text-[13px] text-ink-faint">
              <span className="flex items-center gap-2">
                <Dot /> Patients
              </span>
              <span className="flex items-center gap-2">
                <Dot /> Doctors
              </span>
              <span className="flex items-center gap-2">
                <Dot /> Administrators
              </span>
            </div>
          </Reveal>
        </div>

        {/* Product visual */}
        <Reveal delay={200} className="relative mx-auto w-full max-w-[440px]">
          <div className="float-soft-slow">
            <PatientPreview />
          </div>
          <NotifChip
            title="Appointment confirmed"
            time="Just now"
            className="float-soft absolute -left-4 -top-5 hidden sm:flex"
          />
          <NotifChip
            title="Next appointment — 9:00 PM"
            time="Wed, Aug 26"
            tone="amber"
            className="float-soft absolute -bottom-6 -right-3 hidden sm:flex"
          />
          <NotifChip
            title="Follow-up reminder"
            time="In 3 days"
            className="float-soft-slow absolute -right-6 top-1/3 hidden lg:flex"
          />
        </Reveal>
      </div>
    </section>
  );
}

function Dot() {
  return <span className="h-1.5 w-1.5 rounded-full" style={{ background: "var(--color-teal)" }} />;
}