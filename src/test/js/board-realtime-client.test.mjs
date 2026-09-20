// Run from the repository root (Node 20+, no dependencies):
//   node src/test/js/board-realtime-client.test.mjs
// STOMP and WebSocket are replaced by in-memory fakes: only the module's own logic is tested.
import assert from 'node:assert/strict';

globalThis.location = { protocol: 'http:', host: 'localhost:8080' };

const sockets = [];
globalThis.WebSocket = class {
  constructor(url) {
    this.url = url;
    this.closed = false;
    sockets.push(this);
  }

  close() {
    this.closed = true;
  }
};

const stomps = [];
globalThis.window = {
  Stomp: {
    over(ws) {
      const stomp = {
        ws,
        connected: false,
        disconnected: false,
        sent: [],
        subscriptions: [],
        debug() {},
        connect(headers, onConnected, onError) {
          stomp.succeed = () => { stomp.connected = true; onConnected(); };
          stomp.fail = (error) => onError(error);
        },
        subscribe(destination, callback) {
          const subscription = { destination, callback, unsubscribed: false, unsubscribe() { subscription.unsubscribed = true; } };
          stomp.subscriptions.push(subscription);
          return subscription;
        },
        send(destination, headers, body) {
          stomp.sent.push({ destination, headers, body });
        },
        disconnect(callback) {
          stomp.connected = false;
          stomp.disconnected = true;
          callback?.();
        }
      };
      stomps.push(stomp);
      return stomp;
    }
  }
};

const { createBoardRealtimeClient } = await import('../../main/resources/static/js/realtime/board-realtime-client.js');

let failures = 0;
async function test(name, fn) {
  sockets.length = 0;
  stomps.length = 0;
  try {
    await fn();
    console.log(`ok   ${name}`);
  } catch (error) {
    failures += 1;
    console.log(`FAIL ${name}\n     ${error.message}`);
  }
}

function newClient() {
  const statuses = [];
  const events = [];
  const client = createBoardRealtimeClient({ onStatus: (s) => statuses.push(s), onEvent: (e) => events.push(e) });
  return { client, statuses, events };
}

async function connected(client, boardId) {
  const pending = client.connect(boardId);
  stomps.at(-1).succeed();
  await pending;
}

const eventFor = (boardId) => ({ eventId: 'e1', boardId, type: 'ELEMENT_MOVED', actorId: 'a', occurredAt: 'x', payload: {} });

await test('publish is rejected while disconnected', () => {
  const { client } = newClient();
  assert.throws(() => client.publish(eventFor('b1')), /Not connected/);
});

await test('connect needs a boardId and the STOMP library', async () => {
  const { client } = newClient();
  await assert.rejects(client.connect(''), /boardId is required/);
  const library = window.Stomp;
  window.Stomp = undefined;
  await assert.rejects(client.connect('b1'), /STOMP client library/);
  window.Stomp = library;
});

await test('connect subscribes to the board topic and reports its status', async () => {
  const { client, statuses } = newClient();
  await connected(client, 'b1');
  assert.deepEqual(statuses, ['connecting', 'connected']);
  assert.equal(client.isConnected(), true);
  assert.equal(client.boardId(), 'b1');
  assert.equal(sockets[0].url, 'ws://localhost:8080/ws');
  assert.equal(stomps[0].subscriptions[0].destination, '/topic/boards/b1');
});

await test('publish serializes the event and sends it to the board destination', async () => {
  const { client } = newClient();
  await connected(client, 'b1');
  const event = eventFor('b1');
  client.publish(event);
  const [sent] = stomps[0].sent;
  assert.equal(sent.destination, '/app/boards/b1/events');
  assert.equal(sent.headers['content-type'], 'application/json');
  assert.deepEqual(JSON.parse(sent.body), event);
});

await test('publish rejects an event that belongs to another board', async () => {
  const { client } = newClient();
  await connected(client, 'b1');
  assert.throws(() => client.publish(eventFor('b2')), /does not belong/);
  assert.equal(stomps[0].sent.length, 0);
});

await test('accepted events are handed to onEvent as parsed objects; invalid frames are ignored', async () => {
  const { client, events } = newClient();
  await connected(client, 'b1');
  const original = console.error;
  console.error = () => {};
  stomps[0].subscriptions[0].callback({ body: JSON.stringify(eventFor('b1')) });
  stomps[0].subscriptions[0].callback({ body: 'not json' });
  console.error = original;
  assert.equal(events.length, 1);
  assert.equal(events[0].eventId, 'e1');
});

await test('connecting to the same board again keeps the existing connection', async () => {
  const { client } = newClient();
  await connected(client, 'b1');
  await client.connect('b1');
  assert.equal(sockets.length, 1);
  assert.equal(stomps.length, 1);
});

await test('connecting to another board closes the previous connection first', async () => {
  const { client, statuses } = newClient();
  await connected(client, 'b1');
  const first = stomps[0];
  const pending = client.connect('b2');
  await new Promise(resolve => setImmediate(resolve));
  stomps[1].succeed();
  await pending;
  assert.equal(first.disconnected, true);
  assert.equal(first.subscriptions[0].unsubscribed, true);
  assert.equal(stomps.length, 2);
  assert.equal(client.boardId(), 'b2');
  assert.deepEqual(statuses, ['connecting', 'connected', 'disconnected', 'connecting', 'connected']);
});

await test('a failed connection reports the error and leaves nothing connected', async () => {
  const { client, statuses } = newClient();
  const pending = client.connect('b1');
  stomps[0].fail('handshake refused');
  await assert.rejects(pending, /handshake refused/);
  assert.deepEqual(statuses, ['connecting', 'error']);
  assert.equal(client.isConnected(), false);
  assert.equal(client.boardId(), null);
});

await test('disconnect closes the connection and publish is rejected afterwards', async () => {
  const { client, statuses } = newClient();
  await connected(client, 'b1');
  await client.disconnect();
  assert.equal(stomps[0].disconnected, true);
  assert.equal(client.isConnected(), false);
  assert.equal(statuses.at(-1), 'disconnected');
  assert.throws(() => client.publish(eventFor('b1')), /Not connected/);
});

await test('disconnecting while still connecting closes the socket and cancels the late handshake', async () => {
  const { client } = newClient();
  const pending = client.connect('b1');
  const settled = pending.catch(error => error);
  await client.disconnect();
  assert.equal(sockets[0].closed, true);
  stomps[0].succeed();
  const result = await settled;
  assert.match(result.message, /cancelled/);
  assert.equal(stomps[0].disconnected, true);
  assert.equal(client.isConnected(), false);
});

if (failures > 0) {
  console.log(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall tests passed');
