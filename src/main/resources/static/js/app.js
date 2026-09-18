import { BoardApiClient, BoardApiError } from './api/board-api-client.js';
import { createBoardState } from './state/board-state.js';
import { createBoardView } from './ui/board-view.js';

const els = {
  boardName: document.getElementById('boardName'),
  newBoardBtn: document.getElementById('newBoardBtn'),
  boardId: document.getElementById('boardId'),
  loadBtn: document.getElementById('loadBtn'),
  saveBtn: document.getElementById('saveBtn'),
  retryBtn: document.getElementById('retryBtn'),
  addRectBtn: document.getElementById('addRectBtn'),
  addTextBtn: document.getElementById('addTextBtn'),
  connectBtn: document.getElementById('connectBtn'),
  deleteBtn: document.getElementById('deleteBtn'),
  message: document.getElementById('message'),
  canvas: document.getElementById('boardCanvas')
};

const ACTION_BUTTONS = [
  els.newBoardBtn, els.loadBtn, els.saveBtn,
  els.addRectBtn, els.addTextBtn, els.connectBtn, els.deleteBtn
];

const state = createBoardState();
const view = createBoardView(els.canvas, els.message, els.retryBtn, {
  onElementPointerDown: handleElementPointerDown,
  onElementDrag: handleElementDrag,
  onCanvasPointerDown: handleCanvasPointerDown
});

function isConnectable(element) {
  return Boolean(element) && element.type !== 'CONNECTOR';
}

function describeError(error) {
  if (error instanceof BoardApiError) {
    return `${error.message} (${error.code})`;
  }
  return error?.message || 'Unexpected error.';
}

function setButtonsDisabled(disabled) {
  for (const button of ACTION_BUTTONS) {
    button.disabled = disabled;
  }
}

function renderAll(hint = null) {
  const snapshot = state.snapshot();
  setButtonsDisabled(snapshot.remote.status === 'loading');
  view.render(snapshot, hint);
}

async function handleCreate() {
  const name = els.boardName.value.trim();
  state.setRemote('loading', 'create');
  renderAll();
  try {
    const created = await BoardApiClient.create(name);
    state.setBoard(created);
    els.boardId.value = created.id;
    state.setRemote('success', 'create');
  } catch (error) {
    state.setRemote('error', 'create', describeError(error));
  }
  renderAll();
}

async function handleLoad() {
  const id = els.boardId.value.trim();
  state.setRemote('loading', 'load');
  renderAll();
  try {
    const loaded = await BoardApiClient.load(id);
    state.setBoard(loaded);
    els.boardName.value = loaded.name;
    state.setRemote('success', 'load');
  } catch (error) {
    state.setRemote('error', 'load', describeError(error));
  }
  renderAll();
}

async function handleSave() {
  const board = state.toPersistedBoard();
  if (!board.id) {
    state.setRemote('error', 'save', 'Create or load a board before saving.');
    renderAll();
    return;
  }
  state.setRemote('loading', 'save');
  renderAll();
  try {
    const saved = await BoardApiClient.save(board);
    state.setBoard(saved);
    state.setRemote('success', 'save');
  } catch (error) {
    state.setRemote('error', 'save', describeError(error));
  }
  renderAll();
}

function handleRetry() {
  const { lastAction } = state.snapshot().remote;
  if (lastAction === 'create') return handleCreate();
  if (lastAction === 'load') return handleLoad();
  if (lastAction === 'save') return handleSave();
}

function handleRenameChange() {
  if (state.snapshot().board.id) {
    state.setName(els.boardName.value);
    renderAll();
  }
}

function handleAddRectangle() {
  state.addRectangle();
  renderAll();
}

function handleAddText() {
  state.addText();
  renderAll();
}

function handleDeleteSelected() {
  state.removeSelected();
  renderAll();
}

function handleConnectClick() {
  const snapshot = state.snapshot();
  const selected = snapshot.board.elements.find((e) => e.id === snapshot.selectedId);
  if (!isConnectable(selected)) {
    renderAll('Select a rectangle or text element before connecting.');
    return;
  }
  state.beginConnect();
  renderAll('Click the element you want to connect to.');
}

function handleElementPointerDown(id) {
  const snapshot = state.snapshot();
  if (snapshot.connectSourceId) {
    const target = snapshot.board.elements.find((e) => e.id === id);
    if (!isConnectable(target)) {
      renderAll('Connectors cannot be a connection target — pick a rectangle or text.');
      return;
    }
    const created = state.completeConnect(id);
    renderAll(created ? null : 'Pick a different element to finish the connector.');
    return;
  }
  state.select(id);
  renderAll();
}

function handleElementDrag(x, y) {
  state.moveSelected(x, y);
  renderAll();
}

function handleCanvasPointerDown() {
  state.select(null);
  renderAll();
}

els.newBoardBtn.addEventListener('click', handleCreate);
els.loadBtn.addEventListener('click', handleLoad);
els.saveBtn.addEventListener('click', handleSave);
els.retryBtn.addEventListener('click', handleRetry);
els.boardName.addEventListener('change', handleRenameChange);
els.addRectBtn.addEventListener('click', handleAddRectangle);
els.addTextBtn.addEventListener('click', handleAddText);
els.connectBtn.addEventListener('click', handleConnectClick);
els.deleteBtn.addEventListener('click', handleDeleteSelected);

renderAll();
