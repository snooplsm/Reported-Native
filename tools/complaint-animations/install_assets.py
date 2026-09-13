from pathlib import Path
import json
from palettes import LIGHT, DARK, recolor
root=Path(__file__).resolve().parents[2]
work=Path(__file__).parent
red=json.loads((work/'lottie/ran-red-light.json').read_text());stop=json.loads((work/'lottie/ran-stop-sign.json').read_text())
identity={'o':{'a':0,'k':100},'r':{'a':0,'k':0},'p':{'a':0,'k':[0,0,0]},'a':{'a':0,'k':[0,0,0]},'s':{'a':0,'k':[100,100,100]}}
combined={'v':'5.12.2','fr':30,'ip':0,'op':420,'w':320,'h':320,'nm':'Red light and stop sign','ddd':0,'assets':[{'id':'red','layers':red['layers']},{'id':'stop','layers':stop['layers']}],'layers':[{'ty':0,'ind':1,'nm':'Red light','refId':'red','ks':identity,'w':320,'h':320,'ip':0,'op':240,'st':0,'sr':1},{'ty':0,'ind':2,'nm':'Stop sign','refId':'stop','ks':identity,'w':320,'h':320,'ip':240,'op':420,'st':240,'sr':1}]}
(work/'lottie/ranredlight.json').write_text(json.dumps(combined,separators=(',',':')))
mapping={'blocked-bike-lane':'bikelane','blocked-crosswalk':'crosswalk','ranredlight':'ranredlight','drove-recklessly':'reckless','parked-illegally':'parkedillegally'}
# Keep one category tile while cycling through its approved variations.
from create_variants import build as build_variants
build_variants()

def sequence(names, title):
 result={'v':'5.12.2','fr':30,'ip':0,'op':0,'w':320,'h':320,'nm':title,'ddd':0,'assets':[],'layers':[]}
 for index,name in enumerate(names):
  scene=json.loads((work/'lottie'/f'{name}.json').read_text())
  assert not scene.get('assets'), 'Sequence inputs must contain self-contained vector layers'
  start=result['op']; end=start+scene['op']
  ref=f'scene-{index}'
  result['assets'].append({'id':ref,'layers':scene['layers']})
  result['layers'].append({'ty':0,'ind':index+1,'nm':name,'refId':ref,'ks':identity,'w':320,'h':320,'ip':start,'op':end,'st':start,'sr':1})
  result['op']=end
 return result

sequences={
 'bikelane':sequence(['blocked-bike-lane','blocked-bike-lane-door','blocked-bike-lane-prime'],'Blocked bike lane variations'),
 'parkedillegally':sequence(['parked-illegally','parked-illegally-door'],'Illegal parking variations'),
}
for src,dest in mapping.items():
 animation=sequences.get(dest) or json.loads((work/'lottie'/f'{src}.json').read_text())
 for suffix,palette in [('',LIGHT),('_dark',DARK)]:
  variant=recolor(animation,palette)
  data=json.dumps(variant,separators=(',',':'))
  for folder in [root/'assets/complaints',root/'native/iosApp/ReportediOS/Resources/complaints']:
   (folder/f'{dest}{suffix}.json').write_text(data)
# Separate scenes remain available in the review gallery.
for file in list((work/'lottie').glob('*.json')):
 if file.stem.endswith(('_light','_dark')):continue
 animation=json.loads(file.read_text())
 for suffix,palette in [('_light',LIGHT),('_dark',DARK)]:
  (file.parent/f'{file.stem}{suffix}.json').write_text(json.dumps(recolor(animation,palette),separators=(',',':')))
