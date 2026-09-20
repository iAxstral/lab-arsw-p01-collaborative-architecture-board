import { BoardApiClient, BoardApiError } from './api/board-api-client.js';
import { createBoardState } from './state/board-state.js';
import { createBoardView } from './ui/board-view.js';
import { createBoardRealtimeClient } from './realtime/board-realtime-client.js';
import { BoardEvents } from './events/board-event.js';

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
  connectLiveBtn: document.getElementById('connectLiveBtn'),
  disconnectLiveBtn: document.getElementById('disconnectLiveBtn'),
  liveStatus: document.getElementById('liveStatus'),
  actorId: document.getElementById('actorId'),
  message: document.getElementById('message'),
  canvas: document.getElementById('boardCanvas')
};

const ACTION_BUTTONS = [
  els.newBoardBtn, els.loadBtn, els.saveBtn,
  els.addRectBtn, els.addTextBtn, els.connectBtn, els.deleteBtn
];

function loadActorId() {
  try {
    const stored = sessionStorage.getItem('arsw-actor-id');
    if (stored) return stored;
    const created = `client-${crypto.randomUUID()}`;
    sessionStorage.setItem('arsw-actor-id', created);
    return created;
  } catch {
    return `client-${crypto.randomUUID()}`;
  }
}

const actorId = loadActorId();
let liveStatus = 'disconnected';
let bufferedEvents = null;

const state = createBoardState();
const view = createBoardView(els.canvas, els.message, els.retryBtn, {
  onElementPointerDown: handleElementPointerDown,
  onElementDrag: handleElementDrag,
  onElementDragEnd: handleElementDragEnd,
  onCanvasPointerDown: handleCanvasPointerDown
});
const realtime = createBoardRealtimeClient({
  onStatus(status) {
    liveStatus = status;
    renderAll();
  },
  onEvent(event) {
    if (bufferedEvents) {
      bufferedEvents.push(event);
      return;
    }
    state.applyEvent(event);
    renderAll();
  }
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
  const loading = snapshot.remote.status === 'loading';
  setButtonsDisabled(loading);
  els.canvas.classList.toggle('busy', loading);
  const connected = realtime.isConnected();
  els.liveStatus.textContent = liveStatus;
  els.liveStatus.dataset.status = liveStatus;
  els.connectLiveBtn.disabled = loading || !snapshot.board.id || connected || liveStatus === 'connecting';
  els.disconnectLiveBtn.disabled = !connected;
  view.render(snapshot, hint);
}

async function joinLive(boardId) {
  try {
    await realtime.connect(boardId);
    return true;
  } catch (error) {
    renderAll(`Live channel error: ${error.message}`);
    return false;
  }
}

function publishLocal(buildEvent, ...args) {
  const boardId = state.snapshot().board.id;
  if (!boardId || !realtime.isConnected()) return null;
  try {
    realtime.publish(buildEvent(boardId, actorId, ...args));
    return null;
  } catch (error) {
    return `Live update not sent: ${error.message}`;
  }
}

async function handleCreate() {
  const name = els.boardName.value.trim();
  const wasLive = realtime.isConnected();
  state.setRemote('loading', 'create');
  renderAll();
  try {
    const created = await BoardApiClient.create(name);
    state.setBoard(created);
    els.boardId.value = created.id;
    state.setRemote('success', 'create');
    if (wasLive) await joinLive(created.id);
  } catch (error) {
    state.setRemote('error', 'create', describeError(error));
  }
  renderAll();
}

async function handleLoad() {
  const id = els.boardId.value.trim();
  const previousBoardId = state.snapshot().board.id;
  const wasLive = realtime.isConnected();
  state.setRemote('loading', 'load');
  renderAll();
  bufferedEvents = [];
  try {
    if (wasLive && id) await joinLive(id);
    const loaded = await BoardApiClient.load(id);
    state.setBoard(loaded);
    els.boardName.value = loaded.name;
    state.setRemote('success', 'load');
  } catch (error) {
    state.setRemote('error', 'load', describeError(error));
    if (wasLive && previousBoardId && realtime.boardId() !== previousBoardId) await joinLive(previousBoardId);
  } finally {
    const buffered = bufferedEvents;
    bufferedEvents = null;
    buffered.forEach((event) => state.applyEvent(event));
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
  const element = state.addRectangle();
  renderAll(publishLocal(BoardEvents.elementCreated, element));
}

function handleAddText() {
  const element = state.addText();
  renderAll(publishLocal(BoardEvents.elementCreated, element));
}

function handleDeleteSelected() {
  const { selectedId } = state.snapshot();
  state.removeSelected();
  renderAll(selectedId ? publishLocal(BoardEvents.elementDeleted, selectedId) : null);
}

async function handleConnectLive() {
  const boardId = state.snapshot().board.id;
  if (!boardId) {
    renderAll('Create or load a board before connecting live.');
    return;
  }
  if (await joinLive(boardId)) renderAll('Live collaboration connected.');
}

async function handleDisconnectLive() {
  await realtime.disconnect();
  renderAll('Live collaboration disconnected.');
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
    renderAll(created
      ? publishLocal(BoardEvents.connectorCreated, created)
      : 'Pick a different element to finish the connector.');
    return;
  }
  state.select(id);
  renderAll();
}

function handleElementDrag(x, y) {
  state.moveSelected(x, y);
  renderAll();
}

function handleElementDragEnd(id, x, y) {
  const moved = state.snapshot().board.elements.find((e) => e.id === id);
  if (!moved || moved.x !== x || moved.y !== y) return;
  const hint = publishLocal(BoardEvents.elementMoved, id, x, y);
  if (hint) renderAll(hint);
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
els.connectLiveBtn.addEventListener('click', handleConnectLive);
els.disconnectLiveBtn.addEventListener('click', handleDisconnectLive);

els.actorId.textContent = actorId;
renderAll();
