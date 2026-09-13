import json,io,math,re,gzip
import xml.etree.ElementTree as ET
from pathlib import Path
from lottie.importers.svg import import_svg
ROOT=Path(__file__).parent

def walk(d):
 if isinstance(d,dict):
  yield d
  for v in d.values(): yield from walk(v)
 elif isinstance(d,list):
  for v in d: yield from walk(v)

def prop(values):
 if all(v==values[0] for v in values): return {'a':0,'k':values[0]}
 # Simplify sampled tracks while preserving turns, holds, and visibility changes.
 def vector(v):return v if isinstance(v,list) else [v]
 jumps={t for t in range(1,len(values)) if max(abs(a-b) for a,b in zip(vector(values[t]),vector(values[t-1])))>80}
 keep={0,len(values)-1}|jumps|{t-1 for t in jumps}
 def simplify(lo,hi):
  if hi-lo<2:return
  a=vector(values[lo]);b=vector(values[hi]);worst=0;at=lo
  for t in range(lo+1,hi):
   f=(t-lo)/(hi-lo)
   err=max(abs(v-(x+(y-x)*f)) for v,x,y in zip(vector(values[t]),a,b))
   if err>worst:worst=err;at=t
  if worst>.03:
   keep.add(at);simplify(lo,at);simplify(at,hi)
 boundaries=sorted(keep)
 for lo,hi in zip(boundaries,boundaries[1:]):simplify(lo,hi)
 keys=[{'t':t,'s':vector(values[t]),'i':{'x':[1],'y':[1]},'o':{'x':[0],'y':[0]}} for t in sorted(keep)]
 for key in keys:
  if key['t']+1 in jumps:key['h']=1
 return {'a':1,'k':keys}

def square_canvas(root, scene_name):
 root.set('viewBox','0 -40 320 320')
 root.set('width','320');root.set('height','320')
 # Extend the original geometry, avoiding abutting strips and antialiasing seams.
 replacements={
  'M52 0v240M268 0v240':'M52-40v320M268-40v320',
  'M160 0v240':'M160-40v320',
  'M55 0h64v240H55z':'M55-40h64v320H55z',
  'M120 0v240':'M120-40v320',
  'M156 0v240M164 0v240':'M156-40v320M164-40v320',
  'M156 0v80m0 82v78M164 0v80m0 82v78':'M156-40v120m0 82v118M164-40v120m0 82v118',
  'M100 0h120v60h100v80H220v100H100V140H0V60h100Z':'M100-40h120v100h100v80H220v140H100V140H0V60h100Z',
  'M98 0v45q0 13-13 13H0M222 0v45q0 13 13 13h85M0 142h85q13 0 13 13v85M320 142h-85q-13 0-13 13v85':'M98-40v85q0 13-13 13H0M222-40v85q0 13 13 13h85M0 142h85q13 0 13 13v125M320 142h-85q-13 0-13 13v125',
  'M160 0v26m0 167v47M0 100h65m190 0h65':'M160-40v66m0 167v87M0 100h65m190 0h65',
  'M168 0h152v240H168z':'M168-40h152v320H168z',
  'M162 0h16v240h-16z':'M162-40h16v320h-16z',
  'M170 0h8v240h-8z':'M170-40h8v320h-8z',
  'M178 58h142M178 118h142M178 178h142M251 0v240':'M178 58h142M178 118h142M178 178h142M251-40v320'
 }
 for e in root.iter():
  if e.tag.endswith('rect') and e.get('height') in ['240','260'] and float(e.get('width','0'))>=100:
   e.set('y','-40');e.set('height','320')
  if e.get('d') in replacements:e.set('d',replacements[e.get('d')])

 # Match the asphalt silhouette to rounded sidewalk corners, then give the curb thickness.
 if scene_name in ('ran-red-light','ran-stop-sign'):
  for e in root.iter():
   if e.get('d')=='M100-40h120v100h100v80H220v140H100V140H0V60h100Z':
    e.set('d','M98-40H222V40Q222 58 240 58H320V142H240Q222 142 222 160V280H98V160Q98 142 80 142H0V58H80Q98 58 98 40Z')
   if e.get('id','').endswith('corner-curbs'):
    e.clear()
    e.set('id',scene_name+'-corner-curbs')
    edge='M98-40V40Q98 58 80 58H0M222-40V40Q222 58 240 58H320M0 142H80Q98 142 98 160V280M320 142H240Q222 142 222 160V280'
    ET.SubElement(e,'{http://www.w3.org/2000/svg}path',{'d':edge,'fill':'none','stroke':'#b8c9cb','stroke-width':'7','stroke-linejoin':'round'})
    ET.SubElement(e,'{http://www.w3.org/2000/svg}path',{'d':edge,'fill':'none','stroke':'#f9faf5','stroke-width':'2','stroke-linejoin':'round'})


