import type { ImporterEvent, ImporterMessage } from './contract.js';
import './styles.css';

const importButton = requiredElement<HTMLButtonElement>('import');
const exportButton = requiredElement<HTMLButtonElement>('export');
const closeButton = requiredElement<HTMLButtonElement>('close');
const status = requiredElement<HTMLParagraphElement>('status');

importButton.addEventListener('click', () => {
  send({ type: 'sayhi.import-verify' });
});

exportButton.addEventListener('click', () => {
  send({ type: 'sayhi.export-verify' });
});

closeButton.addEventListener('click', () => {
  send({ type: 'sayhi.close' });
});

window.addEventListener('message', (event: MessageEvent<ImporterEvent>) => {
  const message = event.data;
  if (!message || typeof message !== 'object' || !('type' in message)) return;

  if (message.type === 'sayhi.ready') {
    setText('set-count', message.setCount);
    setText('theme-count', message.themeCount);
    setText('binding-count', message.bindingCount);
    return;
  }
  if (message.type === 'sayhi.importing') {
    importButton.disabled = true;
    setStatus('Importing the canonical resolver…', 'working');
    return;
  }
  if (message.type === 'sayhi.import-error') {
    importButton.disabled = false;
    setStatus(message.error, 'error');
    return;
  }
  if (message.type === 'sayhi.import-result') {
    importButton.disabled = false;
    const result = message.result;
    const verb =
      result.action === 'created'
        ? 'Created'
        : result.action === 'updated'
          ? 'Updated'
          : 'Verified';
    setStatus(
      `${verb} Verify ${result.componentVersion}: ${result.createdTokens} new tokens, ${result.boundShapes} live bindings, ${result.unsupportedTokens} preserved-only motion values. ${result.changes.length} recorded changes.`,
      'success',
    );
    return;
  }
  if (message.type === 'sayhi.export-result') {
    downloadJson(message.filename, message.json);
    setStatus(`Exported ${message.filename}.`, 'success');
    return;
  }
  if (message.type === 'sayhi.projection-result') {
    downloadJson(message.filename, message.json);
    setStatus(`Exported ${message.filename}.`, 'success');
  }
});

function send(message: ImporterMessage): void {
  parent.postMessage(message, '*');
}

function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error(`Missing importer UI element #${id}.`);
  return element as T;
}

function setText(id: string, value: string | number): void {
  requiredElement<HTMLElement>(id).textContent = String(value);
}

function setStatus(
  message: string,
  kind: 'error' | 'success' | 'working',
): void {
  status.textContent = message;
  status.dataset.kind = kind;
}

function downloadJson(filename: string, json: string): void {
  const url = URL.createObjectURL(
    new Blob([json], { type: 'application/json;charset=utf-8' }),
  );
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
