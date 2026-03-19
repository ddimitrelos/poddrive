import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "images.unsplash.com",
      },
      {
        protocol: "https",
        hostname: "scrumorg-website-prod.s3.amazonaws.com",
      },
    ],
  },
};

export default nextConfig;