def convert(scene):
 root=ET.fromstring(scene['svg'])
 for element in root.iter():
  if element.get('id','').endswith('no-parking-sign'):
   element.set('transform','translate(260 0) '+element.get('transform',''))
 for parent in root.iter():
  for child in list(parent):
   if child.get('id','').endswith(('blocked-walking-route','-hydrant')) or (scene['name']=='blocked-crosswalk' and child.get('d')=='M60 170h200'): parent.remove(child)
 if scene['name'] in ('ran-red-light','ran-stop-sign'):
  for element in root.iter():
   if element.get('id','').endswith(('-signal','-stop-sign')):
    element.set('transform','translate(-205 0) '+element.get('transform',''))
 if scene['name']=='drove-recklessly':
  import ast
  module=ast.parse((ROOT/'create.py').read_text())
  function=next(n for n in module.body if isinstance(n,ast.FunctionDef) and n.name=='cyclist')
  source=ast.literal_eval(function.body[0].value)
  cyclist=ET.fromstring(source)
  cyclist.set('id','opposing-cyclist')
  cyclist.set('transform','translate(76 18) rotate(180)')
  root.append(cyclist)
 square_canvas(root,scene['name'])
 for parent in root.iter():
  for child in list(parent):
   if child.tag.endswith('style'):parent.remove(child)
 animated_ids={track['id'] for track in scene['tracks']}
 for el in root.iter():
  el.attrib.pop('style',None)
  if el.get('id') in animated_ids:el.set('opacity','1')
  if el.tag.split('}')[-1] in ['circle','ellipse']:
   el.set('cx',el.get('cx','0'));el.set('cy',el.get('cy','0'))
 anim=import_svg(io.StringIO(ET.tostring(root,encoding='unicode')),n_frames=round(scene['duration']*30),framerate=30).to_dict()
 named={d.get('nm'):d for d in walk(anim) if 'nm'in d}
 for track in scene['tracks']:
  name=track['id'];node=named.get(name)
  if node is None:raise ValueError('Missing '+name)
  frames=track['frames'][:-1]+[track['frames'][-2]];positions=[];rotations=[];scales=[]
  for f in frames:
   a,b,c,d,e,g=f['m'];sx=math.hypot(a,b);sy=(a*d-b*c)/sx
   if any(part in name for part in ['cyclist-detour','close-pass-car','reckless-motion','parking-detour-car']):
    if g>220:g+=g-220
    elif g<0:g*=2
   elif name.endswith('vehicle-model-y') and scene['name'] in ['ran-red-light','ran-stop-sign']:
    if g>180:g+=g-180
    elif g< -180:g+=g+180
   positions.append([round(e,5),round(g,5)])
   angle=math.degrees(math.atan2(b,a))
   if rotations:
    while angle-rotations[-1]>180:angle-=360
    while angle-rotations[-1]<-180:angle+=360
   rotations.append(round(angle,5));scales.append([round(sx*100,5),round(sy*100,5)])
  transform=next((x for x in node.get('it',[]) if x.get('ty')=='tr'),None)
  if transform is None:raise ValueError('No transform '+name+str(node)[:100])
  transform.update({'a':{'a':0,'k':[0,0]},'p':prop(positions),'r':prop(rotations),'s':prop(scales),'o':prop([round(f['o']*100,4) for f in frames])})
  if len(set(f['fill'] for f in frames))>1:
   fills=[x for x in node.get('it',[]) if x.get('ty')=='fl']
   for fill in fills:
    colors=[[int(n)/255 for n in re.findall(r'\d+',f['fill'])[:3]] for f in frames]
    fill['c']=prop(colors)
 if scene['name']=='drove-recklessly':
  cyclist=next(n for n in walk(anim) if n.get('nm')=='opposing-cyclist')
  tr=next(n for n in cyclist['it'] if n.get('ty')=='tr')
  positions=[]
  for frame in range(int(anim['op'])+1):
   phase=frame/anim['op']
   if phase<.30:
    u=phase/.30; y=-130+148*(1-(1-u)**2)
   elif phase<.65:y=18
   else:
    u=(phase-.65)/.35; y=18+390*u*u
   positions.append([76,y])
  tr['p']=prop(positions)
  tr['r']={'a':0,'k':180}
 if scene['name']=='parked-illegally':
  animate_sidewalk_arrival(anim)
 anim['nm']=scene['name'];anim['v']='5.12.2'
 return anim

