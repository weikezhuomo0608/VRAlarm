#!/usr/bin/env python3
"""Build with Android SDK 35 and JDK 17 or Eclipse ECJ; no third-party runtime libraries."""
import argparse,pathlib,subprocess,zipfile,shutil,os
import xml.etree.ElementTree as ET
# aapt2 generates R into the manifest package, and the sources say `package dev.hazel.livealarm`,
# so R stays here while the installed app id is renamed separately (see --rename-manifest-package).
NAMESPACE='dev.hazel.livealarm'
APPLICATION_ID='dev.hazel.livealarm.multi'
p=argparse.ArgumentParser()
p.add_argument('--platform',required=True);p.add_argument('--tools',required=True);p.add_argument('--ecj')
p.add_argument('--keystore',required=True);p.add_argument('--password-file',required=True);p.add_argument('--alias',default='hazelrelease');p.add_argument('--output',default='build/VRAlarm-1.1.6.apk')
p.add_argument('--version-code',default='35');p.add_argument('--version-name',default='1.1.6')
a=p.parse_args();root=pathlib.Path(__file__).resolve().parents[1];build=root/'build/manual';build.mkdir(parents=True,exist_ok=True)
for name in ['classes','gen','dex']:
    dest=build/name
    if dest.exists():shutil.rmtree(dest)
    dest.mkdir()
src=root/'app/src/main';sdk=pathlib.Path(a.tools).resolve();platform=str(pathlib.Path(a.platform).resolve())
if os.name!='nt':
    for binary in ['aapt2','zipalign']:(sdk/binary).chmod(0o755)
def run(args):
    args=[resolve_tool(x) for x in args]
    executable=pathlib.Path(str(args[0]))
    if os.name!='nt' and executable.is_absolute() and executable.is_file():executable.chmod(0o755)
    print('Running:',executable.name,flush=True);subprocess.run([str(x) for x in args],check=True,cwd=root)
def resolve_tool(value):
    """aapt2 and zipalign ship as .exe on Windows; CreateProcess will not guess the suffix,
    so an extensionless path fails with WinError 2 even though the file is right there."""
    if os.name!='nt':return value
    p=pathlib.Path(str(value))
    if p.is_absolute() and p.suffix=='' and p.with_suffix('.exe').is_file():return p.with_suffix('.exe')
    return value
run([sdk/'aapt2','compile','--dir',src/'res','-o',build/'resources.zip'])
manifest=ET.parse(src/'AndroidManifest.xml');manifest.getroot().set('package',NAMESPACE);ET.register_namespace('android','http://schemas.android.com/apk/res/android');manifest.write(build/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
run([sdk/'aapt2','link','-o',build/'resources.apk','-I',platform,'--manifest',build/'AndroidManifest.xml','--java',build/'gen','--rename-manifest-package',APPLICATION_ID,'--min-sdk-version','26','--target-sdk-version','35','--version-code',a.version_code,'--version-name',a.version_name,'-0','wav','-A',src/'assets',build/'resources.zip'])
sources=sorted((src/'java').rglob('*.java'))+sorted((build/'gen').rglob('*.java'))
cmd=['java','-jar',str(pathlib.Path(a.ecj).resolve()),'-1.8','-warn:none'] if a.ecj else ['javac','-source','8','-target','8']
run(cmd+['-encoding','UTF-8','-classpath',platform,'-d',build/'classes']+sources)
with zipfile.ZipFile(build/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
    for f in (build/'classes').rglob('*.class'):z.write(f,f.relative_to(build/'classes'))
run(['java','-cp',sdk/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',platform,'--output',build/'dex',build/'classes.jar'])
shutil.copyfile(build/'resources.apk',build/'unsigned.apk')
with zipfile.ZipFile(build/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    for f in (build/'dex').glob('*.dex'):z.write(f,f.name)
run([sdk/'zipalign','-f','-p','4',build/'unsigned.apk',build/'aligned.apk'])
out=pathlib.Path(a.output).resolve();out.parent.mkdir(parents=True,exist_ok=True)
run(['java','-jar',sdk/'lib/apksigner.jar','sign','--ks',pathlib.Path(a.keystore).resolve(),'--ks-key-alias',a.alias,'--ks-pass','file:'+str(pathlib.Path(a.password_file).resolve()),'--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',out,build/'aligned.apk'])
run(['java','-jar',sdk/'lib/apksigner.jar','verify','--verbose',out]);print('APK:',out)
