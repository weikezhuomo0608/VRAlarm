#!/usr/bin/env python3
"""Build with Android SDK 35 and JDK 17 or Eclipse ECJ; no third-party runtime libraries.

Two lines are built from this one source tree:

    1.3.0  everything (--line 1.3.0, the default)
    1.2.0  the same app without the 舰长红包 feature (--line 1.2.0)

The 1.2.0 line does not keep a second copy of the sources. The build stages its own copy of
app/src/main plus ui-src/app.js and takes the feature out of the copy: its five classes are
deleted and every seam that called them is cut by an exact anchor. Each edit is asserted, so a
later change to those files fails this build loudly rather than shipping half a feature. The
workspace is never touched.
"""
import argparse,pathlib,subprocess,zipfile,shutil,os
import xml.etree.ElementTree as ET
# aapt2 generates R into the manifest package, and the sources say `package dev.hazel.livealarm`,
# so R stays here while the installed app id is renamed separately (see --rename-manifest-package).
NAMESPACE='dev.hazel.livealarm'
APPLICATION_ID='dev.hazel.livealarm.multi'

JAVA='java/dev/hazel/livealarm/'
# The feature's own classes. Nothing else in the app may reference them after the cuts below.
PACKET_FILES=['Packets.java','Wbi.java','DanmakuFrames.java','DanmakuClient.java','BiliAuth.java']
# (path, anchor that opens the cut, anchor that closes it — the closing anchor stays)
PACKET_RANGES=[
 (JAVA+'GuardianService.java','    /**\n     * The red-packet notice has a channel of its own','    public static final int WATCH_ID=101'),
 (JAVA+'GuardianService.java','    /** Its own id, outside the alarm range 201–207','    public static volatile boolean running=false'),
 (JAVA+'GuardianService.java','    /** One danmaku connection per room that asked','    private boolean busy=false'),
 (JAVA+'GuardianService.java','    /**\n     * The rooms that asked for 舰长红包 reminders','    /** A notification button that opens the room.'),
 (JAVA+'GuardianService.java','                // Same network phase, same wake lock','                int spent='),
 (JAVA+'GuardianService.java','        // The red-packet connections live inside the schedule too','        AlarmScheduler.cancel(this,AlarmScheduler.KEEPALIVE);'),
 (JAVA+'GuardianService.java','        NotificationChannel packet=new NotificationChannel(PACKET_CHANNEL','    }\n    @Override public void onCreate(){'),
 (JAVA+'MainActivity.java','            case "probeDanmaku":{','            case "saveAnchor":{'),
 (JAVA+'BiliApi.java','    /** The room id behind an alias: the danmaku service authenticates the canonical one. */','    /** Bounded fetch of the avatar, so one oversized response cannot fill the device. */'),
 (JAVA+'Prefs.java','    /**\n     * The packet ids already announced for one anchor','    // ---- per-anchor stream history for the statistics shown on the anchor cards ----'),
]
# (path, text to find, replacement, how many times it must appear)
PACKET_LITERALS=[
 (JAVA+'GuardianService.java','handler.removeCallbacksAndMessages(null);stopWatches();','handler.removeCallbacksAndMessages(null);',1),
 # The field stays (the two lines share one storage layout, and the 1.3.0 line still writes it),
 # but in this line nothing reads it — so its documentation must not describe a feature that is
 # not here. Anchors, Prefs and MainActivity keep reading and writing it as plain storage.
 (JAVA+'Anchors.java','''        /**
         * 舰长红包 watch for this room: opt-in, off by default. A 舰长红包 is a 0.1-折 boarding
         * slot anybody may grab, so any room the user actually visits is worth watching. It never
         * changes whether the stream itself rings.
         */''','''        /**
         * Stored per-room flag kept for compatibility: the 1.3.0 line uses it for its red-packet
         * watch, which this line does not have. Nothing here reads it — it is carried through
         * saves and backups untouched so a room's setting survives moving between the two lines.
         */''',1),
]
# The UI source is stripped in its hand-written form (single quotes) and then bundled, so the
# packaged asset carries no trace of the entry, the card tag, the panel or the probe button.
UI_RANGES=[
 ('<button class="quick" data-action="packetPicker"','<button class="quick" data-action="kaoziPicker"'),
 ('/** The per-room switches for 舰长红包, the one setting that used to live in the anchor editor. */','function editAnchor(id){'),
 ('<div class="section-label"><h2>舰长红包提醒</h2>','<div class="section-label"><h2>主题与外观</h2>'),
 ("    if(action==='probeDanmaku'){","    if(action==='addScheduleEntry'){"),
]
UI_LITERALS=[
 ('    // 舰长红包 and 考子闹钟 both live up here now: one entry each, one panel each, a switch per room.\n','',1),
 (",packet:'M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5'",'',1),
 ("${a.packet?'<span class=\"rule-tag packet\">舰包</span>':''}",'',1),
 ("${packetPickerOpen?packetPicker():''}",'',1),
 ('let packetPickerOpen=false;\n','',1),
 ("    if(action==='packetPicker'){packetPickerOpen=!packetPickerOpen;kaoziPickerOpen=false;render();return;}\n",'',1),
 ("if(action==='kaoziPicker'){kaoziPickerOpen=!kaoziPickerOpen;packetPickerOpen=false;render();return;}","if(action==='kaoziPicker'){kaoziPickerOpen=!kaoziPickerOpen;render();return;}",1),
 ("else if(key.startsWith('packet:'))await togglePacket(key.slice(7));",'',1),
 ("async function togglePacket(id){await putFlag(id,'packet');}\n",'',1),
 ('packet:existing.packet===true,','',1),
 ('packet:false,','',1),
 ('packet:anchorDraft.packet===true,','',1),
 ('packet:a.packet===true,','',1),
 ('const packetOn=list.filter(a=>a.packet===true).length, kaoziOn=list.filter(a=>a.allDay===true).length;','const kaoziOn=list.filter(a=>a.allDay===true).length;',1),
 ('；<strong>舰长红包提醒</strong>在旁边的「舰长红包」入口里——都在看得见的地方，这里只留说明和下面的试连工具。','——在看得见的地方，这里只留说明。',1),
 ('<button class="secondary" data-action="probeDanmaku" style="margin-top:10px">${icon(\'spark\')}试连一次弹幕</button><div id="packet-probe"></div>','',1),
 ('<p class="hint">试连工具：点它会连上该房间的弹幕听 <strong>20 秒</strong>，把连接过程和收到的命令原样列出来（在那之前会先取一次弹幕服务器地址，失败的步骤也会写出来）。红包只是提醒，抢的动作由你自己来。</p>','',1),
 ('页面顶部的<strong>「舰长红包」</strong>与<strong>「考子」</strong>是逐房间开关：前者盯红包，后者让这位主播全天守候、不受时段限制（金色卡片的就是它）。','页面顶部的<strong>「考子」</strong>是逐房间开关：打开后这位主播全天守候、不受时段限制（金色卡片的就是它）。',1),
]
# style.css has no source outside the assets, so the staged copy is edited in place. The red tag
# and its two variables exist only for the feature; leaving them behind would keep the name in a
# packaged file for no reason.
CSS_LITERALS=[
 ('''/* 舰包 = 舰长红包（0.1 折上舰红包）。Red because that is what it is, and named 舰包 so the two
   characters fit the tag row the way 高频 does. */
.rule-tag.packet{background:var(--red-bg);color:var(--red)}
''','',1),
 ('--red:#bb4130;--red-bg:#fbe8e4;','',1),
 ('--red:#e79a8d;--red-bg:#4a2a25;','',1),
]
# Anything from the feature that must not survive in a 1.2.0 build. Checked after the edits.
PACKET_FORBIDDEN_JAVA=['PACKET_CHANNEL','PACKET_ID','Packets.','Danmaku','Wbi.','BiliAuth','舰长红包','POPULARITY_RED_POCKET']
# 'packetOn' is in here because a half-applied replacement once left `const packetOn=kaoziOn=…`,
# which throws under 'use strict' and took the whole anchors page down without a syntax error.
PACKET_FORBIDDEN_UI=['舰长红包','舰包','packet-picker','probeDanmaku','packet-probe','togglePacket','packetOn','POPULARITY_RED_POCKET']

