import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      { protocol: "https", hostname: "image.a-rt.com" },
      { protocol: "https", hostname: "img.29cm.co.kr" },
    ],
  },
};

export default nextConfig;
