from pathlib import Path
import urllib.request,xml.etree.ElementTree as E,json,zipfile,re,concurrent.futures
base=Path('work/android-maven');base.mkdir(exist_ok=True)
repos=['https://dl.google.com/dl/android/maven2/','https://repo.maven.apache.org/maven2/']
ns={'m':'http://maven.apache.org/POM/4.0.0'};selected={};poms={}
def fetch(g,a,v,ext):
 name=a+'-'+v+'.'+ext;target=base/(g+'-'+name)
 if target.exists():return target
 path=g.replace('.','/')+'/'+a+'/'+v+'/'+name
 for repo in repos:
  try:
   with urllib.request.urlopen(repo+path,timeout=30) as r:target.write_bytes(r.read())
   return target
  except Exception:pass
 raise RuntimeError('Missing '+path)
def version(v):
 if v.startswith('['):return v.strip('[]()').split(',')[0]
 return v
def rank(v):
 main,_,suffix=v.partition('-');return tuple(int(x) for x in main.split('.'))+(1 if not suffix else 0,suffix)
queue=[('androidx.credentials','credentials','1.3.0'),('androidx.credentials','credentials-play-services-auth','1.3.0'),('com.google.android.libraries.identity.googleid','googleid','1.1.1')]
while queue:
 g,a,v=queue.pop(0);v=version(v);key=(g,a)
 if key in selected and rank(selected[key])>=rank(v):continue
 selected[key]=v;tree=E.parse(fetch(g,a,v,'pom'));poms[(g,a,v)]=tree
 props={'project.version':v}
 for node in tree.findall('m:properties/*',ns):props[node.tag.split('}')[-1]]=node.text
 for d in tree.findall('m:dependencies/m:dependency',ns):
  def t(k):return d.findtext('m:'+k,'',ns)
  if t('scope') in ('test','provided') or t('optional')=='true':continue
  dv=t('version')
  for k,val in props.items():dv=dv.replace('${'+k+'}',val)
  if not dv or '${' in dv:raise RuntimeError('Unresolved '+g+':'+a+' -> '+t('artifactId')+':'+dv)
  queue.append((t('groupId'),t('artifactId'),dv))
print('Resolved',len(selected),'dependencies',flush=True)
def download(item):
 (g,a),v=item;tree=poms[(g,a,v)];pack=tree.findtext('m:packaging','jar',ns);p=fetch(g,a,v,pack)
 if pack=='aar':
  dest=base/(g+'-'+a+'-'+v);dest.mkdir(exist_ok=True)
  with zipfile.ZipFile(p) as z:z.extractall(dest)
  jar=dest/'classes.jar'
 else:dest=None;jar=p
 return {'group':g,'artifact':a,'version':v,'jar':str(jar),'aarDir':str(dest) if dest else None}
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:entries=list(pool.map(download,selected.items()))
(base/'dependencies.json').write_text(json.dumps(entries,indent=2));print('Downloaded official native Google Credential Manager dependencies.',flush=True)