def find_once(text,needle,where):
    at=text.find(needle)
    if at<0:raise SystemExit('1.2.0 线：找不到锚点（源码改过了？）：'+where+' -> '+needle[:60])
    if text.find(needle,at+1)>=0:raise SystemExit('1.2.0 线：锚点出现多次，无法定位：'+where+' -> '+needle[:60])
    return at

def cut(path,start,end):
    text=path.read_text(encoding='utf-8')
    i=find_once(text,start,path.name)
    j=find_once(text,end,path.name)
    if j<=i:raise SystemExit('1.2.0 线：锚点顺序不对，'+path.name+' -> '+start[:40])
    path.write_text(text[:i]+text[j:],encoding='utf-8',newline='\n')

def swap(path,old,new,count):
    text=path.read_text(encoding='utf-8')
    seen=text.count(old)
    if seen!=count:raise SystemExit('1.2.0 线：要替换的文本出现 %d 次（应为 %d 次）：%s -> %s'%(seen,count,path.name,old[:60]))
    path.write_text(text.replace(old,new),encoding='utf-8',newline='\n')

def stage_without_packets(root,dest):
    """Copy app/src/main and ui-src/app.js, take the 舰长红包 feature out, and return the copy."""
    src=root/'app/src/main'
    if dest.exists():shutil.rmtree(dest)
    shutil.copytree(src,dest)
    for name in PACKET_FILES:
        target=dest/JAVA/name
        if not target.exists():raise SystemExit('1.2.0 线：要删的类不在了：'+name)
        target.unlink()
    for rel,start,end in PACKET_RANGES:cut(dest/rel,start,end)
    for rel,old,new,count in PACKET_LITERALS:swap(dest/rel,old,new,count)
    staged_ui=dest.parent/'line-1.2.0-ui.js'
    shutil.copyfile(root/'ui-src/app.js',staged_ui)
    for start,end in UI_RANGES:cut(staged_ui,start,end)
    for old,new,count in UI_LITERALS:swap(staged_ui,old,new,count)
    subprocess.run(['node','tools/build_ui.cjs',str(staged_ui),str(dest/'assets/app.js')],check=True,cwd=root)
    for old,new,count in CSS_LITERALS:swap(dest/'assets/style.css',old,new,count)
    # Nothing from the feature may be left anywhere: a hit here means a seam was missed.
    java='\n'.join(p.read_text(encoding='utf-8') for p in (dest/'java').rglob('*.java'))
    ui=(dest/'assets/app.js').read_text(encoding='utf-8')
    css=(dest/'assets/style.css').read_text(encoding='utf-8')
    for needle in PACKET_FORBIDDEN_JAVA:
        if needle in java:raise SystemExit('1.2.0 线：Java 里还留着「'+needle+'」，剥离不完整')
    for needle in PACKET_FORBIDDEN_UI:
        if needle in ui or needle in css:raise SystemExit('1.2.0 线：界面里还留着「'+needle+'」，剥离不完整')
    print('1.2.0 line staged at',dest,'— 舰长红包 feature removed',flush=True)
    return dest

p=argparse.ArgumentParser()
p.add_argument('--platform',required=True);p.add_argument('--tools',required=True);p.add_argument('--ecj')
p.add_argument('--keystore',required=True);p.add_argument('--password-file',required=True);p.add_argument('--alias',default='hazelrelease');p.add_argument('--output',default='build/VRAlarm-1.3.0.apk')
p.add_argument('--version-code',default='57');p.add_argument('--version-name',default='1.3.0')
p.add_argument('--line',default='1.3.0',choices=['1.3.0','1.2.0'],help='1.2.0 stages a copy of the sources with the 舰长红包 feature taken out')
a=p.parse_args();root=pathlib.Path(__file__).resolve().parents[1];build=root/'build/manual';build.mkdir(parents=True,exist_ok=True)
for name in ['classes','gen','dex']:
    dest=build/name
    if dest.exists():shutil.rmtree(dest)
    dest.mkdir()
src=root/'app/src/main';sdk=pathlib.Path(a.tools).resolve();platform=str(pathlib.Path(a.platform).resolve())
if a.line=='1.2.0':src=stage_without_packets(root,build/'line-1.2.0')
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
