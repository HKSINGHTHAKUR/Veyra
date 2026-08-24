import { StatusBadge } from "./primitives";
import VeyraLogo from "../../components/VeyraLogo";

function Avatar({ initials, tone = "teal" }) {
  return (
    <span
      className="grid h-9 w-9 shrink-0 place-items-center rounded-full font-mono text-[12px] font-semibold"
      style={
        tone === "teal"
          ? { background: "var(--color-teal-wash)", color: "var(--color-teal-deep)" }
          : { background: "var(--color-mist-2)", color: "var(--color-ink-soft)" }
      }
    >
      {initials}
    </span>
  );
}

export function PatientPreview({ className = "" }) {
  return (
    <div className={`overflow-hidden rounded-[14px] border border-line bg-white shadow-[0_24px_60px_-32px_rgba(11,21,18,0.35)] ${className}`}>
      <div className="flex items-center justify-between border-b border-line px-5 py-3.5">
        <div className="flex items-center gap-2">
          <VeyraLogo className="h-4 w-auto" />
          <span className="font-display text-[13px] font-bold tracking-tight text-ink">Veyra</span>
        </div>
        <span className="font-mono text-[10px] uppercase tracking-widest text-ink-faint">Patient</span>
      </div>

      <div className="px-5 py-4">
        <p className="font-mono text-[10px] uppercase tracking-[0.2em] text-ink-faint">My appointments</p>

        <div className="mt-3 rounded-xl border border-line bg-mist/60 p-4">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <Avatar initials="DD" />
              <div>
                <p className="text-[14px] font-semibold text-ink">Dr. Dev Doctor</p>
                <p className="text-[12px] text-ink-soft">General Practice</p>
              </div>
            </div>
            <StatusBadge label="BOOKED" />
          </div>

          <div className="mt-4 grid grid-cols-2 gap-3">
            <div className="rounded-lg bg-white px-3 py-2.5">
              <p className="font-mono text-[10px] uppercase tracking-wider text-ink-faint">Date</p>
              <p className="mt-0.5 text-[13px] font-semibold text-ink">Wed, Aug 26</p>
            </div>
            <div className="rounded-lg bg-white px-3 py-2.5">
              <p className="font-mono text-[10px] uppercase tracking-wider text-ink-faint">Time</p>
              <p className="mt-0.5 text-[13px] font-semibold text-ink">9:00 PM</p>
            </div>
          </div>

          <div className="mt-3 rounded-lg bg-white px-3 py-2.5">
            <p className="font-mono text-[10px] uppercase tracking-wider text-ink-faint">Symptoms</p>
            <p className="mt-0.5 text-[13px] text-ink">Fever, mild headache</p>
          </div>
        </div>

        <div className="mt-3 flex gap-2">
          <span className="flex-1 rounded-lg border border-line py-2 text-center text-[12px] font-medium text-ink-soft">Reschedule</span>
          <span className="flex-1 rounded-lg py-2 text-center text-[12px] font-semibold text-white" style={{ background: "var(--color-ink)" }}>
            View details
          </span>
        </div>
      </div>
    </div>
  );
}

export function DoctorPreview({ className = "" }) {
  const rows = [
    { name: "patient@healthcare.dev", time: "9:00 PM", status: "BOOKED", tone: "teal", i: "PH" },
    { name: "a.morgan@mail.dev", time: "9:30 PM", status: "PENDING", tone: "amber", i: "AM" },
    { name: "s.kaur@mail.dev", time: "10:15 PM", status: "BOOKED", tone: "teal", i: "SK" },
  ];
  return (
    <div className={`overflow-hidden rounded-[14px] border border-line bg-white shadow-[0_24px_60px_-32px_rgba(11,21,18,0.35)] ${className}`}>
      <div className="flex items-center justify-between border-b border-line px-5 py-3.5">
        <span className="font-display text-[13px] font-bold tracking-tight text-ink">Doctor Portal</span>
        <span className="font-mono text-[10px] uppercase tracking-widest text-ink-faint">Today</span>
      </div>
      <div className="px-5 py-4">
        <p className="text-[13px] text-ink-soft">
          Welcome, <span className="font-semibold text-ink">Dr. Dev</span>
        </p>
        <p className="mt-3 font-mono text-[10px] uppercase tracking-[0.2em] text-ink-faint">Next appointments</p>
        <ul className="mt-2 divide-y divide-line rounded-xl border border-line">
          {rows.map((r) => (
            <li key={r.name} className="flex items-center gap-3 px-3.5 py-3">
              <Avatar initials={r.i} tone="slate" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-[13px] font-medium text-ink">{r.name}</p>
                <p className="font-mono text-[11px] text-ink-faint">Wed, Aug 26 · {r.time}</p>
              </div>
              <StatusBadge label={r.status} tone={r.tone} />
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

export function NotifChip({
  title,
  time,
  tone = "teal",
  className = "",
}) {
  return (
    <div className={`flex items-center gap-3 rounded-xl border border-line bg-white/95 px-3.5 py-2.5 shadow-[0_16px_40px_-20px_rgba(11,21,18,0.4)] backdrop-blur ${className}`}>
      <span
        className="grid h-8 w-8 place-items-center rounded-lg"
        style={tone === "teal" ? { background: "var(--color-teal-wash)" } : { background: "#fbf1df" }}
      >
        <svg width="15" height="15" viewBox="0 0 15 15" fill="none" aria-hidden="true">
          <path
            d="M4 7.8l2.3 2.3L11 5"
            stroke={tone === "teal" ? "var(--color-teal-deep)" : "#8a5a10"}
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
      <div>
        <p className="text-[12.5px] font-semibold text-ink">{title}</p>
        <p className="font-mono text-[10px] text-ink-faint">{time}</p>
      </div>
    </div>
  );
}