def animate_sidewalk_arrival(anim):
 # Follow a curved driving path; heading comes from its tangent, never a sideways slide.
 vehicle='parked-illegally-sidewalk-vehicle-model-y'
 parent=next(n for n in walk(anim) if any(c.get('nm')==vehicle for c in n.get('it',[]) if isinstance(c,dict)))
 transform=next(c for c in parent['it'] if c.get('ty')=='tr')
 positions=[];rotations=[]
 for frame in range(int(anim['op'])+1):
  u=max(0,min(1,(frame-178)/76)); t=u*u*(3-2*u)
  points=[(115,365),(115,245),(220,220),(199,100)]
  x=sum(w*p[0] for w,p in zip([(1-t)**3,3*(1-t)**2*t,3*(1-t)*t*t,t**3],points))
  y=sum(w*p[1] for w,p in zip([(1-t)**3,3*(1-t)**2*t,3*(1-t)*t*t,t**3],points))
  dx=3*(1-t)**2*(points[1][0]-points[0][0])+6*(1-t)*t*(points[2][0]-points[1][0])+3*t*t*(points[3][0]-points[2][0])
  dy=3*(1-t)**2*(points[1][1]-points[0][1])+6*(1-t)*t*(points[2][1]-points[1][1])+3*t*t*(points[3][1]-points[2][1])
  positions.append([x,y]);rotations.append(math.degrees(math.atan2(dx,-dy)))
 transform['p']=prop(positions);transform['r']=prop(rotations)
 named={n.get('nm'):n for n in walk(anim)}
 pedestrian=named.get('parked-illegally-sidewalk-pedestrian')
 if pedestrian:
  tr=next(c for c in pedestrian['it'] if c.get('ty')=='tr')
  retreat=[]
  for f in range(int(anim['op'])+1):
   u=max(0,min(1,(f-207)/23)); u=u*u*(3-2*u)
   retreat.append([36*u,0])
  tr['p']=prop(retreat)
  tr['r']={'a':0,'k':-90}

 # Keep the open-door variant closed while moving, then reveal the open door after parking.
 for n in walk(parent):
  if n.get('nm')=='open-passenger-door':
   tr=next(c for c in n['it'] if c.get('ty')=='tr')
   tr['o']=prop([0 if f<254 else 100 for f in range(int(anim['op'])+1)])

if __name__=='__main__':
 capture=ROOT/'motion-capture.json'
 scenes=json.loads(capture.read_text() if capture.exists() else gzip.decompress((ROOT/'motion-capture.json.gz').read_bytes()))
 out=ROOT/'lottie';out.mkdir(exist_ok=True)
 for scene in scenes:
  anim=convert(scene)
  (out/(scene['name']+'.json')).write_text(json.dumps(anim,separators=(',',':')))
  print(scene['name'],len(json.dumps(anim)))
