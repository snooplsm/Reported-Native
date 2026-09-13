async function exportMotion() {
 const button=document.getElementById('export-motion'); button.textContent='Exporting…';
 document.body.classList.add('animated'); document.body.classList.remove('paused');
 const durations=[8,18,8,6,3.2,12], result=[];
 for (const [index,svg] of [...document.querySelectorAll('.art svg')].entries()) {
  const animations=svg.getAnimations({subtree:true});
  animations.forEach(a=>a.pause());
  const targets=[...svg.querySelectorAll('*')].filter(e=>getComputedStyle(e).animationName!=='none');
  targets.forEach((e,i)=>{if(!e.id)e.id='motion-'+index+'-'+i});
  const tracks=targets.map(e=>({id:e.id,frames:[]}));
  for(let frame=0;frame<=Math.round(durations[index]*30);frame++){
   animations.forEach(a=>a.currentTime=frame/30*1000);
   targets.forEach((e,i)=>{
    const world=e.getCTM(),parent=e.parentNode.getCTM(); const m=parent.inverse().multiply(world),s=getComputedStyle(e);
    tracks[i].frames.push({m:[m.a,m.b,m.c,m.d,m.e,m.f],o:parseFloat(s.opacity),fill:s.fill});
   });
  }
  result.push({name:svg.closest('article').className.split(' ')[1],duration:durations[index],svg:svg.outerHTML,tracks});
  animations.forEach(a=>{a.currentTime=0;a.play()});
 }
 const response=await fetch('/save-motion',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(result)});
 button.textContent=response.ok?'Motion exported':'Export failed';
}
