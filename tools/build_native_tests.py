#!/usr/bin/env python3
"""Build disposable-device instrumentation with the same signing key as the app."""
import argparse, pathlib, subprocess, zipfile
p=argparse.ArgumentParser()
for name in ['platform','tools','ecj','keystore','password-file','output']:p.add_argument('--'+name,required=True)
a=p.parse_args();root=pathlib.Path(__file__).resolve().parents[1]
b=root/'build/native-tests';b.mkdir(parents=True,exist_ok=True)
sdk=pathlib.Path(a.tools).resolve();platform=pathlib.Path(a.platform).resolve()
def run(cmd):subprocess.run([str(x) for x in cmd],check=True)
for d in ['classes','dex']:(b/d).mkdir(exist_ok=True)
run([sdk/'aapt2','link','-o',b/'resources.apk','-I',platform,'--manifest',root/'tests/native/AndroidManifest.xml','--version-code','1','--version-name','1'])
run(['java','-jar',pathlib.Path(a.ecj).resolve(),'-1.8','-warn:none','-encoding','UTF-8','-cp',str(platform)+':'+str(root/'build/manual/classes'),'-d',b/'classes',root/'tests/native/NativeTests.java'])
with zipfile.ZipFile(b/'tests.jar','w') as z:
 for f in (b/'classes').rglob('*.class'):z.write(f,f.relative_to(b/'classes'))
run(['java','-cp',sdk/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',platform,'--classpath',root/'build/manual/classes.jar','--output',b/'dex',b/'tests.jar'])
with zipfile.ZipFile(b/'resources.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in (b/'dex').glob('*.dex'):z.write(f,f.name)
run([sdk/'zipalign','-f','4',b/'resources.apk',b/'aligned.apk'])
run(['java','-jar',sdk/'lib/apksigner.jar','sign','--ks',pathlib.Path(a.keystore).resolve(),'--ks-key-alias','hazelrelease','--ks-pass','file:'+str(pathlib.Path(a.password_file).resolve()),'--v4-signing-enabled','false','--out',pathlib.Path(a.output).resolve(),b/'aligned.apk'])
print('Native test APK built; it is for an emulator only and is not a user deliverable.')
