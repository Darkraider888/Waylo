document.addEventListener('DOMContentLoaded',()=>{
 document.documentElement.classList.add('js-ready');
 const toggle=document.querySelector('.mobile-menu-toggle'),nav=document.getElementById('main-navigation');
 toggle?.addEventListener('click',()=>{const open=toggle.getAttribute('aria-expanded')!=='true';toggle.setAttribute('aria-expanded',String(open));nav?.classList.toggle('is-open',open)});
 document.addEventListener('keydown',e=>{if(e.key==='Escape'&&nav?.classList.contains('is-open')){nav.classList.remove('is-open');toggle.setAttribute('aria-expanded','false');toggle.focus()}});
 document.querySelectorAll('input[type=file]:not([data-photo-input])').forEach(input=>{
  if(!input.accept.includes('image'))return;
  const preview=document.createElement('img');preview.className='device-preview';preview.alt='Selected photo preview';preview.hidden=true;
  const status=document.createElement('p');status.className='upload-status';status.setAttribute('role','status');input.after(preview,status);let objectUrl;
  input.addEventListener('change',()=>{if(objectUrl)URL.revokeObjectURL(objectUrl);const file=input.files?.[0];preview.hidden=true;status.textContent='';if(!file)return;
   if(!['image/jpeg','image/png'].includes(file.type)||file.size>6*1024*1024){input.value='';status.textContent='Choose a JPEG or PNG photo up to 6 MB.';return}
   objectUrl=URL.createObjectURL(file);preview.src=objectUrl;preview.hidden=false;status.textContent='Ready to upload. Save the form to confirm.';
  });
 });
});