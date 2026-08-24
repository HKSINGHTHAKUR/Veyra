import { Link } from "react-router-dom";
import VeyraLogo from "../../components/VeyraLogo";

export function Logo({ className = "", light = false }) {
  const ink = light ? "#ffffff" : "#0b1512";
  return (
    <a href="#top" className={`inline-flex items-center gap-2.5 ${className}`} aria-label="Veyra home">
      <VeyraLogo className="h-8 w-auto" />
      <span className="font-display text-[19px] font-bold tracking-tight" style={{ color: ink }}>
        Veyra
      </span>
    </a>
  );
}

export function Eyebrow({ children }) {
  return (
    <span className="font-mono text-[11px] font-medium uppercase tracking-[0.22em] text-teal">
      {children}
    </span>
  );
}

export function StatusBadge({ label = "BOOKED", tone = "teal" }) {
  const styles = {
    teal: "bg-teal-wash text-teal-deep",
    amber: "bg-[#fbf1df] text-[#8a5a10]",
    slate: "bg-mist-2 text-ink-soft",
  };
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 font-mono text-[10px] font-semibold tracking-wider ${styles[tone]}`}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-80" />
      {label}
    </span>
  );
}

export function Button({
  children,
  href = "#",
  variant = "primary",
  className = "",
}) {
  const base =
    "inline-flex items-center justify-center gap-2 rounded-full px-5 py-2.5 text-[14px] font-semibold transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-teal focus-visible:ring-offset-white";
  const variants = {
    primary: "bg-ink text-white hover:bg-teal-deep hover:-translate-y-0.5 shadow-sm",
    outline: "border border-line text-ink hover:border-ink hover:-translate-y-0.5",
    ghost: "text-ink-soft hover:text-ink",
    light: "bg-white text-ink hover:-translate-y-0.5 shadow-sm",
  };

  // Internal app routes use React Router Link; anchor links use <a>
  if (href.startsWith("/")) {
    return (
      <Link to={href} className={`${base} ${variants[variant]} ${className}`}>
        {children}
      </Link>
    );
  }
  return (
    <a href={href} className={`${base} ${variants[variant]} ${className}`}>
      {children}
    </a>
  );
}

export function ArrowRight({ className = "" }) {
  return (
    <svg width="15" height="15" viewBox="0 0 15 15" fill="none" aria-hidden="true" className={className}>
      <path d="M3 7.5h9M8 3.5l4 4-4 4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}