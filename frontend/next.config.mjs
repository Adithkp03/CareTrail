/** @type {import('next').NextConfig} */
const nextConfig = {
  eslint: { ignoreDuringBuilds: true },
  // Static export so the app can be embedded in the Capacitor Android shell.
  output: "export",
  images: { unoptimized: true },
};
export default nextConfig;
