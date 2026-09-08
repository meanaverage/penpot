import { readFile } from "node:fs/promises";
import { JSDOM } from "jsdom";
import assert from "node:assert/strict";
import sax from "sax";

const [pluginPath, candidatePath] = process.argv.slice(2);
if (!pluginPath || !candidatePath) throw new Error("Pass the built private image plugin and a synthetic candidate JSON.");
const dom = new JSDOM("<!doctype html><body></body>", { url: "http://localhost/" });
dom.window.matchMedia = query => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {} });
for (const key of ["window", "document", "DOMParser", "XMLSerializer", "HTMLElement", "Element", "Node", "MutationObserver", "navigator"]) {
  Object.defineProperty(globalThis, key, { value: dom.window[key], configurable: true });
}
const { setup, snapshot } = await import("../target/image-import-check/harness.js");
// The release browser bundler normalizes this CommonJS package. Node's ESM
// namespace exposes only default, so supply the identical real parser export.
globalThis.shadow.esm.esm_import$sax = sax;
const api = setup();
const candidate = JSON.parse(await readFile(candidatePath, "utf8"));
const request = { scene: candidate.scene, identity: candidate.identity, mode: candidate.mode, fileId: api.currentFile.id, pageId: api.currentPage.id };
// Real Penpot proxies/events/store, synthetic file. Renderer heap is the same
// boundary fake used by the native unit tests; no persistence/network watcher.
const penpot = new Proxy(api, { get(target, key) {
  if (key === "closePlugin") return () => {};
  return Reflect.get(target, key);
} });
const code = await readFile(pluginPath, "utf8");
const result = await Promise.race([
  new Promise(resolve => {
    new Function("penpot", "imageComponent", code)(penpot, {
      request: () => JSON.stringify(request), canImport: () => true,
      complete: json => resolve(JSON.parse(json)),
    });
  }),
  new Promise((_, reject) => setTimeout(() => reject(new Error("Native import timed out")), 40000)),
]);
assert.equal(result.ok, true, result.error);
const file = snapshot();
assert.equal(Object.keys(file.data.components).length, 1);
assert.ok(result.result.createdNodes >= candidate.scene.elements.length);
assert.ok(result.result.createdTokens > 0);
const component = Object.values(file.data.components)[0];
assert.ok(Object.values(component["plugin-data"] ?? {}).some(data => Boolean(data["portable-web-artifact"])));
console.log(JSON.stringify({ result, components: Object.keys(file.data.components).length, mode: candidate.mode }));
process.exit(0);
