// Run from the repository root (Node 20+, no dependencies):
//   node src/test/js/board-state.applyEvent.test.mjs
import assert from 'node:assert/strict';
import { createBoardState } from '../../main/resources/static/js/state/board-state.js';

const BOARD_ID = 'board-1';
let failures = 0;

function test(name, fn) {
  try {
    fn();
    console.log(`ok   ${name}`);
  } catch (error) {
    failures += 1;
    console.log(`FAIL ${name}\n     ${error.message}`);
  }
}

const rect = (id, x = 10, y = 20) =>
  ({ id, type: 'RECTANGLE', x, y, width: 100, height: 60, text: `rect ${id}`, sourceId: null, targetId: null });
const textEl = (id) =>
  ({ id, type: 'TEXT', x: 200, y: 20, width: 100, height: 30, text: `text ${id}`, sourceId: null, targetId: null });
const connector = (id, sourceId, targetId) =>
  ({ id, type: 'CONNECTOR', x: 0, y: 0, width: 0, height: 0, text: '', sourceId, targetId });

function event(type, payload, boardId = BOARD_ID) {
  return {
    eventId: `evt-${Math.random()}`,
    boardId,
    type,
    actorId: 'client-remote',
    occurredAt: '2026-09-01T12:30:00.000Z',
    payload: { element: null, elementId: null, x: null, y: null, ...payload }
  };
}

const created = (element) => event('ELEMENT_CREATED', { element });
const connectorCreated = (element) => event('CONNECTOR_CREATED', { element });
const moved = (elementId, x, y) => event('ELEMENT_MOVED', { elementId, x, y });
const updated = (element) => event('ELEMENT_UPDATED', { element, elementId: element.id });
const deleted = (elementId) => event('ELEMENT_DELETED', { elementId });

function loadedState(elements = [rect('a'), rect('b'), textEl('t'), connector('ab', 'a', 'b'), connector('at', 'a', 't')]) {
  const state = createBoardState();
  state.setBoard({ id: BOARD_ID, name: 'Demo', elements });
  return state;
}

const ids = (state) => state.snapshot().board.elements.map((e) => e.id);
const element = (state, id) => state.snapshot().board.elements.find((e) => e.id === id);

// ---- ignored events ----

test('ignores events when no board is loaded', () => {
  const state = createBoardState();
  const before = state.snapshot();
  state.applyEvent(created(rect('x')));
  assert.deepEqual(state.snapshot(), before);
});

test('ignores events that belong to another board', () => {
  const state = loadedState();
  const before = state.snapshot();
  state.applyEvent(event('ELEMENT_CREATED', { element: rect('x') }, 'other-board'));
  state.applyEvent(event('ELEMENT_DELETED', { elementId: 'a' }, 'other-board'));
  assert.deepEqual(state.snapshot(), before);
});

test('ignores unknown event types, null events and missing payloads without throwing', () => {
  const state = loadedState();
  const before = state.snapshot();
  assert.doesNotThrow(() => state.applyEvent(event('SOMETHING_NEW', {})));
  assert.doesNotThrow(() => state.applyEvent(null));
  assert.doesNotThrow(() => state.applyEvent(undefined));
  assert.doesNotThrow(() => state.applyEvent({ boardId: BOARD_ID, type: 'ELEMENT_CREATED' }));
  assert.deepEqual(state.snapshot(), before);
});

// ---- ELEMENT_CREATED / CONNECTOR_CREATED ----

test('ELEMENT_CREATED adds the element and is idempotent (own echo)', () => {
  const state = loadedState();
  state.applyEvent(created(rect('n', 50, 60)));
  state.applyEvent(created(rect('n', 50, 60)));
  assert.equal(ids(state).filter((id) => id === 'n').length, 1);
  assert.equal(element(state, 'n').x, 50);
});

test('ELEMENT_CREATED with an existing id keeps the existing element', () => {
  const state = loadedState();
  state.applyEvent(created({ ...rect('a'), x: 999, text: 'other' }));
  assert.equal(element(state, 'a').x, 10);
  assert.equal(element(state, 'a').text, 'rect a');
});

test('CONNECTOR_CREATED adds the connector and is idempotent', () => {
  const state = loadedState();
  state.applyEvent(connectorCreated(connector('bt', 'b', 't')));
  state.applyEvent(connectorCreated(connector('bt', 'b', 't')));
  assert.equal(ids(state).filter((id) => id === 'bt').length, 1);
  assert.equal(element(state, 'bt').targetId, 't');
});

// ---- ELEMENT_MOVED ----

