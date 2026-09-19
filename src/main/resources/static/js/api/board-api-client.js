export class BoardApiError extends Error {
  constructor(status, code, message){ super(message); this.name='BoardApiError'; this.status=status; this.code=code; }
}

const BASE = '/api/boards';
const JSON_HEADERS = {'Content-Type':'application/json'};

function requireId(id){
  const value = typeof id === 'string' ? id.trim() : '';
  if(!value) throw new BoardApiError(0, 'INVALID_ID', 'Board id is required');
  return encodeURIComponent(value);
}

// The only place that knows about fetch, URLs and HTTP status codes.
async function request(url, options){
  let response;
  try { response = await fetch(url, options); }
  catch { throw new BoardApiError(0, 'NETWORK_ERROR', 'Could not reach the server'); }
  const payload = await response.json().catch(() => null);
  if(!response.ok){ throw new BoardApiError(response.status, payload?.code ?? 'HTTP_ERROR', payload?.message ?? `HTTP ${response.status}`); }
  return payload;
}

export const BoardApiClient = {
  async create(name){
    return request(BASE, {method:'POST', headers:JSON_HEADERS, body:JSON.stringify({name})});
  },
  async load(id){
    return request(`${BASE}/${requireId(id)}`);
  },
  // Full replacement (PUT) of the board: no partial /move or /draw endpoints.
  async save(board){
    return request(`${BASE}/${requireId(board?.id)}`, {method:'PUT', headers:JSON_HEADERS, body:JSON.stringify({name:board.name, elements:board.elements})});
  }
};
