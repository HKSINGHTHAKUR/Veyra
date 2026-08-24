/**
 * Reusable Veyra brand mark — the approved logo image.
 * Renders inline with the adjacent "Veyra" wordmark text.
 * Pass a `className` with explicit height (e.g. "h-8 w-auto") to control size.
 */
export default function VeyraLogo({ className = "h-8 w-auto" }) {
  return (
    <img
      src="/veyra-logo.png"
      alt=""
      className={`inline-block shrink-0 object-contain ${className}`}
    />
  );
}