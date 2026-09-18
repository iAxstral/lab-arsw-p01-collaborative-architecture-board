export class BoardApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function parse(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new BoardApiError(
      response.status,
      payload?.code ?? 'HTTP_ERROR',
      payload?.message ?? `HTTP ${response.status}`
    );
  }
  return payload;
}

export const BoardApiClient = {
  async create(name) {
    const response = await fetch('/api/boards', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name })
    });
    return parse(response);
  },

  async load(id) {
    if (!id || !id.trim()) {
      throw new BoardApiError(400, 'INVALID_INPUT', 'A board id is required to load a board');
    }
    const response = await fetch(`/api/boards/${encodeURIComponent(id)}`);
    return parse(response);
  },

  async save(board) {
    const response = await fetch(`/api/boards/${encodeURIComponent(board.id)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: board.name, elements: board.elements })
    });
    return parse(response);
  }
};
