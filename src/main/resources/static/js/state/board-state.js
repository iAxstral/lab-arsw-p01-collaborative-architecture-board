function uid(prefix) {
  return `${prefix}-${crypto.randomUUID()}`;
}

export function createBoardState() {
  let board = { id: null, name: 'Architecture Board', elements: [] };
  let selectedId = null;
  let connectSourceId = null;
  let remote = { status: 'idle', lastAction: null, error: null };

  function snapshot() {
    return structuredClone({ board, selectedId, connectSourceId, remote });
  }

  function hasElement(id) {
    return board.elements.some(e => e.id === id);
  }

  function isValidElement(element) {
    return Boolean(element) && typeof element.id === 'string' && element.id !== '';
  }

  function addRemoteElement(element) {
    if (!isValidElement(element) || hasElement(element.id)) return;
    board = { ...board, elements: [...board.elements, structuredClone(element)] };
  }

  function moveRemoteElement(elementId, x, y) {
    if (!Number.isFinite(x) || !Number.isFinite(y)) return;
    board = {
      ...board,
      elements: board.elements.map(e =>
        e.id === elementId && e.type !== 'CONNECTOR' ? { ...e, x, y } : e
      )
    };
  }

  function replaceRemoteElement(element) {
    if (!isValidElement(element) || !hasElement(element.id)) return;
    board = {
      ...board,
      elements: board.elements.map(e => e.id === element.id ? structuredClone(element) : e)
    };
  }

  function removeRemoteElement(elementId) {
    if (!hasElement(elementId)) return;
    const removedIds = new Set(
      board.elements
        .filter(e => e.id === elementId || e.sourceId === elementId || e.targetId === elementId)
        .map(e => e.id)
    );
    board = { ...board, elements: board.elements.filter(e => !removedIds.has(e.id)) };
    if (removedIds.has(selectedId)) selectedId = null;
    if (removedIds.has(connectSourceId)) connectSourceId = null;
  }

  return {
    snapshot,
    setBoard(next) {
      board = structuredClone(next);
      selectedId = null;
      connectSourceId = null;
    },
    setName(name) {
      board = { ...board, name };
    },
    select(id) {
      selectedId = id;
    },
    setRemote(status, lastAction = null, error = null) {
      remote = { status, lastAction, error };
    },
    addRectangle() {
      const e = {
        id: uid('rect'), type: 'RECTANGLE',
        x: 100 + board.elements.length * 12, y: 90 + board.elements.length * 12,
        width: 170, height: 70, text: 'Component', sourceId: null, targetId: null
      };
      board = { ...board, elements: [...board.elements, e] };
      selectedId = e.id;
      return e;
    },
    addText() {
      const e = {
        id: uid('text'), type: 'TEXT',
        x: 120, y: 210, width: 150, height: 30, text: 'Text', sourceId: null, targetId: null
      };
      board = { ...board, elements: [...board.elements, e] };
      selectedId = e.id;
      return e;
    },
    moveSelected(x, y) {
      board = {
        ...board,
        elements: board.elements.map(e =>
          e.id === selectedId && e.type !== 'CONNECTOR' ? { ...e, x, y } : e
        )
      };
    },
    beginConnect() {
      if (selectedId) connectSourceId = selectedId;
    },
    completeConnect(targetId) {
      if (!connectSourceId || !targetId || connectSourceId === targetId) return null;
      const e = {
        id: uid('conn'), type: 'CONNECTOR',
        x: 0, y: 0, width: 0, height: 0, text: '',
        sourceId: connectSourceId, targetId
      };
      board = { ...board, elements: [...board.elements, e] };
      connectSourceId = null;
      selectedId = e.id;
      return e;
    },
    removeSelected() {
      if (!selectedId) return;
      const removed = selectedId;
      board = {
        ...board,
        elements: board.elements.filter(e => e.id !== removed && e.sourceId !== removed && e.targetId !== removed)
      };
      selectedId = null;
    },
    applyEvent(event) {
      if (!board.id || event?.boardId !== board.id) return snapshot();
      const payload = event.payload ?? {};
      switch (event.type) {
        case 'ELEMENT_CREATED':
        case 'CONNECTOR_CREATED':
          addRemoteElement(payload.element);
          break;
        case 'ELEMENT_MOVED':
          moveRemoteElement(payload.elementId, payload.x, payload.y);
          break;
        case 'ELEMENT_UPDATED':
          replaceRemoteElement(payload.element);
          break;
        case 'ELEMENT_DELETED':
          removeRemoteElement(payload.elementId);
          break;
        default:
          break;
      }
      return snapshot();
    },
    toPersistedBoard() {
      return structuredClone(board);
    }
  };
}
