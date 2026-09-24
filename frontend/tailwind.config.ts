import type { Config } from "tailwindcss";
export default {
  content: ["./app/**/*.{ts,tsx}", "./lib/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: { DEFAULT: "#0d7a5f", soft: "#e6f4f0" },
        ink: "#17201c",
      },
    },
  },
  plugins: [],
} satisfies Config;
