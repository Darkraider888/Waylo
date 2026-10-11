document.addEventListener('DOMContentLoaded',()=>{
 const wall=document.getElementById('waylo-messages');if(!wall)return;
 const trip=wall.dataset.tripId,before=Number(wall.dataset.beforeId||0),viewer=Number(wall.dataset.viewerId),moderator=wall.dataset.moderator==='true';
 const status=document.getElementById('chat-status');let running=false,closed=false;
 function action(item,kind,label){const form=document.createElement('form');form.method='post';form.action=`/groups/trips/${encodeURIComponent(trip)}/messages/${item.id}/${kind}`;
  const csrf=document.querySelector('.group-compose input[type=hidden]');if(csrf)form.append(csrf.cloneNode(true));
  if(kind==='edit'){const label=document.createElement('label');label.textContent='Message';const input=document.createElement('textarea');input.name='content';input.maxLength=1500;input.required=true;input.value=item.content;label.append(input);form.append(label)}
  const button=document.createElement('button');button.type='submit';button.className='community-text-button';button.textContent=label;form.append(button);return form;
 }
 async function refresh(){if(running||closed||document.hidden)return;running=true;
  try{const response=await fetch(`/groups/trips/${encodeURIComponent(trip)}/messages?beforeId=${before}`,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});
   if([401,403,404].includes(response.status)||response.redirected){closed=true;document.querySelector('.group-layout').replaceChildren();status.textContent='Group access is no longer available. Sign in again or check your booking.';document.querySelector('.community-page').append(status);return}
   if(!response.ok)throw new Error('unavailable');const rows=await response.json();const ids=new Set(rows.map(x=>String(x.id)));let changed=false;
   wall.querySelectorAll('[data-message-id]').forEach(node=>{if(!ids.has(node.dataset.messageId))node.remove()});
   for(const item of rows){if(!Number.isInteger(item.id))continue;let node=wall.querySelector(`[data-message-id="${item.id}"]`);
    if(!node){changed=true;node=document.createElement('div');node.className='group-message';node.dataset.messageId=String(item.id);const header=document.createElement('div');header.className='group-message-heading';const author=document.createElement('strong');author.textContent=item.authorName;const time=document.createElement('time');time.textContent=String(item.createdAt).replace('T',' ');header.append(author,time);node.append(header,document.createElement('p'));wall.append(node);
     if(!item.removed&&(moderator||item.authorId===viewer))node.append(action(item,'remove','Remove message'));
     if(!item.removed&&item.authorId===viewer){const details=document.createElement('details'),summary=document.createElement('summary');summary.textContent='Edit (within 15 minutes)';details.append(summary,action(item,'edit','Save edit'));node.append(details)}
    }
    const body=node.querySelector('p');if(body.textContent!==item.content){body.textContent=item.content;changed=true}body.classList.toggle('removed',item.removed);
    if(item.removed)node.querySelectorAll('form,details').forEach(x=>x.remove());
   }
   status.textContent='Up to date';if(changed&&!before)wall.scrollTop=wall.scrollHeight;
  }catch(_){status.textContent='Connection interrupted. Retrying shortly.'}finally{running=false}
 }
 wall.scrollTop=wall.scrollHeight;setInterval(refresh,8000);
});