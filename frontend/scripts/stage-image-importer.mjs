import { readFile, writeFile, mkdir } from "node:fs/promises";
import { createHash } from "node:crypto";
import { resolve } from "node:path";

const [source, expectedDigest] = process.argv.slice(2);
if (!source || !/^[a-f0-9]{64}$/.test(expectedDigest || "")) throw new Error("Pass the independently built private image plugin and its SHA-256.");
const bytes = await readFile(source);
const digest = createHash("sha256").update(bytes).digest("hex");
if (digest !== expectedDigest || bytes.length > 250000) throw new Error("Image importer artifact verification failed.");
const directory = resolve(new URL("../target/dist/plugins/sayhi-image-component/", import.meta.url).pathname);
await mkdir(directory, { recursive: true });
await writeFile(resolve(directory, "image-plugin.js"), bytes);
await writeFile(resolve(directory, "artifact.json"), JSON.stringify({ owner: "sayhi-ve-gateway/apps/sayhi-component-importer-v2", format: "sayhi.image-scene/1.0", sha256: digest, bytes: bytes.length }));
console.log(JSON.stringify({ directory, digest, bytes: bytes.length }));
