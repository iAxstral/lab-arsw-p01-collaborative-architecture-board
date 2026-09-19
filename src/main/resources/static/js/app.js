import {BoardApiClient} from './api/board-api-client.js';
import {createBoardState} from './state/board-state.js';
import {createBoardView} from './ui/board-view.js';

// BoardApp wires BoardApiClient, BoardState and BoardView. It owns the remote-call workflow
// (Loading / Success / Error / Retry); the other three modules never know about each other.
const state=createBoardState();
const view=createBoardView(document.querySelector('#boardCanvas'));
const $=id=>document.getElementById(id);

const STATUS_LABEL={idle:'Idle',loading:'Loading',success:'Success',error:'Error'};
const REMOTE_BUTTONS=['newBoardBtn','loadBtn','saveBtn','retryBtn'];
const LOCAL_BUTTONS=['addRectBtn','addTextBtn','connectBtn','deleteBtn'];

// Closure of the last failed remote operation. It is not part of BoardState because it is not data.
let retryAction=null;
let notice='';

function refresh(){
  const s=state.snapshot();
  const {status,error}=s.remote;
  view.render(s);

  const statusEl=$('remoteStatus');
  statusEl.textContent=STATUS_LABEL[status];
  statusEl.dataset.status=status;

  const messageEl=$('message');
  messageEl.textContent=status==='error'?error.message:notice;
  messageEl.dataset.kind=status==='error'?'error':'info';

  const busy=status==='loading';
  REMOTE_BUTTONS.forEach(id=>{$(id).disabled=busy;});
  LOCAL_BUTTONS.forEach(id=>{$(id).disabled=busy;});
  $('retryBtn').hidden = !(status==='error' && retryAction);
  $('saveBtn').disabled=busy || !s.board.id;
  $('connectBtn').classList.toggle('active',Boolean(s.connectSourceId));
  $('boardDirty').hidden=!s.dirty;

  if(s.board.id) $('boardId').value=s.board.id;
  if(document.activeElement!==$('boardName')) $('boardName').value=s.board.name;
}

function say(message){ notice=message; refresh(); }

// Runs one remote operation and drives the remote status. Ignored while another one is in flight.
async function remote(label,action,onSuccess){
  if(state.snapshot().remote.status==='loading') return;
  state.setRemote('loading',label);
  notice=`${label}...`; retryAction=null; refresh();
  try{
    const result=await action();
    onSuccess(result);
    state.setRemote('success',label);
    retryAction=null;
  }catch(error){
    state.setRemote('error',label,error);
    retryAction=()=>remote(label,action,onSuccess);
  }
  refresh();
}

const applyBoard=message=>board=>{ state.setBoard(board); notice=message; };
const confirmDiscard=()=>!state.snapshot().dirty || confirm('There are unsaved changes. Discard them?');

view.on({
  select(id){
    const {connectSourceId}=state.snapshot();
    if(connectSourceId && id){
      const created=state.completeConnect(id);
      say(created?'Connector created locally. Save to persist.':'Connector not created: pick a different element that is not already connected.');
      return;
    }
    state.select(id); refresh();
  },
  move(id,x,y){ state.select(id); state.moveSelected(x,y); refresh(); },
  remove(){ if(state.removeSelected()) say('Element removed locally'); },
  edit(id){
    state.select(id);
    const current=state.snapshot().board.elements.find(e=>e.id===id);
    if(!current || current.type==='CONNECTOR') return;
    const text=prompt('Element text',current.text);
    if(text!==null){ state.renameSelected(text); say('Text updated locally'); } else refresh();
  }
});

$('newBoardBtn').onclick=()=>{
  if(!confirmDiscard()) return;
  const name=$('boardName').value.trim();
  remote('Creating',()=>BoardApiClient.create(name),applyBoard('Board created'));
};
$('loadBtn').onclick=()=>{
  if(!confirmDiscard()) return;
  const id=$('boardId').value.trim();
  remote('Loading',()=>BoardApiClient.load(id),applyBoard('Board loaded'));
};
$('saveBtn').onclick=()=>{
  state.setName($('boardName').value.trim());
  remote('Saving',()=>BoardApiClient.save(state.toPersistedBoard()),applyBoard('Board saved'));
};
$('retryBtn').onclick=()=>{ if(retryAction) retryAction(); };

$('addRectBtn').onclick=()=>{ state.addRectangle(); say('Rectangle added locally'); };
$('addTextBtn').onclick=()=>{ state.addText(); say('Text added locally'); };
$('connectBtn').onclick=()=>{
  if(state.snapshot().connectSourceId){ state.cancelConnect(); say('Connect cancelled'); return; }
  say(state.beginConnect()?'Select the target element':'Select a rectangle or text first');
};
$('deleteBtn').onclick=()=>{ say(state.removeSelected()?'Element removed locally':'Nothing selected'); };

refresh();
