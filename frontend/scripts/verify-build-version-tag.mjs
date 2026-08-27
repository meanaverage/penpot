import fs from "node:fs/promises";
import path from "node:path";

const [bundleDirectory, expectedVersionTag] = process.argv.slice(2);

if (!bundleDirectory || !expectedVersionTag) {
  throw new Error(
    "usage: verify-build-version-tag.mjs <bundle-directory> <expected-version-tag>",
  );
}

if (expectedVersionTag === "develop") {
  throw new Error(
    "production bundles must use a unique version tag; the static develop tag is forbidden",
  );
}

const indexPath = path.join(bundleDirectory, "index.html");
const mainPath = path.join(bundleDirectory, "js", "main.js");
const [indexHtml, compiledMain] = await Promise.all([
  fs.readFile(indexPath, "utf8"),
  fs.readFile(mainPath, "utf8"),
]);

const expectedAssignment = `globalThis.penpotVersionTag = ${JSON.stringify(expectedVersionTag)};`;
const expectedAssetSuffix = `?version=${expectedVersionTag}`;

if (!indexHtml.includes(expectedAssignment)) {
  throw new Error(
    `generated index version tag does not match ${expectedVersionTag}`,
  );
}

if (!indexHtml.includes(expectedAssetSuffix)) {
  throw new Error(
    `generated index does not reference assets with ${expectedVersionTag}`,
  );
}

if (!compiledMain.includes(expectedVersionTag)) {
  throw new Error(
    `compiled JavaScript version tag does not match ${expectedVersionTag}`,
  );
}

console.log(`verified Penpot frontend version tag: ${expectedVersionTag}`);
