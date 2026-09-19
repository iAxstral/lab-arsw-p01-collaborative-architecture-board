const NS='http://www.w3.org/2000/svg';
const VIEW={width:1000,height:600};

function svgEl(name,attrs={}){ const e=document.createElementNS(NS,name); Object.entries(attrs).forEach(([k,v])=>e.setAttribute(k,v)); return e; }
function center(e){ return {x:e.x+e.width/2,y:e.y+e.height/2}; }
const clamp=(v,min,max)=>Math.min(Math.max(v,min),Math.max(min,max));

// Pure projection of a BoardState snapshot onto SVG. It keeps no business state:
// user gestures are translated into events for BoardApp through on({...}).
export function createBoardView(canvas){
  let handlers={select:()=>{},move:()=>{},remove:()=>{},edit:()=>{}};
  let current=null;
  let drag=null;

  const cls=(base,on,extra)=>[base,on&&'selected',extra].filter(Boolean).join(' ');

  function render(snapshot){
    current=snapshot;
    canvas.replaceChildren();
    const {board,selectedId,connectSourceId}=snapshot;
    const shapes=board.elements.filter(e=>e.type!=='CONNECTOR');
    const byId=new Map(shapes.map(e=>[e.id,e]));

    for(const e of board.elements.filter(e=>e.type==='CONNECTOR')){
      const a=byId.get(e.sourceId), b=byId.get(e.targetId); if(!a||!b) continue;
      const ca=center(a), cb=center(b);
      const g=svgEl('g',{'data-id':e.id,class:'connector-group'});
      g.append(svgEl('line',{x1:ca.x,y1:ca.y,x2:cb.x,y2:cb.y,class:'connector-hit'}));
      g.append(svgEl('line',{x1:ca.x,y1:ca.y,x2:cb.x,y2:cb.y,class:cls('connector',selectedId===e.id)}));
      canvas.append(g);
    }

    for(const e of shapes){
      const g=svgEl('g',{'data-id':e.id,class:'shape'});
      const selected=selectedId===e.id;
      if(e.type==='RECTANGLE'){
        g.append(svgEl('rect',{x:e.x,y:e.y,width:e.width,height:e.height,rx:8,fill:'#e8f0f7',stroke:'#597995',class:cls('',selected,connectSourceId===e.id&&'connect-source')}));
        const t=svgEl('text',{x:e.x+12,y:e.y+e.height/2+5,class:'label'}); t.textContent=e.text||'Component'; g.append(t);
      } else if(e.type==='TEXT'){
        g.append(svgEl('rect',{x:e.x-4,y:e.y-2,width:Math.max(e.width,40),height:e.height+4,fill:'transparent',stroke:'none',class:cls('',selected,connectSourceId===e.id&&'connect-source')}));
        const t=svgEl('text',{x:e.x,y:e.y+20,'font-size':20,fill:'#1d2733',class:'label'}); t.textContent=e.text||'Text'; g.append(t);
      }
      canvas.append(g);
    }
    canvas.classList.toggle('connecting',Boolean(connectSourceId));
  }

  function toSvgPoint(ev){
    const pt=canvas.createSVGPoint(); pt.x=ev.clientX; pt.y=ev.clientY;
    return pt.matrixTransform(canvas.getScreenCTM().inverse());
  }

  canvas.addEventListener('pointerdown',ev=>{
    const node=ev.target.closest?.('[data-id]');
    if(!node){ handlers.select(null); return; }
    const id=node.dataset.id;
    handlers.select(id);
    const el=current?.board.elements.find(e=>e.id===id);
    if(!el || el.type==='CONNECTOR' || current.connectSourceId) return;
    const p=toSvgPoint(ev);
    drag={id,dx:p.x-el.x,dy:p.y-el.y,width:el.width,height:el.height};
    canvas.setPointerCapture(ev.pointerId);
  });
  canvas.addEventListener('pointermove',ev=>{
    if(!drag) return;
    const p=toSvgPoint(ev);
    handlers.move(drag.id,clamp(p.x-drag.dx,0,VIEW.width-drag.width),clamp(p.y-drag.dy,0,VIEW.height-drag.height));
  });
  const endDrag=()=>{drag=null;};
  canvas.addEventListener('pointerup',endDrag);
  canvas.addEventListener('pointercancel',endDrag);
  canvas.addEventListener('dblclick',ev=>{
    const node=ev.target.closest?.('[data-id]'); if(node) handlers.edit(node.dataset.id);
  });
  canvas.addEventListener('keydown',ev=>{
    if(ev.key==='Delete'||ev.key==='Backspace'){ ev.preventDefault(); handlers.remove(); }
  });

  return {render,on(next){handlers={...handlers,...next};}};
}
