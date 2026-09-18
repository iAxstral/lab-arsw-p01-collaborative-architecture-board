const SVG_NS = 'http://www.w3.org/2000/svg';

const COLORS = {
  rectFill: '#eaf2fb',
  rectStroke: '#33445a',
  selectedStroke: '#e08a2c',
  connectSourceStroke: '#8a4fd6',
  textFill: '#1d2733',
  connectorStroke: '#5b6b7c',
  connectorSelected: '#e08a2c'
};

function createSvgElement(tag, attrs = {}) {
  const node = document.createElementNS(SVG_NS, tag);
  for (const [key, value] of Object.entries(attrs)) {
    node.setAttribute(key, value);
  }
  return node;
}

function toSvgPoint(svg, clientX, clientY) {
  const point = svg.createSVGPoint();
  point.x = clientX;
  point.y = clientY;
  const ctm = svg.getScreenCTM();
  if (!ctm) {
    return { x: clientX, y: clientY };
  }
  const local = point.matrixTransform(ctm.inverse());
  return { x: local.x, y: local.y };
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function center(element) {
  return { x: element.x + element.width / 2, y: element.y + element.height / 2 };
}

function statusText(remote) {
  const action = remote.lastAction;
  switch (remote.status) {
    case 'loading':
      if (action === 'create') return 'Creating board…';
      if (action === 'load') return 'Loading board…';
      if (action === 'save') return 'Saving changes…';
      return 'Working…';
    case 'success':
      if (action === 'create') return 'Board created.';
      if (action === 'load') return 'Board loaded.';
      if (action === 'save') return 'Changes saved.';
      return 'Done.';
    case 'error':
      return remote.error || 'The last operation failed.';
    default:
      return 'Create a board or load one by id to get started.';
  }
}

/**
 * Pure projection of a board-state snapshot onto the SVG canvas plus the
 * status/retry chrome. Never imports BoardApiClient and never mutates the
 * state it is given — user interaction is only ever reported upward via
 * `handlers`.
 */
export function createBoardView(svg, messageEl, retryBtn, handlers) {
  let drag = null;

  function endDrag(event) {
    if (drag && svg.hasPointerCapture?.(event.pointerId)) {
      svg.releasePointerCapture(event.pointerId);
    }
    drag = null;
  }

  svg.addEventListener('pointerdown', (event) => {
    if (event.target === svg) {
      handlers.onCanvasPointerDown();
    }
  });

  svg.addEventListener('pointermove', (event) => {
    if (!drag) return;
    const point = toSvgPoint(svg, event.clientX, event.clientY);
    const viewBox = svg.viewBox.baseVal;
    const x = clamp(point.x - drag.offsetX, 0, Math.max(0, viewBox.width - drag.width));
    const y = clamp(point.y - drag.offsetY, 0, Math.max(0, viewBox.height - drag.height));
    handlers.onElementDrag(x, y);
  });

  svg.addEventListener('pointerup', endDrag);
  svg.addEventListener('pointercancel', endDrag);

  function startDrag(element, event) {
    const point = toSvgPoint(svg, event.clientX, event.clientY);
    drag = {
      offsetX: point.x - element.x,
      offsetY: point.y - element.y,
      width: element.width,
      height: element.height
    };
    svg.setPointerCapture(event.pointerId);
  }

  function attachInteraction(node, element) {
    node.addEventListener('pointerdown', (event) => {
      event.stopPropagation();
      handlers.onElementPointerDown(element.id);
      startDrag(element, event);
    });
  }

  function renderRectangle(element, selectedId, connectSourceId) {
    const isSource = element.id === connectSourceId;
    const isSelected = element.id === selectedId;
    const group = createSvgElement('g', { 'data-id': element.id, 'data-type': 'RECTANGLE' });
    group.classList.add('board-element');

    const rect = createSvgElement('rect', {
      x: element.x, y: element.y, width: element.width, height: element.height,
      rx: 6,
      fill: COLORS.rectFill,
      stroke: isSource ? COLORS.connectSourceStroke : isSelected ? COLORS.selectedStroke : COLORS.rectStroke,
      'stroke-width': isSource || isSelected ? 3 : 2,
      'stroke-dasharray': isSource ? '6 4' : 'none'
    });

    const label = createSvgElement('text', {
      x: element.x + element.width / 2, y: element.y + element.height / 2,
      'text-anchor': 'middle', 'dominant-baseline': 'middle',
      fill: COLORS.textFill, 'font-size': 14, 'font-family': 'system-ui, sans-serif'
    });
    label.textContent = element.text;
    label.style.pointerEvents = 'none';

    group.append(rect, label);
    attachInteraction(group, element);
    return group;
  }

  function renderText(element, selectedId, connectSourceId) {
    const isSource = element.id === connectSourceId;
    const isSelected = element.id === selectedId;
    const group = createSvgElement('g', { 'data-id': element.id, 'data-type': 'TEXT' });
    group.classList.add('board-element');

    const hit = createSvgElement('rect', {
      x: element.x, y: element.y, width: element.width, height: element.height,
      fill: 'transparent',
      stroke: isSource || isSelected ? (isSource ? COLORS.connectSourceStroke : COLORS.selectedStroke) : 'none',
      'stroke-width': 2,
      'stroke-dasharray': isSource ? '6 4' : 'none'
    });

    const label = createSvgElement('text', {
      x: element.x + 8, y: element.y + element.height / 2,
      'dominant-baseline': 'middle',
      fill: COLORS.textFill, 'font-size': 14, 'font-family': 'system-ui, sans-serif'
    });
    label.textContent = element.text;
    label.style.pointerEvents = 'none';

    group.append(hit, label);
    attachInteraction(group, element);
    return group;
  }

  function renderConnector(element, byId, selectedId) {
    const source = byId.get(element.sourceId);
    const target = byId.get(element.targetId);
    if (!source || !target) return null;

    const from = center(source);
    const to = center(target);
    const isSelected = element.id === selectedId;
    const line = createSvgElement('line', {
      x1: from.x, y1: from.y, x2: to.x, y2: to.y,
      stroke: isSelected ? COLORS.connectorSelected : COLORS.connectorStroke,
      'stroke-width': isSelected ? 3 : 2,
      'data-id': element.id, 'data-type': 'CONNECTOR'
    });
    line.classList.add('board-connector');
    line.addEventListener('pointerdown', (event) => {
      event.stopPropagation();
      handlers.onElementPointerDown(element.id);
    });
    return line;
  }

  function renderCanvas(snapshot) {
    const { board, selectedId, connectSourceId } = snapshot;
    const byId = new Map(board.elements.map((element) => [element.id, element]));

    while (svg.firstChild) {
      svg.removeChild(svg.firstChild);
    }

    const connectorsLayer = createSvgElement('g', { class: 'connectors-layer' });
    const shapesLayer = createSvgElement('g', { class: 'shapes-layer' });

    for (const element of board.elements) {
      if (element.type === 'CONNECTOR') {
        const line = renderConnector(element, byId, selectedId);
        if (line) connectorsLayer.appendChild(line);
      } else if (element.type === 'RECTANGLE') {
        shapesLayer.appendChild(renderRectangle(element, selectedId, connectSourceId));
      } else if (element.type === 'TEXT') {
        shapesLayer.appendChild(renderText(element, selectedId, connectSourceId));
      }
    }

    svg.append(connectorsLayer, shapesLayer);
  }

  function renderMessage(snapshot, hint) {
    messageEl.textContent = hint || statusText(snapshot.remote);
    messageEl.className = hint ? 'message message--hint' : `message message--${snapshot.remote.status}`;
    retryBtn.hidden = snapshot.remote.status !== 'error';
  }

  function render(snapshot, hint = null) {
    renderCanvas(snapshot);
    renderMessage(snapshot, hint);
  }

  return { render };
}
