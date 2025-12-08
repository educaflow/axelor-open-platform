import legacy from "@vitejs/plugin-legacy";
import react from "@vitejs/plugin-react";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { visualizer } from "rollup-plugin-visualizer";
import { defineConfig } from "vite";
import svgr from "vite-plugin-svgr";

import monacoPkg from "monaco-editor/package.json" with { type: "json" };

const monacoHash = crypto
  .createHash("sha256")
  .update(JSON.stringify(monacoPkg))
  .digest()
  .toString("base64url")
  .slice(0, 8);
const monacoPath = `assets/monaco-${monacoHash}/vs`;
const monacoNodePath = "node_modules/monaco-editor/min/vs";

export default defineConfig({
  base: "./",
  plugins: [
    react(),
    svgr({
      svgrOptions: {
        icon: true,
      },
    }),
    {
      name: "monaco-hash",
      writeBundle() {
        const sourceDir = path.resolve(__dirname, monacoNodePath);
        const targetDir = `dist/${monacoPath}`;
        fs.mkdirSync(path.dirname(targetDir));
        fs.symlinkSync(sourceDir, targetDir, "dir");
      },
    },
    process.env.VITE_VISUALIZER === "true" && visualizer({
      emitFile: true,
      // to generate json output
      //template: "raw-data"
    }),
  ].filter(Boolean),
  define: {
    "import.meta.env.MONACO_PATH": JSON.stringify(
      process.env.NODE_ENV === "production" ? monacoPath : monacoNodePath,
    ),
  },
  optimizeDeps: {
    entries: ["src/**/*.{ts,js,tsx,jsx,css,scss,html}"],
  },
  resolve: {
    alias: [
      {
        find: /^~(.*)/,
        replacement: "$1",
      },
      {
        find: /^@\/(.*)/,
        replacement: path.join(__dirname, "src", "$1"),
      },
    ],
  },
  build: {
    /**
     * Browser Compatibility (ES2022):
     *
     *  ┌──────────────┬──────────────────┬───────────────────┐
     *  │ Browser      │ Minimum Version  │ Release Date      │
     *  ├──────────────┼──────────────────┼───────────────────┤
     *  │ Chrome       │ 105+             │ August 2022       │
     *  │ Edge         │ 105+             │ September 2022    │
     *  │ Firefox      │ 104+             │ August 2022       │
     *  │ Safari       │ 16.4+            │ March 2023        │
     *  │ Opera        │ 91+              │ September 2022    │
     *  └──────────────┴──────────────────┴───────────────────┘
     */
    target: ["es2022"],
    sourcemap: true,
    minify: false,    
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          if (id.includes("@babel+standalone")) {
            return "babel";
          }
        },
      },
    },
  },
});