test('ELEMENT_MOVED changes only x/y and applying it twice does not corrupt the board', () => {
  const state = loadedState();
  state.applyEvent(moved('a', 420.5, 180));
  const afterFirst = state.snapshot();
  state.applyEvent(moved('a', 420.5, 180));
  assert.deepEqual(state.snapshot(), afterFirst);
  assert.equal(element(state, 'a').x, 420.5);
  assert.equal(element(state, 'a').y, 180);
  assert.equal(element(state, 'a').width, 100);
  assert.equal(element(state, 'a').text, 'rect a');
});

test('ELEMENT_MOVED on an unknown element, a connector or without coordinates is ignored', () => {
  const state = loadedState();
  const before = state.snapshot();
  state.applyEvent(moved('zzz', 1, 2));
  state.applyEvent(moved('ab', 1, 2));
  state.applyEvent(moved('a', null, 2));
  state.applyEvent(moved('a', 1, undefined));
  assert.deepEqual(state.snapshot(), before);
});

test('a remote ELEMENT_MOVED keeps the local selection and connect mode', () => {
  const state = loadedState();
  state.select('b');
  state.beginConnect();
  state.applyEvent(moved('a', 300, 300));
  const snapshot = state.snapshot();
  assert.equal(snapshot.selectedId, 'b');
  assert.equal(snapshot.connectSourceId, 'b');
});

// ---- ELEMENT_UPDATED ----

test('ELEMENT_UPDATED replaces the element and keeps the selection', () => {
  const state = loadedState();
  state.select('a');
  state.applyEvent(updated({ ...rect('a'), text: 'renamed' }));
  assert.equal(element(state, 'a').text, 'renamed');
  assert.equal(state.snapshot().selectedId, 'a');
});

test('ELEMENT_UPDATED on an unknown element is ignored', () => {
  const state = loadedState();
  const before = state.snapshot();
  state.applyEvent(updated(rect('zzz')));
  assert.deepEqual(state.snapshot(), before);
});

// ---- ELEMENT_DELETED ----

test('ELEMENT_DELETED removes the element and the connectors that reference it', () => {
  const state = loadedState();
  state.applyEvent(deleted('a'));
  assert.deepEqual(ids(state), ['b', 't']);
});

test('ELEMENT_DELETED of a connector removes only that connector', () => {
  const state = loadedState();
  state.applyEvent(deleted('ab'));
  assert.deepEqual(ids(state), ['a', 'b', 't', 'at']);
});

test('ELEMENT_DELETED is idempotent and ignores unknown ids', () => {
  const state = loadedState();
  state.applyEvent(deleted('a'));
  const afterFirst = state.snapshot();
  state.applyEvent(deleted('a'));
  state.applyEvent(deleted('zzz'));
  assert.deepEqual(state.snapshot(), afterFirst);
});

test('deleting the selected element clears the selection', () => {
  const state = loadedState();
  state.select('a');
  state.applyEvent(deleted('a'));
  assert.equal(state.snapshot().selectedId, null);
});

test('deleting an element clears a selection that was one of its cascaded connectors', () => {
  const state = loadedState();
  state.select('ab');
  state.applyEvent(deleted('a'));
  assert.equal(state.snapshot().selectedId, null);
});

test('deleting the connect source clears the connect mode', () => {
  const state = loadedState();
  state.select('a');
  state.beginConnect();
  state.select('b');
  state.applyEvent(deleted('a'));
  const snapshot = state.snapshot();
  assert.equal(snapshot.connectSourceId, null);
  assert.equal(snapshot.selectedId, 'b');
});

test('deleting an unrelated element keeps the selection and the connect mode', () => {
  const state = loadedState();
  state.select('b');
  state.beginConnect();
  state.applyEvent(deleted('t'));
  const snapshot = state.snapshot();
  assert.equal(snapshot.selectedId, 'b');
  assert.equal(snapshot.connectSourceId, 'b');
  assert.deepEqual(ids(state), ['a', 'b', 'ab']);
});

// ---- state contract ----

test('applyEvent returns the new snapshot and never hands out internal state', () => {
  const state = loadedState();
  const before = state.snapshot();
  const returned = state.applyEvent(created(rect('n')));
  assert.deepEqual(returned, state.snapshot());
  assert.equal(before.board.elements.length, 5);
  returned.board.elements.length = 0;
  assert.equal(state.snapshot().board.elements.length, 6);
});

test('the event payload is copied, not shared with the state', () => {
  const state = loadedState();
  const incoming = created(rect('n'));
  state.applyEvent(incoming);
  incoming.payload.element.x = 12345;
  assert.equal(element(state, 'n').x, 10);
});

test('applyEvent does not touch the REST status or the board name', () => {
  const state = loadedState();
  state.setRemote('loading', 'save');
  state.applyEvent(created(rect('n')));
  const snapshot = state.snapshot();
  assert.equal(snapshot.remote.status, 'loading');
  assert.equal(snapshot.board.name, 'Demo');
});

if (failures > 0) {
  console.log(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall tests passed');
