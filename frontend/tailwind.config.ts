import type { Config } from "tailwindcss";
export default {
  content: ["./app/**/*.{ts,tsx}", "./lib/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: { DEFAULT: "#126e67", soft: "#e8f3f0" },
        navy: "#10283f",
        ink: "#203044",
      },
    },
  },
  plugins: [],
} satisfies Config;
