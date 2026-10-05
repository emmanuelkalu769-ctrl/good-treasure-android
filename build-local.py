from pathlib import Path
import os,json,secrets,subprocess,zipfile,hashlib,xml.etree.ElementTree as ET,copy
root=Path.cwd();source=root/'outputs/good-treasure-android';build=root/'work/android-build';build.mkdir(parents=True,exist_ok=True);temporary=build/'tmp';temporary.mkdir(exist_ok=True)
jdk=next((root/'work/android-tools/azul-jdk').glob('*/bin/java.exe')).parent
tools=root/'work/android-tools/build-tools/android-15';platform=root/'work/android-tools/platform/android-35/android.jar'
def run(label,args,env=None):
 print(label,flush=True)
 args=[str(a) for a in args]
 if Path(args[0]).name=='java.exe':args.insert(1,'-Djava.io.tmpdir='+str(temporary))
 if Path(args[0]).name in ('javac.exe','keytool.exe'):args.insert(1,'-J-Djava.io.tmpdir='+str(temporary))
 result=subprocess.run(args,capture_output=True,text=True,encoding='utf8',errors='replace',env=env)
 if result.returncode:
  print(result.stdout);print(result.stderr);raise RuntimeError(label+' failed')
 if result.stdout.strip():print(result.stdout.strip(),flush=True)
 return result.stdout+result.stderr
resources=build/'resources.zip';unsigned=build/'unsigned.apk';aligned=build/'aligned.apk'
run('Compile Android resources',[tools/'aapt2.exe','compile','--dir',source/'res','-o',resources])
generated=build/'generated';generated.mkdir(exist_ok=True)
entries=json.loads((root/'work/android-maven/dependencies.json').read_text(encoding='utf8'))
android='http://schemas.android.com/apk/res/android';ET.register_namespace('android',android)
manifestTree=ET.parse(source/'AndroidManifest.xml');app=manifestTree.getroot().find('application');packages=[];overlays=[];dependencyJars=[]
for entry in entries:
 jarPath=root/entry['jar']
 if jarPath.exists() and jarPath.suffix=='.jar':dependencyJars.append(jarPath)
 if not entry['aarDir']:continue
 directory=root/entry['aarDir'];libManifest=directory/'AndroidManifest.xml'
 if libManifest.exists():
  tree=ET.parse(libManifest);package=tree.getroot().get('package')
  if package:packages.append(package)
  libApp=tree.getroot().find('application')
  if libApp is not None:
   for node in libApp:
    cloned=copy.deepcopy(node)
    for child in cloned.iter():
     for attr in list(child.attrib):
      if attr.startswith('{http://schemas.android.com/tools}'):del child.attrib[attr]
    app.append(cloned)
 resourceDir=directory/'res'
 if resourceDir.exists() and any(resourceDir.rglob('*')):
  archive=build/(entry['artifact']+'-resources.zip')
  run('Compile '+entry['artifact']+' resources',[tools/'aapt2.exe','compile','--dir',resourceDir,'-o',archive]);overlays.append(archive)
mergedManifest=build/'AndroidManifest.xml';manifestTree.write(mergedManifest,encoding='utf-8',xml_declaration=True)
linkArgs=[tools/'aapt2.exe','link','-o',unsigned,'-I',platform,'--manifest',mergedManifest,'--java',generated,'--auto-add-overlay','--extra-packages',':'.join(dict.fromkeys(packages)),'-A',source/'assets']
for archive in overlays:linkArgs.extend(['-R',archive])
linkArgs.extend(['-R',resources]);run('Link Android package',linkArgs)
classes=build/'classes';classes.mkdir(exist_ok=True)
sources=list((source/'src').rglob('*.java'))+list(generated.rglob('*.java'))
run('Compile Android application',[jdk/'javac.exe','--release','8','-encoding','UTF-8','-cp',os.pathsep.join(str(j) for j in [platform,*dependencyJars]),'-d',classes,*sources])
jar=build/'classes.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as archive:
 for file in classes.rglob('*.class'):archive.write(file,file.relative_to(classes).as_posix())
dex=build/'dex';dex.mkdir(exist_ok=True)
run('Compile Android executable',[jdk/'java.exe','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','24','--lib',platform,'--output',dex,jar,*dependencyJars])
with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as archive:
 for file in dex.glob('*.dex'):archive.write(file,file.name)
 services={}
 for dependency in dependencyJars:
  with zipfile.ZipFile(dependency) as lib:
   for name in lib.namelist():
    if name.startswith('META-INF/services/') and not name.endswith('/'):
     services.setdefault(name,set()).update(lib.read(name).decode('utf8').splitlines())
 for name,values in services.items():archive.writestr(name,'\n'.join(sorted(values))+'\n')
run('Align Android package',[tools/'zipalign.exe','-f','-p','4',unsigned,aligned])
private=root/'work/android-signing';private.mkdir(exist_ok=True);credentials=private/'signing.json'
if not credentials.exists():credentials.write_text(json.dumps({'password':secrets.token_urlsafe(32),'alias':'goodtreasure'}),encoding='utf8')
settings=json.loads(credentials.read_text(encoding='utf8'));env=os.environ.copy();env['GT_APK_SIGNING_PASSWORD']=settings['password'];keystore=private/'good-treasure.p12'
if not keystore.exists():run('Create private app signing key',[jdk/'keytool.exe','-genkeypair','-storetype','PKCS12','-keystore',keystore,'-alias',settings['alias'],'-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Good Treasure House of Fashion, O=Good Treasure House of Fashion, C=NG','-storepass:env','GT_APK_SIGNING_PASSWORD','-keypass:env','GT_APK_SIGNING_PASSWORD'],env)
output=root/'outputs/Good-Treasure-Native-Android.apk'
run('Sign installable APK',[jdk/'java.exe','-jar',tools/'lib/apksigner.jar','sign','--ks',keystore,'--ks-key-alias',settings['alias'],'--ks-pass','env:GT_APK_SIGNING_PASSWORD','--key-pass','env:GT_APK_SIGNING_PASSWORD','--out',output,aligned],env)
verification=run('Verify APK signature',[jdk/'java.exe','-jar',tools/'lib/apksigner.jar','verify','--verbose','--print-certs',output])
(private/'public-certificate.txt').write_text(verification,encoding='utf8')
manifest=run('Verify app name, Android support and launch icon',[tools/'aapt.exe','dump','badging',output])
assert "package: name='com.goodtreasure.fashion'" in manifest
assert "launchable-activity: name='com.goodtreasure.fashion.MainActivity'" in manifest
assert "application-label:'Good Treasure'" in manifest
with zipfile.ZipFile(output) as archive:
 assert archive.testzip() is None and 'classes.dex' in archive.namelist() and 'AndroidManifest.xml' in archive.namelist()
 assert not any('.env' in name or name.endswith(('.p12','.jks')) for name in archive.namelist())
sha=hashlib.sha256(output.read_bytes()).hexdigest();(root/'outputs/Good-Treasure-Native-Android.apk.sha256').write_text(sha+'  Good-Treasure-Native-Android.apk\n',encoding='utf8')
print('APK READY:',output.name,'('+str(output.stat().st_size)+' bytes)',flush=True)

