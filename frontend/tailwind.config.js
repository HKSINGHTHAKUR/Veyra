/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{js,jsx}',
  ],
  theme: {
    extend: {
      colors: {
        // Clinical blue — calm, professional, not neon
        primary: {
          50: '#f0f7ff',
          100: '#e0effe',
          200: '#b9dcfd',
          300: '#7cc0fb',
          400: '#3aa1f6',
          500: '#1283ea',
          600: '#0668c7',
          700: '#0753a2',
          800: '#0a4686',
          900: '#0d3c70',
        },
        surface: '#f7f9fb',
        // Landing page palette (teal / ink — Veyra brand)
        ink: '#0b1512',
        'ink-soft': '#47554f',
        'ink-faint': '#7b8781',
        paper: '#ffffff',
        mist: '#f4f6f5',
        'mist-2': '#eef1f0',
        line: '#e2e7e5',
        teal: '#0e7c66',
        'teal-deep': '#0a5b4b',
        'teal-wash': '#e7f2ef',
      },
      fontFamily: {
        sans: [
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'sans-serif',
        ],
        display: ['Archivo', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'monospace'],
      },
      fontSize: {
        '2xs': ['0.6875rem', { lineHeight: '1rem' }],
      },
      boxShadow: {
        card: '0 1px 2px 0 rgb(15 23 42 / 0.05), 0 1px 3px 0 rgb(15 23 42 / 0.04)',
        pop: '0 10px 25px -5px rgb(15 23 42 / 0.12), 0 4px 10px -4px rgb(15 23 42 / 0.06)',
      },
      borderRadius: {
        card: '0.75rem',
      },
    },
  },
  plugins: [],
}
