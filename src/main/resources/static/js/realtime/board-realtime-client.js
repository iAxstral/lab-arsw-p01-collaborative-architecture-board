export function createBoardRealtimeClient({ onEvent = () => {}, onStatus = () => {} } = {}) {
  let client = null;
  let socket = null;
  let subscription = null;
  let currentBoardId = null;

  function webSocketUrl() {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${location.host}/ws`;
  }

  function reset() {
    client = null;
    socket = null;
    subscription = null;
    currentBoardId = null;
  }

  async function connect(boardId) {
    if (!boardId) throw new Error('boardId is required before connecting');
    if (!window.Stomp) throw new Error('STOMP client library was not loaded');
    if (client?.connected && currentBoardId === boardId) return;
    if (client) await disconnect();

    await new Promise((resolve, reject) => {
      onStatus('connecting');
      const ws = new WebSocket(webSocketUrl());
      const stomp = window.Stomp.over(ws);
      stomp.debug = () => {};
      client = stomp;
      socket = ws;
      stomp.connect({}, () => {
        if (client !== stomp) {
          stomp.disconnect();
          reject(new Error('Connection was cancelled'));
          return;
        }
        currentBoardId = boardId;
        subscription = stomp.subscribe(`/topic/boards/${boardId}`, message => {
          try {
            onEvent(JSON.parse(message.body));
          } catch (error) {
            console.error('Invalid board event', error);
          }
        });
        onStatus('connected');
        resolve();
      }, error => {
        if (client === stomp) {
          reset();
          onStatus('error');
        }
        reject(error instanceof Error ? error : new Error(String(error)));
      });
    });
  }

  function publish(event) {
    if (!client?.connected) throw new Error('Not connected to the live channel');
    if (!event || event.boardId !== currentBoardId) throw new Error('The event does not belong to the connected board');
    client.send(`/app/boards/${currentBoardId}/events`, { 'content-type': 'application/json' }, JSON.stringify(event));
  }

  function disconnect() {
    return new Promise(resolve => {
      try {
        subscription?.unsubscribe();
      } catch {
        // the socket is already closed: nothing to unsubscribe from
      }
      const finish = () => {
        reset();
        onStatus('disconnected');
        resolve();
      };
      if (client?.connected) {
        client.disconnect(finish);
      } else {
        socket?.close();
        finish();
      }
    });
  }

  return {
    connect,
    publish,
    disconnect,
    isConnected() {
      return Boolean(client?.connected);
    },
    boardId() {
      return currentBoardId;
    }
  };
}
