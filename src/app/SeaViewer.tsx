"use client";

import { useState } from "react";

const WINDSURF_VIDEOS = [
  "BnEpmJwpcvw",
  "BMxKbtz7U_k",
  "ZdoWr12ZGDs",
  "19zQyRbTm-I",
  "PX3OAOJ6-a4",
  "SJWlsVB8f2o",
  "WBPfazY0xxg",
];

function randomVideo(exclude?: string) {
  const pool = WINDSURF_VIDEOS.filter((v) => v !== exclude);
  return pool[Math.floor(Math.random() * pool.length)];
}

export default function SeaViewer() {
  const [videoId, setVideoId] = useState(() => randomVideo());
  const [exited, setExited] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);

  if (exited) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-zinc-950">
        <p className="text-xl font-medium text-white">Goodbye!</p>
        <button
          onClick={() => {
            setVideoId(randomVideo());
            setAcknowledged(false);
            setExited(false);
          }}
          className="rounded-full border border-zinc-600 px-8 py-3 text-base font-semibold text-zinc-300 transition-colors hover:border-zinc-400 hover:text-white"
        >
          Watch again
        </button>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-950">
      {!acknowledged ? (
        <div className="flex flex-col items-center gap-6">
          <div className="w-[min(90vw,800px)] aspect-video rounded-2xl overflow-hidden shadow-2xl">
            <iframe
              key={videoId}
              width="100%"
              height="100%"
              src={`https://www.youtube.com/embed/${videoId}?autoplay=0`}
              title="Windsurfing video"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
            />
          </div>
          <button
            onClick={() => setAcknowledged(true)}
            className="rounded-full bg-white px-12 py-3 text-base font-semibold text-zinc-900 shadow-lg transition-colors hover:bg-zinc-200"
          >
            OK
          </button>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-4">
          <p className="text-lg font-medium text-zinc-300">What would you like to do?</p>
          <div className="flex gap-4">
            <button
              onClick={() => {
                setVideoId(randomVideo(videoId));
                setAcknowledged(false);
              }}
              className="rounded-full bg-white px-8 py-3 text-base font-semibold text-zinc-900 shadow-lg transition-colors hover:bg-zinc-200"
            >
              See another video
            </button>
            <button
              onClick={() => setExited(true)}
              className="rounded-full border border-zinc-600 px-8 py-3 text-base font-semibold text-zinc-300 transition-colors hover:border-zinc-400 hover:text-white"
            >
              Exit
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
