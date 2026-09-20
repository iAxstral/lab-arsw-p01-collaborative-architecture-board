function event(type, boardId, actorId, payload){
  return {
    eventId: crypto.randomUUID(),
    boardId,
    type,
    actorId,
    occurredAt: new Date().toISOString(),
    payload
  };
}

export const BoardEvents = {
  elementCreated(boardId, actorId, element){ return event('ELEMENT_CREATED', boardId, actorId, {element, elementId:null, x:null, y:null}); },
  connectorCreated(boardId, actorId, element){ return event('CONNECTOR_CREATED', boardId, actorId, {element, elementId:null, x:null, y:null}); },
  elementMoved(boardId, actorId, elementId, x, y){ return event('ELEMENT_MOVED', boardId, actorId, {element:null, elementId, x, y}); },
  elementUpdated(boardId, actorId, element){ return event('ELEMENT_UPDATED', boardId, actorId, {element, elementId:element.id, x:null, y:null}); },
  elementDeleted(boardId, actorId, elementId){ return event('ELEMENT_DELETED', boardId, actorId, {element:null, elementId, x:null, y:null}); }
};
