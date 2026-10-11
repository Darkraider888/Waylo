document.querySelectorAll('.destination-stage').forEach(stage => {
 const labels = {sreemangal:'Sreemangal',chattogram:'Chattogram','coxs-bazar':'Cox’s Bazar'};
 stage.querySelectorAll('[data-place]').forEach(button => button.addEventListener('click', () => {
  stage.dataset.current = button.dataset.place;
  stage.querySelectorAll('[data-place]').forEach(card => card.setAttribute('aria-pressed',String(card === button)));
  stage.querySelector('.destination-label').textContent = labels[button.dataset.place] + ' · Bangladesh';
 }));
});
