import type { ImporterEvent, ImporterMessage } from './projection-contract.js';
import './styles.css';

const importButton = requiredElement<HTMLButtonElement>('import');
const exportButton = requiredElement<HTMLButtonElement>('export');
const closeButton = requiredElement<HTMLButtonElement>('close');
const status = requiredElement<HTMLParagraphElement>('status');

importButton.addEventListener('click', () => send({ type: 'sayhi.v2.import' }));
exportButton.addEventListener('click', () => send({ type: 'sayhi.v2.export' }));
closeButton.addEventListener('click', () => send({ type: 'sayhi.v2.close' }));

window.addEventListener('message', (event: MessageEvent<ImporterEvent>) => {
  const message = event.data;
  if (!message || typeof message !== 'object' || !('type' in message)) return;

  if (message.type === 'sayhi.v2.ready') {
    setText('node-count', message.nodeCount);
    setText('set-count', message.setCount);
    setText('binding-count', message.bindingCount);
    return;
  }
  if (message.type === 'sayhi.v2.importing') {
    importButton.disabled = true;
    setStatus('Materializing the V2 component manifest…', 'working');
    return;
  }
  if (message.type === 'sayhi.v2.error') {
    importButton.disabled = false;
    setStatus(message.error, 'error');
    return;
  }
  if (message.type === 'sayhi.v2.import-result') {
    importButton.disabled = false;
    const result = message.result;
    setStatus(
      `Imported ${result.createdNodes} nodes, ${result.createdTokens} new tokens, and ${result.boundNodes} live bindings. ${result.unsupportedTokens} canonical values remain preserved-only.`,
      'success',
    );
    return;
  }
  if (message.type === 'sayhi.v2.export-result') {
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
