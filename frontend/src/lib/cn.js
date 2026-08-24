/** Tiny classname joiner: cn('a', cond && 'b', { c: true }) -> 'a b c' */
export function cn(...parts) {
  const out = []
  for (const p of parts) {
    if (!p) continue
    if (typeof p === 'string') out.push(p)
    else if (typeof p === 'object')
      for (const [k, v] of Object.entries(p)) if (v) out.push(k)
  }
  return out.join(' ')
}
