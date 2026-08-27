import { VERIFY_PROJECTION_MANIFEST } from './fixtures/verify.js';
import {
  canonicalManifestJson,
  importProjectionManifest,
  projectionCoverage,
} from './materializer.js';
import type { ImporterMessage } from './projection-contract.js';

let importInFlight = false;

penpot.ui.open('SayHi component importer V2', `?theme=${penpot.theme}`, {
  width: 390,
  height: 440,
});

const coverage = projectionCoverage(VERIFY_PROJECTION_MANIFEST);
penpot.ui.sendMessage({
  type: 'sayhi.v2.ready',
  nodeCount: coverage.nodes,
  setCount: coverage.sets,
  bindingCount: coverage.bindings,
});

penpot.ui.onMessage<ImporterMessage>(async (message) => {
  if (message.type === 'sayhi.v2.close') {
    penpot.closePlugin();
    return;
  }
  if (message.type === 'sayhi.v2.export') {
    penpot.ui.sendMessage({
      type: 'sayhi.v2.export-result',
      json: canonicalManifestJson(VERIFY_PROJECTION_MANIFEST),
      filename: 'sayhi-verify.component-projection.v2.json',
    });
    return;
  }
  if (message.type !== 'sayhi.v2.import' || importInFlight) return;

  importInFlight = true;
  penpot.ui.sendMessage({ type: 'sayhi.v2.importing' });
  try {
    const result = await importProjectionManifest(VERIFY_PROJECTION_MANIFEST);
    penpot.ui.sendMessage({ type: 'sayhi.v2.import-result', result });
  } catch (error) {
    penpot.ui.sendMessage({
      type: 'sayhi.v2.error',
      error:
        error instanceof Error
          ? (error.stack ?? error.message)
          : 'Penpot could not materialize the component manifest.',
    });
  } finally {
    importInFlight = false;
  }
});
