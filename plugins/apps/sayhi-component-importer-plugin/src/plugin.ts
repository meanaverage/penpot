import type { ImporterMessage } from './contract.js';
import {
  exportVerifyDtcgPackage,
  exportVerifyProjectionSnapshot,
  importVerifyComponent,
} from './importer.js';
import { VERIFY_TOKEN_BINDINGS } from './verify-package.js';
import { materializeVerifyTokenSource } from './token-source.js';

let importInFlight = false;

penpot.ui.open('SayHi component importer vNext', `?theme=${penpot.theme}`, {
  width: 390,
  height: 440,
});

const tokenPlan = materializeVerifyTokenSource();
penpot.ui.sendMessage({
  type: 'sayhi.ready',
  setCount: tokenPlan.sets.length,
  themeCount: tokenPlan.themes.length,
  bindingCount: VERIFY_TOKEN_BINDINGS.length,
});

penpot.ui.onMessage<ImporterMessage>(async (message) => {
  if (message.type === 'sayhi.close') {
    penpot.closePlugin();
    return;
  }
  if (message.type === 'sayhi.export-verify') {
    try {
      penpot.ui.sendMessage({
        type: 'sayhi.export-result',
        json: exportVerifyDtcgPackage(),
        filename: 'sayhi-verify.resolver.json',
      });
    } catch (error) {
      penpot.ui.sendMessage({
        type: 'sayhi.import-error',
        error:
          error instanceof Error
            ? error.message
            : 'Penpot could not export SayHi Verify.',
      });
    }
    return;
  }
  if (message.type === 'sayhi.export-projection') {
    try {
      penpot.ui.sendMessage({
        type: 'sayhi.projection-result',
        json: exportVerifyProjectionSnapshot(),
        filename: 'sayhi-verify.penpot-projection.json',
      });
    } catch (error) {
      penpot.ui.sendMessage({
        type: 'sayhi.import-error',
        error:
          error instanceof Error
            ? error.message
            : 'Penpot could not export the SayHi Verify projection.',
      });
    }
    return;
  }
  if (message.type !== 'sayhi.import-verify' || importInFlight) return;

  importInFlight = true;
  penpot.ui.sendMessage({ type: 'sayhi.importing' });
  try {
    const result = await importVerifyComponent();
    penpot.ui.sendMessage({ type: 'sayhi.import-result', result });
  } catch (error) {
    penpot.ui.sendMessage({
      type: 'sayhi.import-error',
      error:
        error instanceof Error
          ? error.message
          : 'Penpot could not import SayHi Verify.',
    });
  } finally {
    importInFlight = false;
  }
});
