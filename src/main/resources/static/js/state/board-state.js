function uid(prefix){ return `${prefix}-${crypto.randomUUID()}`; }

const isShape = e => e && e.type !== 'CONNECTOR';

// Holds every piece of client state: board, selection, connect mode, dirty flag and remote status.
// It knows nothing about the DOM or HTTP. Snapshots contain plain data only.
export function createBoardState(){
  let board={id:null,name:'Architecture Board',elements:[]};
  let selectedId=null;
  let connectSourceId=null;
  let dirty=false;
  let remote={status:'idle',operation:null,error:null};

  const find = id => board.elements.find(e=>e.id===id);
  const replaceElements = elements => { board={...board,elements}; dirty=true; };
  const patch = (id,changes) => replaceElements(board.elements.map(e=>e.id===id?{...e,...changes}:e));
  const add = e => { replaceElements([...board.elements,e]); selectedId=e.id; return e; };

  return {
    snapshot(){ return structuredClone({board,selectedId,connectSourceId,dirty,remote}); },

    setBoard(next){ board=structuredClone(next); selectedId=null; connectSourceId=null; dirty=false; },
    setName(name){ if(name!==board.name){ board={...board,name}; dirty=true; } },
    select(id){ selectedId=find(id)?id:null; },
    setRemote(status,operation=null,error=null){
      remote={status,operation,error:error?{code:error.code??'ERROR',message:error.message}:null};
    },

    addRectangle(){
      const n=board.elements.length;
      return add({id:uid('rect'),type:'RECTANGLE',x:100+n*12,y:90+n*12,width:170,height:70,text:'Component',sourceId:null,targetId:null});
    },
    addText(){
      const n=board.elements.length;
      return add({id:uid('text'),type:'TEXT',x:120+n*12,y:210+n*12,width:150,height:30,text:'Text',sourceId:null,targetId:null});
    },

    // Moves the selected shape; connectors follow their endpoints and cannot be moved.
    moveSelected(x,y){
      const e=find(selectedId);
      if(!isShape(e) || !Number.isFinite(x) || !Number.isFinite(y)) return false;
      patch(e.id,{x:Math.round(x),y:Math.round(y)});
      return true;
    },
    renameSelected(text){
      const e=find(selectedId);
      if(!isShape(e)) return false;
      patch(e.id,{text}); return true;
    },

    beginConnect(){
      if(!isShape(find(selectedId))) return false;
      connectSourceId=selectedId; return true;
    },
    cancelConnect(){ connectSourceId=null; },
    // Creates a connector only between two different, existing shapes and never duplicates one.
    completeConnect(targetId){
      const source=find(connectSourceId), target=find(targetId);
      if(!isShape(source) || !isShape(target) || source.id===target.id) return null;
      const exists=board.elements.some(e=>e.type==='CONNECTOR' && e.sourceId===source.id && e.targetId===target.id);
      connectSourceId=null;
      if(exists) return null;
      return add({id:uid('conn'),type:'CONNECTOR',x:0,y:0,width:0,height:0,text:'',sourceId:source.id,targetId:target.id});
    },

    // Removing a shape also removes the connectors attached to it.
    removeSelected(){
      if(!find(selectedId)) return false;
      const removed=selectedId;
      replaceElements(board.elements.filter(e=>e.id!==removed && e.sourceId!==removed && e.targetId!==removed));
      selectedId=null;
      if(connectSourceId===removed) connectSourceId=null;
      return true;
    },

    toPersistedBoard(){ return structuredClone(board); }
  };
}
