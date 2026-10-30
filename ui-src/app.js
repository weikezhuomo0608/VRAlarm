'use strict';
const clone=value=>JSON.parse(JSON.stringify(value));
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths={bell:'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',moon:'M20.8 13A9 9 0 0 1 11 3.2 9 9 0 1 0 20.8 13Z',clock:'M12 8v4l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',sound:'M11 5 6 9H3v6h3l5 4ZM15 8a6 6 0 0 1 0 8M18 5a10 10 0 0 1 0 14',settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1Z',shield:'M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7ZM8 12l3 3 5-6',arrow:'M4 12h16m-6-6 6 6-6 6',refresh:'M20 10a8 8 0 0 0-14-5L3 8m0-5v5h5M4 14a8 8 0 0 0 14 5l3-3m0 5v-5h-5',external:'M14 3h7v7m0-7L10 14M9 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-4',history:'M3 11a9 9 0 1 1 2 7M3 3v8h8M12 7v5l4 2',play:'m9 5 11 7-11 7Z',spark:'m12 2 2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5ZM20 2v4m-2-2h4',sun:'M12 3V1M12 23v-2M3 12H1m22 0h-2M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0',music:'M9 18V5l12-2v13M9 8l12-2M9 18a3 3 0 1 1-3-3h3m12 1a3 3 0 1 1-3-3h3',file:'M14 2H5a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9ZM14 2v7h7M8 14h8M8 18h5',calendar:'M8 2v4M16 2v4M3 8h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z',plus:'M12 5v14M5 12h14',close:'m6 6 12 12M6 18 18 6',check:'m5 12 4 4L19 6',battery:'M22 10v4M3 6h15a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1M11 8l-3 5h5l-3 4',screen:'M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5',download:'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',upload:'M12 16V4m-5 5 5-5 5 5M4 16v5h16v-5',info:'M12 11v6M12 7v.1M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',wifi:'M2 8a16 16 0 0 1 20 0M5 12a11 11 0 0 1 14 0M8 16a6 6 0 0 1 8 0M12 20h.01',heart:'M20 5c-3-3-7-1-8 1-1-2-5-4-8-1-4 4 0 9 8 15 8-6 12-11 8-15',pause:'M7 4v16M17 4v16',repeat:'M17 2l4 4-4 4M3 11V9a4 4 0 0 1 4-4h14M7 22l-4-4 4-4M21 13v2a4 4 0 0 1-4 4H3',crown:'M4 18h16M4 18 3 7l5 4 4-5 4 5 5-4-1 11',packet:'M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5',pencil:'M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z'};
const icon=(name,extra='')=>`<svg class="i ${extra}" viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[name]||paths.bell}"></path></svg>`;
let seq=0,awaiting=new Map(),native=typeof window.HazelNative!=='undefined',route='home',S=null,configFingerprint='',busy=false,ruleDraft=null,ruleTarget='',alarmPainted=false,zoneList=[];
let compatibilityAction=null,compatibilitySaving=false,anchorDraft=null;
/* Doze will not wake any app more often than about once every nine minutes, so a heartbeat is
   legitimately older than that on a sleeping phone. Only past this does a stale beat mean
   something even while the service is still alive. Mirrors PollPlan.INTERRUPTION_FLOOR_MS. */
const WATCH_STALL_MS=10*60000;
const defaults={soundWithoutNotifications:false,allDay:true,timezone:'device',catchUp:false,pollSeconds:30,reliable:true,boot:true,ringtone:'starlight',customName:'未选择',volume:85,ramp:true,vibrate:true,duration:60,snoozeMinutes:5,quietCalls:true,theme:'light',preStream:true,ringQueue:false,aiOcr:true,aiKey:'',aiModel:'deepseek-flash',seedColor:'',amoled:false,hideRecents:false,recovery:true,turbo:true,backgroundDim:40,cardOpacity:94,pet:true,petCharacter:'manqu',silentUntil:0,highFrequency:false,lowPollSeconds:120,autoOpenLive:false,endNotice:false,highWindows:[{id:'h1',name:'上午高频',start:480,end:720,days:127,enabled:true},{id:'h2',name:'下午高频',start:840,end:960,days:127,enabled:true},{id:'h3',name:'晚间高频',start:1200,end:0,days:127,enabled:true}],windows:[{id:'night',name:'凌晨守候',start:60,end:360,days:127,enabled:true}]};
const previewState={config:clone(defaults),enabled:false,running:false,ringing:false,alarmSilent:false,snapshot:{},anchors:[{id:'hazel',name:'灰泽满 Hazel',uid:1298779265,uidText:'1298779265',room:1713546334,enabled:true,avatar:false,snapshot:{}}],networkError:'',serviceError:'',startError:'',inside:true,schedulePaused:false,hasWindow:true,nextBoundary:0,highInside:false,deviceInUse:false,highNextBoundary:0,pollSecondsNow:30,permissions:{notifications:false,alarmChannel:true,battery:false,fullScreen:false,exact:false,dnd:false,alarmVolume:4,alarmMax:7},zone:Intl.DateTimeFormat().resolvedOptions().timeZone,deviceZone:Intl.DateTimeFormat().resolvedOptions().timeZone,version:'1.3.0',preview:true,now:Date.now(),snoozeAt:0,testAt:0,backgroundName:'',backgroundSet:false,recoveryAt:0};
if(!native)$('#preview').textContent='界面预览 · 检测与响铃功能请安装安卓应用体验';
window.NativeReply=(id,result)=>{const p=awaiting.get(id);if(!p)return;clearTimeout(p.timer);awaiting.delete(id);result.ok?p.resolve(result.value):p.reject(new Error(result.error));};
function api(action,data={},timeoutMs=10000){
    if(!native){if(action==='state'){previewState.now=Date.now();return Promise.resolve(clone(previewState));}if(action==='history')return Promise.resolve([]);if(action==='zones'){const ids=Intl.supportedValuesOf?Intl.supportedValuesOf('timeZone'):['Asia/Shanghai','Asia/Tokyo','Asia/Kathmandu','Europe/London','America/New_York','America/Los_Angeles','Australia/Sydney'];return Promise.resolve(ids.map(id=>({id,offset:'',time:new Intl.DateTimeFormat('zh-CN',{timeZone:id,hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date())})));}if(action==='save'){Object.assign(previewState.config,data);
        // The design preview mirrors the native store here too: the page hands over a duration and
        // the deadline comes back, so the card's countdown behaves as it does on the phone.
        if(data.silentMinutes!==undefined){const minutes=data.silentMinutes;previewState.config.silentUntil=minutes===0?0:minutes<0?Number.MAX_SAFE_INTEGER:Date.now()+minutes*60000;delete previewState.config.silentMinutes;}
        return Promise.resolve(previewState.config);}if(action==='resolveAnchor')return Promise.resolve({id:data.id||'preview',room:Number(data.room)||1713546334,uid:1298779265,uidText:'1298779265',name:'灰泽满 Hazel',avatar:false});if(action==='saveAnchor'||action==='deleteAnchor'){toast('这是界面预览，请安装 APK 使用此功能');return Promise.resolve(previewState.anchors);}if(action==='toggle'||action==='test'||action==='testLater'||action==='permission'||action==='refresh'||action==='pickAudio'||action==='export'||action==='exportNotificationReport'||action==='import'){toast('这是界面预览，请安装 APK 使用此功能');return Promise.resolve(previewState);}return Promise.resolve(true);}
    return new Promise((resolve,reject)=>{const id='r'+(++seq),timer=setTimeout(()=>{awaiting.delete(id);reject(new Error('操作超时，请重新打开应用后重试'));},timeoutMs);awaiting.set(id,{resolve,reject,timer});window.HazelNative.request(id,action,JSON.stringify(data));});
}
function toast(text){const el=$('#toast');el.textContent=text;el.classList.add('visible');clearTimeout(toast.timer);toast.timer=setTimeout(()=>el.classList.remove('visible'),4200);}
function time(n){return `${String(Math.floor(n/60)).padStart(2,'0')}:${String(n%60).padStart(2,'0')}`;}
function stamp(ms,full=false){if(!ms)return '尚未检测';try{return new Intl.DateTimeFormat('zh-CN',{timeZone:S?.zone||undefined,...(full?{month:'2-digit',day:'2-digit'}:{}),hour:'2-digit',minute:'2-digit',...(full?{}:{second:'2-digit'}),hour12:false}).format(new Date(ms));}catch{return new Date(ms).toLocaleTimeString();}}
/** Just the clock time, in the same zone the schedule is read in — used for "resumes at 01:00". */
function clock(ms){if(!ms)return '';try{return new Intl.DateTimeFormat('zh-CN',{timeZone:S?.zone||undefined,hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(ms));}catch{return new Date(ms).toLocaleTimeString().slice(0,5);}}
const toneNames={starlight:'星铃',morning:'清晨',urgent:'强提醒',system:'系统闹钟',custom:'自选铃声'};
/* --- silent mode -------------------------------------------------------------
   The sound page is where the app decides whether it may make noise, so the mode that stops all
   noise belongs there. What is stored is a deadline, never a flag: a mode switched on for a
   meeting ends on its own instead of quietly swallowing the next stream. Mirrors QuietMode.java,
   which is the copy the ringing side actually reads. */
const QUIET_FOREVER_MS=5*365*24*3600000;
const quietChoiceNames={30:'30 分钟',60:'1 小时',120:'2 小时'};
/** The date text of a one-off entry, or '' for a weekly one. Mirrors Schedule.dateOf. */
function entryDate(e){return e&&e.date?String(e.date):'';}
/** A calendar date as the ISO text the store uses, or '' when the text is not a date. A bare
 *  month/day that has already passed rolls to next year, the way the weekly parser always did. */
function isoDateOf(text){
    const t=String(text||'').trim();
    let m=t.match(/^(\d{4})[\/\-.](\d{1,2})[\/\-.](\d{1,2})$/);
    let y,mo,dy;
    if(m){y=Number(m[1]);mo=Number(m[2]);dy=Number(m[3]);}
    else{
        m=t.match(/^(\d{1,2})[\/\-.月](\d{1,2})日?$/);
        if(m){const now=new Date();y=now.getFullYear();mo=Number(m[1]);dy=Number(m[2]);}
        else{
            m=t.match(/^(\d{1,2})号$/);
            if(!m)return '';
            const now=new Date();y=now.getFullYear();mo=now.getMonth()+1;dy=Number(m[1]);
        }
    }
    const probe=new Date(y,mo-1,dy);
    if(probe.getFullYear()!==y||probe.getMonth()!==mo-1||probe.getDate()!==dy)return '';
    const now=new Date();
    if(y===now.getFullYear()&&probe.getTime()<new Date(now.getFullYear(),now.getMonth(),now.getDate()).getTime()-86400000)y+=1;
    return y+'-'+String(mo).padStart(2,'0')+'-'+String(dy).padStart(2,'0');
}
/** Today as ISO text in the device's own day, matching how the store compares dates. */
function isoToday(now){const d=now||new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}
/** "9月30日" / "2027年1月2日" for a dated entry, null for a weekly one. */
function entryWhen(e){
    const date=entryDate(e);if(!date)return null;
    const parts=date.split('-'),now=new Date();
    const month=Number(parts[1]),day=Number(parts[2]);
    return (Number(parts[0])===now.getFullYear()?'':parts[0]+'年')+month+'月'+day+'日';
}
/** How early this entry is announced; the default is the global five minutes. Whole hours are
    named as hours, which is how the editor's own list offers them ("提前 1 小时", not "60 分钟").
    The line spells the moment out ("开播前 30 分钟提醒") because "默认提前量" alone read as a
    piece of jargon rather than as the one thing it does: notify you shortly before the start. */
function leadLabel(lead){
    const n=Number(lead)||0;
    if(n<=0)return '开播前 5 分钟提醒（默认）';
    return '开播前 '+(n%60===0?n/60+' 小时':pollLabel(n*60))+'提醒';
}
/** Whether an entry happens on that weekday: a dated one only on its own day. */
function occursOn(e,weekday,isoDay){const date=entryDate(e);return date?date===isoDay:!!(e.days&(1<<weekday));}
/** "30 秒" / "2 分钟" — the pace is shown in the same units wherever it appears. */
function pollLabel(seconds){const n=Number(seconds)||0;return n>=60?(n%60?`${Math.floor(n/60)} 分 ${n%60} 秒`:`${n/60} 分钟`):`${n} 秒`;}
function quietState(nowMs){
    const until=Number((S&&S.config&&S.config.silentUntil)||0),now=Number(nowMs||Date.now());
    if(!(until>0)||!(until>now))return {on:false,left:0,forever:false};
    const left=until-now;
    return {on:true,left,forever:left>QUIET_FOREVER_MS};
}
/** "剩余 1 小时 20 分钟" / "直到你手动恢复" — the countdown is the point of the mode. */
function quietLeftLabel(nowMs){
    const q=quietState(nowMs);if(!q.on)return '';
    if(q.forever)return '直到你手动恢复';
    const minutes=Math.max(1,Math.ceil(q.left/60000));
    if(minutes<60)return `剩余 ${minutes} 分钟`;
    return minutes%60?`剩余 ${Math.floor(minutes/60)} 小时 ${minutes%60} 分钟`:`剩余 ${Math.floor(minutes/60)} 小时`;
}
/** One tap from the home page's card, for a meeting that starts in a minute. */
function quietDialog(){
    const q=quietState();
    const choices=(q.on?[[0,'立即恢复响铃']]:[]).concat([[30,'静音 30 分钟'],[60,'静音 1 小时'],[120,'静音 2 小时'],[-1,'直到手动恢复']]);
    openModal('静音模式',`<div class="body-copy"><p>开会、上课这类场合：<strong>全屏提醒与通知照常，只是不响铃、也不振动。</strong>暂缓提醒、去直播间与本场只响一次的去重，都和平时一样。</p><p>${q.on?'现在是开启状态（'+esc(q.forever?'直到你手动恢复':quietLeftLabel())+'）。':'现在会正常响铃。'}静音期间「响铃测试」同样不发声，想确认铃声请先恢复响铃。</p></div><div class="chips" style="margin:16px 0 0">${choices.map(([m,label])=>`<button class="chip" data-quiet-minutes="${m}">${label}</button>`).join('')}</div>`);
}
/** Only the duration is sent; the native side turns it into a deadline, so one clock owns it. */
async function setQuiet(minutes){
    await save({silentMinutes:minutes});
    closeModal();
    toast(minutes===0?'静音模式已关闭，铃声已恢复':minutes<0?'已开启静音模式，直到你手动恢复':`已静音 ${quietChoiceNames[minutes]||minutes+' 分钟'}，到点自动恢复响铃`);
}
function quietSection(){
    const q=quietState();
    const choices=(q.on?[[0,'立即恢复响铃']]:[]).concat([[30,'静音 30 分钟'],[60,'静音 1 小时'],[120,'静音 2 小时'],[-1,'直到手动恢复']]);
    return `<div class="section-label"><h2>静音模式</h2><small>开会、上课等场合</small></div><section class="card" data-quiet><div class="row"><div class="label-icon ${q.on?'amber':''}">${icon('moon')}</div><div class="grow"><h3>${q.on?'静音中 · '+esc(q.forever?'手动恢复':quietLeftLabel()):'当前正常响铃'}</h3><p class="sub">${q.on?'全屏提醒与通知照常，只是不响铃、不振动；到点自动恢复响铃。':'开启后仍会全屏提醒、发通知，只是不响铃、不振动：适合开会、上课这类不方便出声的场合。'}</p></div></div><div class="chips">${choices.map(([m,label])=>`<button class="chip" data-quiet-minutes="${m}">${label}</button>`).join('')}</div><p class="hint" style="margin:0">静音模式只影响本应用，不会改动系统的静音或勿扰设置。静音期间「响铃测试」同样不发声：想确认铃声，请先点「立即恢复响铃」。</p></section>`;
}
function sw(key,checked,label){return `<button class="switch" role="switch" aria-checked="${!!checked}" aria-label="${esc(label)}" data-toggle="${esc(key)}"></button>`;}
function setting(title,description,right){return `<div class="setting-row"><div class="grow"><h3>${title}</h3><p class="sub">${description}</p></div>${right}</div>`;}
function select(key,value,choices){return `<select aria-label="${esc(key)}" data-setting="${key}">${choices.map(([v,n])=>`<option value="${esc(v)}" ${String(v)===String(value)?'selected':''}>${esc(n)}</option>`).join('')}</select>`;}
function hexRgb(hex){return [parseInt(hex.slice(1,3),16),parseInt(hex.slice(3,5),16),parseInt(hex.slice(5,7),16)];}
function lumaRgb(rgb){return (0.2126*rgb[0]+0.7152*rgb[1]+0.0722*rgb[2])/255;}
function mixRgb(a,b,t){return [0,1,2].map(i=>Math.round(a[i]+(b[i]-a[i])*t));}
function cssRgb(rgb){return `rgb(${rgb[0]},${rgb[1]},${rgb[2]})`;}
/** A swatch must show the colour that will really be applied, not the raw seed: dark mode
 *  lightens the accent, so the plain seed would promise a shade the user never sees. */
function seedSwatch(hex,config){
    const seed=hexRgb(hex);
    const night=config.theme==='dark'||(config.theme==='system'&&typeof matchMedia==='function'&&matchMedia('(prefers-color-scheme:dark)').matches);
    return night?cssRgb(mixRgb(seed,[255,255,255],.5)):cssRgb(seed);
}
let systemThemeBound=false;
// Writing theme variables on <html> alone loses to the body.dark stylesheet rule, because
// every card resolves its custom properties from the nearest ancestor that sets them. Both
// elements get the value so the inline declaration wins in either theme.
function setVar(name,value){
    [document.documentElement.style,document.body.style].forEach(style=>{
        if(value===null||value===undefined)style.removeProperty(name);else style.setProperty(name,value);
    });
}
function bindSystemTheme(){
    if(systemThemeBound||typeof matchMedia!=='function')return;
    systemThemeBound=true;
    const query=matchMedia('(prefers-color-scheme:dark)');
    const handler=()=>{if(S&&S.config&&S.config.theme==='system')applyTheme();};
    if(query.addEventListener)query.addEventListener('change',handler);
    else if(query.addListener)query.addListener(handler);
}
function applyTheme(){const c=S.config,night=c.theme==='dark'||(c.theme==='system'&&matchMedia('(prefers-color-scheme:dark)').matches);
    document.body.classList.toggle('dark',night);
    document.body.classList.toggle('amoled',night&&!!c.amoled);
    bindSystemTheme();
    // Seed colour drives the accent, using the same light/dark derivation as upstream 1.0.6.
    const seed=c.seedColor&&/^#[0-9a-fA-F]{6}$/.test(c.seedColor)?hexRgb(c.seedColor):null;
    if(seed){let primary=seed;
        if(night)primary=mixRgb(seed,[255,255,255],.5);
        else if(lumaRgb(seed)>.45)primary=mixRgb(seed,[0,0,0],.38);
        setVar('--primary',cssRgb(primary));
        setVar('--on-primary',lumaRgb(primary)>.179?'#111318':'#ffffff');
    }else{setVar('--primary',null);setVar('--on-primary',null);}
    const isAlarm=document.body.classList.contains('alarm-page');
    if(isAlarm)return;
    // Card translucency only matters where the background image can show through.
    const base=night?(c.amoled?[18,17,20]:[36,44,53]):[255,255,255];
    const op=Math.min(100,Math.max(75,Number(c.cardOpacity)||94))/100;
    setVar('--card',`rgba(${base[0]},${base[1]},${base[2]},${op})`);
    const layer=document.getElementById('bg-layer');
    if(layer){
        // The address is stable, so the browser would keep serving the first decoded image for
        // every later pick. A revision suffix is appended so each import becomes a new URL.
        if(S.backgroundSet){
            const stamp=S.backgroundRevision||S.backgroundSet;
            layer.style.backgroundImage=`url(/background/current?v=${stamp})`;
            document.body.classList.add('has-bg');
        }
        else{layer.style.backgroundImage='';document.body.classList.remove('has-bg');}
        setVar('--bg-dim',String(Math.min(90,Math.max(0,Number(c.backgroundDim)||0))/100));
    }
}
function header(){const history=route==='history';$('#header').innerHTML=`<div class="brand"><img src="hazel.png" alt="VR闹钟图标"><div><div class="brand-name">VR闹钟</div><div class="brand-sub">LIVE STREAM ALARM</div></div></div><button class="icon-button" data-action="${history?'back':'history'}" aria-label="${history?'返回':'通知记录'}">${icon(history?'close':'history')}</button>`;}
function navigation(){const tabs=[['home','bell','守候'],['anchors','heart','主播'],['week','calendar','日程'],['schedule','clock','时段'],['sound','sound','声音'],['settings','settings','设置']];$('#nav').innerHTML=tabs.map(([id,i,label])=>`<button class="${route===id?'active':''}" data-route="${id}" ${route===id?'aria-current="page"':''}><span class="icon-wrap">${icon(i)}</span>${label}</button>`).join('');}
function heading(label,title,sub){return `<div class="page-heading"><div class="eyebrow">${label}</div><h1>${title}</h1><p>${sub}</p></div>`;}
let lastRoute=null;
function render(){if(!S)return;
    // A re-render must not move the reader: state ticks arrive every couple of seconds, and
    // rebuilding #content used to snap the page (and any open list inside it) back to the top.
    const same=route===lastRoute,top=window.scrollY;
    const lists=[...document.querySelectorAll('.live-picker-list')].map(el=>el.scrollTop);
    applyTheme();syncPet();if(document.body.classList.contains('alarm-page')){renderAlarm();lastRoute=route;return;}header();navigation();standPet();
    if(route==='home')home();if(route==='anchors')anchorsPage();if(route==='week')week();if(route==='schedule')schedule();if(route==='sound')sound();if(route==='settings')settings();if(route==='history')history();
    lastRoute=route;
    if(same&&top>0)window.scrollTo(0,top);
    if(same)[...document.querySelectorAll('.live-picker-list')].forEach((el,i)=>{if(lists[i])el.scrollTop=lists[i];});
}
function enabledAnchors(){return (S.anchors||[]).filter(a=>a.enabled);}
function statusSummary(){
    const list=enabledAnchors(),known=list.filter(a=>(a.snapshot||{}).checkedAt);
    if(!known.length)return [S.networkError?'待重试':'尚未检测',S.networkError?'wifi':'clock',false];
    const live=known.filter(a=>a.snapshot.status===1);
    if(live.length===1)return [live[0].name+' 正在直播','bell',true];
    if(live.length>1)return [live.length+' 位主播正在直播','bell',true];
    if(Date.now()-Math.max(...known.map(a=>a.snapshot.checkedAt))>Math.max(300000,S.config.pollSeconds*4000))return ['状态待更新','clock',false];
    if(known.some(a=>a.snapshot.status===2))return ['轮播中 · 不触发开播提醒','play',false];
    return ['暂未开播','moon',false];
}
function alarmAvailable(){const p=S.permissions;return !!S.config.soundWithoutNotifications||!!(p.notifications&&(p.alarmChannelImportance===undefined?p.alarmChannel:p.alarmChannelImportance>0));}
function watchNoticeContent(){
    const status=S.watchNotification?.status||(S.running?'unknown':S.enabled?(S.schedulePaused?'paused':'interrupted'):'stopped');
    const copy={
        stopped:['尚未开启','开启守候后，使用前台服务持续监测，并提交常驻状态通知。'],
        paused:['时段外 · 守候已暂停','守候开关是开着的，但按你的时段现在属于时间外：前台服务已停下，常驻通知同时移除，进入时段时自动恢复。'],
        interrupted:['后台服务待恢复','守候开关已保存，但服务暂未运行。请检查后台权限后重新开启。'],
        permission_blocked:['通知被系统拦截','前台服务正在运行，但系统未允许通知，通知栏无法显示常驻卡片。'],
        channel_blocked:['守候通知通道已关闭','前台服务正在运行。请打开“后台守候状态”通道，才能显示常驻卡片。'],
        registered:['前台守候已运行','守候通知已提交到系统；可在通知中立即检查、关闭响铃或停止守候。'],
        not_posted:['前台服务正在运行','系统暂未列出守候通知。请下拉通知栏确认，并检查通知设置。'],
        unknown:['前台服务正在运行','暂时无法读取通知的提交状态，请下拉通知栏确认。']
    };
    const [label,detail]=copy[status]||copy.unknown;
    return `<div class="row"><div class="label-icon">${icon('shield')}</div><div class="grow"><h3>常驻通知守候</h3><p class="sub">${esc(label)}</p></div></div><p class="sub" style="margin-top:12px">${esc(detail)}</p><div class="permission-actions"><button class="text-button" data-permission="watchChannel">守候通知设置 ${icon('external')}</button><button class="text-button" data-action="watchNotificationHelp">通知授权帮助 ${icon('arrow')}</button></div><p class="hint">停止守候后会移除通知。常驻服务可减少后台中断，但仍受系统省电和强行停止限制。</p>`;
}
function watchNoticeCard(){const status=S.watchNotification?.status||(S.running?'unknown':S.enabled?(S.schedulePaused?'paused':'interrupted'):'stopped');const alert=status==='interrupted'||status==='permission_blocked'||status==='channel_blocked';return `<section class="card${alert?' notice-alert':''}" data-watch-notice>${watchNoticeContent()}</section>`;}
function paintWatchNotice(){const card=$('[data-watch-notice]');if(!card||!S)return;const html=watchNoticeContent();if(card.innerHTML!==html)card.innerHTML=html;}
function compatibilityInfo(next=null){
    compatibilityAction=next;
    openModal('通知异常，也可以选择响铃',`<div class="body-copy"><p>如果小米等手机的通知开关反复关闭，可以开启<strong>通知异常时仍响铃</strong>。只需在本应用中选择，无需额外工具。</p><p>开启后，即使系统未允许通知，也会按你的时段、时区和音量设置检测开播并尝试播放闹铃。不会替你打开系统通知权限。</p><p><strong>通知栏和锁屏提醒可能不显示。</strong>响铃时可打开应用关闭，或等待设定的 ${S.config.duration} 秒自动停止。可选的悬浮关闭按钮需要“显示在其他应用上层”权限。</p><p>勿扰、通话和系统后台限制仍然有效。建议开启后做一次锁屏测试。</p></div>`,`<div class="sheet-actions"><button class="secondary" data-permission="notifications">允许系统通知</button><button class="primary" data-action="confirmCompatibility">${next?'兼容响铃并继续':'开启兼容响铃'}</button></div>`);
}
function home(){
    const c=S.config,[liveLabel,liveIcon,isLive]=statusSummary(),n=c.windows.filter(x=>x.enabled).length,p=S.permissions,ready=[p.notifications,p.alarmChannel,p.fullScreen,p.battery,p.exact].filter(Boolean).length;
    const armed=enabledAnchors(),liveNow=armed.filter(a=>(a.snapshot||{}).status===1);
    const lastCheck=Math.max(0,...armed.map(a=>((a.snapshot||{}).checkedAt||0)));
    const watchChip=esc(armed.length===0?'尚未选择主播':armed.length===1?'专属守候 · '+armed[0].name:'守候 '+armed.length+' 位主播');
    // A parked watch is not a broken one. schedulePaused is the service's own reading of the
    // schedule; the fallback keeps an older core driving the page from showing a false alarm.
    const paused=!!(S.enabled&&(S.schedulePaused===undefined?!S.inside:S.schedulePaused));
    const watchTitle=S.enabled?(paused?'时段外 · 守候已暂停':S.running?'正在替你守候':'守候需要恢复'):'准备好，再开始守候';
    const watchSub=S.enabled?(paused?`不联网、不检测开播${S.nextBoundary?'；'+clock(S.nextBoundary)+' 自动恢复':''}`:S.inside?'当前在提醒时段内 · 开播即提醒':'时段外安静守候 · 不会响铃'):'先完成权限检查，锁屏也能安心等待';
    let html=`<section class="hero"><div class="hero-copy"><div class="eyebrow">NEVER MISS A STREAM</div><h1>${S.enabled?'安心去忙，<br>开播叫你。':'开播那一刻，<br>马上叫醒你。'}</h1><div class="tiny-note">${icon('heart')}把期待，交给一声铃响</div></div><img class="hero-art" src="hazel.png" alt="VR闹钟插画"><div class="hero-chip"><span class="dot ${S.enabled?'green':''}"></span>${watchChip}</div></section>
    <section class="card"><div class="row"><div class="label-icon ${S.enabled&&S.running?'green':''}">${icon(S.enabled?'shield':'bell')}</div><div class="grow"><h3>${watchTitle}</h3><p class="sub">${watchSub}</p></div>${sw('enabled',S.enabled,'开启或停止直播守候')}</div>
    <div class="metrics"><button class="metric" data-route="schedule"><strong>${c.allDay?'全天':n+' 个时段'}</strong><small>提醒范围</small></button><button class="metric" data-route="sound"><strong>${c.volume}<span class="small">%</span></strong><small>闹钟音量</small></button><button class="metric" data-route="settings"><strong>${Number(S.pollSecondsNow||c.pollSeconds)}<span class="small"> 秒</span></strong><small>检测间隔</small></button></div>
    ${!S.enabled?`<button class="primary watch-button" data-action="start">${icon('bell')}开始守候</button>`:''}
    <div class="live-info"><span class="dot ${isLive?'live':''}"></span><span class="live-state">${liveLabel}</span><button class="text-button" data-action="refresh">${icon('refresh')}刷新</button></div>${liveNow.length===1&&liveNow[0].snapshot.title?`<p class="live-title">${esc(liveNow[0].snapshot.title)}</p>`:''}<p class="timestamp" style="margin-top:7px">${lastCheck?'上次成功检测 '+stamp(lastCheck)+' · '+esc(S.zone):'尚未连接直播间，点击刷新查看状态'}</p></section>`;
    html+=highFrequencyCard();
    if(!armed.length)html+=`<div class="card warning"><p>当前没有启用中的主播，守候不会响铃。</p><button class="text-button" data-route="anchors">去主播页启用 ${icon('arrow')}</button></div>`;
    // Silence has to be visible where the watch is: the reminder still arrives, it just arrives
    // without a sound, so the card stays on the home page until the deadline passes.
    const quiet=quietState();
    if(quiet.on)html+=`<section class="card warning"><div class="row"><div class="label-icon amber">${icon('moon')}</div><div class="grow"><h3>静音模式 · ${esc(quiet.forever?'手动恢复':quietLeftLabel())}</h3><p class="sub">开播时仍会全屏提醒并发通知，只是不响铃、不振动。${quiet.forever?'恢复响铃前一直有效。':'到点自动恢复响铃。'}</p></div><button class="text-button" data-action="quietDialog">调整</button></div></section>`;
    if(S.ringing)html=`<div class="card warning"><div class="row"><div class="grow"><h3>响铃进行中</h3><p class="sub">${S.alarmAnchor?esc(S.alarmAnchor)+' · ':''}点按右侧按钮立即结束</p></div><button class="primary" data-action="dismiss">关闭响铃</button></div></div>`+html;
    // Liveness, not heartbeat age, decides whether the watch is really gone. The state poll runs
    // in the same process as the service, so S.running cannot be stale; a heartbeat is delayed by
    // exactly the doze that delays the checks, so a stale one proves nothing on its own — under
    // doze no app is woken more often than about once every nine minutes, and warning at three
    // made the phone's own sleep look like a failure.
    const beat=S.serviceHeartbeatAt||0,silent=Date.now()-beat,stalled=beat>0&&silent>WATCH_STALL_MS;
    if(S.enabled&&!S.running&&!paused)html+=`<div class="card warning"><p>守候服务已中断${beat?'，上次心跳在约 '+Math.round(silent/60000)+' 分钟前':'，尚未收到服务心跳'}，期间不会检测开播。应用每 15 分钟会尝试自动恢复；若经常中断，请在系统设置中允许“自启动”，把电池策略设为“不限制”，并在最近任务里长按本应用的卡片将它<strong>锁定</strong>——从最近任务划掉应用时，系统会连恢复用的闹钟一起停掉。</p><button class="text-button" data-action="start">立即重新开启 ${icon('arrow')}</button>${p.accessibility?'':`<button class="text-button" data-action="accessibilityInfo">开启无障碍守候辅助 ${icon('arrow')}</button>`}</div>`;
    else if(S.enabled&&!paused&&stalled)html+=`<div class="card"><p class="sub">守候服务仍在运行，只是最近一次心跳在约 ${Math.round(silent/60000)} 分钟前——手机休眠或省电策略会同时推迟心跳与检测，恢复亮屏后会自动接上。</p></div>`;
    // A parked watch is the intended state, not a failure, so it gets its own card instead of the
    // interruption one. With every rule switched off there is no next window at all: that is a
    // configuration mistake and is said out loud, with the one-tap way out of it.
    if(paused)html+=S.hasWindow===false
        ?`<div class="card warning"><p>当前是自定义时段，但<strong>没有任何启用中的时段</strong>：守候会一直暂停，不会检测开播，也不会响铃。切到「全天提醒」，或添加一个时段即可恢复。</p><button class="text-button" data-route="schedule">去设置时段 ${icon('arrow')}</button></div>`
        :`<div class="card"><div class="row"><div class="label-icon">${icon('moon')}</div><div class="grow"><h3>时段外 · 守候已暂停</h3><p class="sub">${S.nextBoundary?clock(S.nextBoundary)+' 进入时段时自动恢复；':''}期间不联网、不检测开播。</p></div><button class="text-button" data-route="schedule">时段设置 ${icon('arrow')}</button></div></div>`;
    if(S.enabled)html+=watchNoticeCard();
    if(!p.notifications)html+=`<div class="card warning"><p>${c.soundWithoutNotifications?'通知异常兼容响铃已开启；系统通知仍未获准，响铃时可打开应用关闭。':'系统尚未允许通知。可在设置中授权，或选择通知异常时仍响铃。'}</p><button class="text-button" data-action="compatibilityInfo">${c.soundWithoutNotifications?'查看兼容响铃说明':'选择兼容响铃'} ${icon('arrow')}</button></div>`;
    if(S.testAt>Date.now())html+=`<div class="card warning"><div class="row"><div class="grow"><h3>锁屏测试已安排</h3><p class="sub">${stamp(S.testAt)} 响铃，现在可以锁屏</p></div><button class="text-button" data-action="cancelTest">取消</button></div></div>`;
    if(S.snoozeAt>0)html+=`<div class="card"><div class="row"><div class="label-icon">${icon('clock')}</div><div class="grow"><h3>稍后再叫你</h3><p class="sub">${stamp(S.snoozeAt)} 再确认本场直播</p></div><button class="text-button" data-action="cancelSnooze">取消</button></div></div>`;
    if(S.networkError||S.serviceError||S.startError)html+=`<div class="card warning"><p>${esc(S.serviceError||S.startError||S.networkError)}</p></div>`;
    html+=`<div class="quick-actions"><button class="quick" data-action="testDialog">${icon('sound')}<div><strong>响铃测试</strong><small>听一下，放心等</small></div></button><button class="quick" data-action="openLive" aria-expanded="${livePickerOpen?'true':'false'}" aria-controls="live-picker">${icon('external')}<div class="grow"><strong>打开直播间</strong><small>${livePickerOpen?'点选一位主播':liveNow.length?liveNow.length+' 位正在直播 · 点按选择':'去守候的直播间看看'}</small></div><span class="caret" aria-hidden="true"></span></button></div>${livePickerOpen?livePicker():''}
    <section class="card ready"><div class="row"><div class="label-icon green">${icon('shield')}</div><div class="grow"><h3>响铃准备度 <span class="muted">${ready} / 5</span></h3><p class="sub">${ready===5?'主要权限已就绪，仍建议做一次锁屏测试':'检查通知与后台权限，让提醒更可靠'}</p></div><button class="text-button" data-route="settings">去检查 ${icon('arrow')}</button></div></section>
    <p class="footnote">每场直播只自动提醒一次。<br>关机、断网或被系统强制停止时，提醒可能延迟或无法送达。<br>本质AI拼好钟，切勿盲目信任。</p>`;
    $('#content').innerHTML=html;
}
function anchorStatus(snapshot){
    if(!snapshot||!snapshot.checkedAt)return ['尚未检测','clock',false];
    if(Date.now()-snapshot.checkedAt>Math.max(300000,S.config.pollSeconds*4000))return ['状态待更新','clock',false];
    if(snapshot.status===1)return ['正在直播','bell',true];
    if(snapshot.status===2)return ['轮播中 · 不触发','play',false];
    return ['暂未开播','moon',false];
}
function artHue(key){let h=0;const s=String(key||'');for(let i=0;i<s.length;i++)h=(h*31+s.charCodeAt(i))%360;return h;}
function anchorArt(a,cls=''){
    const letter=esc((a.name||'?').trim().slice(0,1)||'?');
    const h=artHue(a.id||a.name);
    const tone=`background:linear-gradient(135deg,hsl(${h},46%,72%),hsl(${(h+40)%360},46%,58%))`;
    if(!a.avatar)return `<div class="anchor-art placeholder ${cls}" style="${tone}">${letter}</div>`;
    // Absolute path on purpose: a relative one resolves under /assets/ and misses the app's /avatar/ route.
    return `<div class="anchor-art ${cls}" style="${tone}"><span class="anchor-letter">${letter}</span><img src="/avatar/${esc(a.id)}.png" alt="${esc(a.name)}的头像"></div>`;
}
function anchorsPage(){
    const list=S.anchors||[],on=list.filter(a=>a.enabled),live=on.filter(a=>(a.snapshot||{}).status===1);
    const packetOn=list.filter(a=>a.packet===true).length, kaoziOn=list.filter(a=>a.allDay===true).length;
    let html=heading('YOUR BROADCASTERS','想被谁叫醒，就加上谁。','每位主播一个开关；提醒时段与铃声由所有主播共用。')+
        `<section class="card"><div class="row"><div class="label-icon ${live.length?'green':''}">${icon(live.length?'bell':'shield')}</div><div class="grow"><h3>${live.length?live.length+' 位主播正在直播':'守候中 · '+on.length+' 位主播'}</h3><p class="sub">同一时间只响一次；其余主播开播时记入记录，不会叠着响。</p></div></div><div class="rule-footer"><small class="muted">头像只在识别时下载一次，失败不会自动补</small><button class="text-button" data-action="refreshAvatars">${icon('refresh')}刷新全部头像</button></div></section>`;
    // 舰长红包 and 考子闹钟 both live up here now: one entry each, one panel each, a switch per room.
    html+=`<div class="quick-actions"><button class="quick" data-action="packetPicker" aria-expanded="${packetPickerOpen?'true':'false'}" aria-controls="packet-picker">${icon('packet')}<div class="grow"><strong>舰长红包</strong><small>${packetOn?packetOn+' 个房间在盯着':'点开，选择要盯哪几个房间'}</small></div><span class="caret" aria-hidden="true"></span></button><button class="quick" data-action="kaoziPicker" aria-expanded="${kaoziPickerOpen?'true':'false'}" aria-controls="kaozi-picker">${icon('crown')}<div class="grow"><strong>考子</strong><small>${kaoziOn?kaoziOn+' 位全天守候':'不受时段限制，全天盯人'}</small></div><span class="caret" aria-hidden="true"></span></button></div>${packetPickerOpen?packetPicker():''}${kaoziPickerOpen?kaoziPicker():''}`;
    html+=list.map(a=>{
        const [label,i,isLive]=anchorStatus(a.snapshot||{});
        return `<article class="card anchor-card ${a.enabled?'':'disabled-rule'} ${a.allDay?'devoted':''}"><div class="row">${anchorArt(a)}
            <div class="grow"><div class="anchor-name">${esc(a.name)}</div><div class="live-info"><span class="dot ${isLive?'live':''}"></span><span class="live-state">${label}</span>${a.packet?'<span class="rule-tag packet">舰包</span>':''}</div><small class="sub"><span class="nowrap">UID ${esc(readUid(a))}</span> · <span class="nowrap">直播间 ${esc(String(a.room))}</span></small></div>
            <div class="anchor-switches"><span class="mini">检测${sw('anchor:'+a.id,a.enabled,a.name+' 的检测开关')}</span><span class="mini">响铃${sw('ring:'+a.id,a.alarm!==false,a.name+' 的响铃开关')}</span></div></div>
            <div class="rule-footer anchor-footer"><small class="muted">${a.snapshot&&a.snapshot.checkedAt?`<span class="nowrap">上次检测 ${stamp(a.snapshot.checkedAt)}</span>`:'<span class="nowrap">尚未检测</span>'}${a.stats&&a.stats.count30!==undefined?` · <span class="nowrap">本周 ${a.stats.count7} 场</span> · <span class="nowrap">近 30 天 ${a.stats.count30} 场</span>`:''}</small><div class="anchor-actions"><button class="text-button" data-anchor-schedule="${esc(a.id)}">日程 ${(a.schedule||[]).length?`<span class="muted">${a.schedule.length}</span>`:''} ${icon('calendar')}</button><button class="text-button" data-anchor-edit="${esc(a.id)}">编辑 ${icon('pencil')}</button><button class="text-button" data-anchor-open="${esc(a.id)}">直播间 ${icon('external')}</button><button class="text-button" data-anchor-profile="${esc(a.id)}">空间 ${icon('external')}</button></div></div></article>`;
    }).join('');
    html+=`<button class="add-rule" data-action="addAnchor">${icon('plus')}添加主播</button><p class="hint">${icon('info')} 卡片上有两个开关：<strong>检测</strong>关掉后不再检测这位主播；<strong>响铃</strong>关掉后照常检测、日程照常显示，只是不吵你。页面顶部的<strong>「舰长红包」</strong>与<strong>「考子」</strong>是逐房间开关：前者盯红包，后者让这位主播全天守候、不受时段限制（金色卡片的就是它）。</p>`;
    $('#content').innerHTML=html;
}
/** 考子闹钟: the room is watched around the clock, whatever the reminder windows say. The switch
 *  lives in the 考子 entry above the cards, next to the red-packet one. */
function kaoziPicker(){
    const list=S.anchors||[];
    const rows=list.map(a=>`<div class="packet-row"><div class="grow"><strong>${esc(a.name)}</strong><small>${a.allDay===true?'全天守候 · 不受时段限制':'只按时段守候'}</small></div>${sw('allDay:'+a.id,a.allDay===true,a.name+' 的考子闹钟（全天守候，不受时段限制）')}</div>`).join('');
    return `<section class="live-picker" id="kaozi-picker"><div class="live-picker-head">${icon('crown')}考子 · 打开的主播全天守候，不受提醒时段限制</div><div class="live-picker-list">${rows}</div><p class="hint" style="padding:11px 15px 13px">给必须盯住的主播用：打开后它<b>不再受「时段」限制</b>，任何时间开播都会响铃。代价是时段之外手机也要查：那些小时自动按<b>「低频检测间隔」</b>慢查（默认 2 分钟），<b>不保持唤醒锁</b>、由闹钟按点叫醒，手机可以照常睡。<b>守候总开关关着的时候，谁都不会被检测——考子也一样。</b></p></section>`;
}
/** The per-room switches for 舰长红包, the one setting that used to live in the anchor editor. */
function packetPicker(){
    const list=S.anchors||[];
    const rows=list.map(a=>`<div class="packet-row"><div class="grow"><strong>${esc(a.name)}</strong><small>${a.packet===true?'直播时连着弹幕盯着红包':'不盯这个房间'}</small></div>${sw('packet:'+a.id,a.packet===true,a.name+' 的舰长红包提醒')}</div>`).join('');
    return `<section class="live-picker" id="packet-picker"><div class="live-picker-head">${icon('packet')}舰长红包 · 打开的房间会在它直播时远程盯着</div><div class="live-picker-list">${rows}</div><p class="hint" style="padding:11px 15px 13px">舰长红包是别人掏钱发的 <strong>0.1 折上舰红包</strong>：谁都能抢，一般几分钟就被抢光，所以是「一出现就提醒」。只提醒，不替你抢。「试连一次弹幕」在每位主播的编辑里。</p></section>`;
}
function editAnchor(id){
    const existing=(S.anchors||[]).find(a=>a.id===id)||null;
    anchorDraft=existing?{id:existing.id,name:existing.name,uid:readUid(existing),room:existing.room,resolvedRoom:existing.room,enabled:existing.enabled,alarm:existing.alarm!==false,packet:existing.packet===true,allDay:existing.allDay===true,avatar:!!existing.avatar}
                        :{id:'',name:'',uid:'',room:'',resolvedRoom:0,enabled:true,alarm:true,packet:false,allDay:false,avatar:false};
    const d=anchorDraft;
    openModal(existing?'编辑主播':'添加主播',`<label class="form-label" for="anchor-room">主播 UID（推荐）或直播间链接</label><input class="input" id="anchor-room" maxlength="80" value="${esc(d.uid||String(d.room||''))}" placeholder="例如 3546729368520811，或粘贴 space/live 链接"><button class="secondary" data-action="resolveAnchor" style="margin-top:12px">${icon('refresh')}识别主播信息</button><button class="secondary" data-action="refreshAvatar" style="margin-top:10px">${icon('download')}重新下载头像</button><div id="anchor-preview"></div><label class="form-label" for="anchor-name">主播名称</label><input class="input" id="anchor-name" maxlength="40" value="${esc(d.name)}" placeholder="识别后自动填写"><div class="setting-row"><div class="grow"><h3>开播响铃</h3><p class="sub">关闭后仍检测并显示在时间线，但不响铃</p></div>${sw('draftAlarm',d.alarm!==false,'开播响铃开关')}</div><p class="hint">${icon('crown')} <strong>考子闹钟</strong>（全天守候，不受时段限制）在<strong>主播页顶部的「考子」入口</strong>里逐房间开关；<strong>舰长红包提醒</strong>在旁边的「舰长红包」入口里——都在看得见的地方，这里只留说明和下面的试连工具。</p><button class="secondary" data-action="probeDanmaku" style="margin-top:10px">${icon('spark')}试连一次弹幕</button><div id="packet-probe"></div><p class="hint">试连工具：点它会连上该房间的弹幕听 <strong>20 秒</strong>，把连接过程和收到的命令原样列出来（在那之前会先取一次弹幕服务器地址，失败的步骤也会写出来）。红包只是提醒，抢的动作由你自己来。</p><p class="hint">推荐填<strong>主播 UID</strong>（主页地址 space.bilibili.com 后面那串数字，例如 3546729368520811），也可直接粘贴主播的主页链接；应用会自动找到对应直播间。直播间链接（live.bilibili.com/…）同样可用，但换直播间后需要重新识别。名称会显示在提醒通知里；改动号码后需要重新识别，避免认错主播。</p><div class="form-error" id="anchor-error"></div>`,`<div class="sheet-actions">${existing?'<button class="secondary outline" data-action="deleteAnchor">删除主播</button>':'<button class="secondary outline" data-action="closeModal">取消</button>'}<button class="primary" data-action="saveAnchorDraft">保存主播</button></div>`);
    paintAnchorPreview();
}
function paintAnchorPreview(){
    const box=$('#anchor-preview');if(!box||!anchorDraft)return;
    const d=anchorDraft;
    box.innerHTML=d.room?`<div class="anchor-preview">${anchorArt(d)}<div class="grow"><strong>${esc(d.name||'尚未识别')}</strong><small class="sub">${uidDigitsOnly(d.uid)?`<span class="nowrap">UID ${esc(uidText(d.uid))}</span> · <span class="nowrap">直播间 ${esc(String(d.room))}</span>`:'请先点“识别主播信息”'}</small></div></div>`:'';
}
/** Accepts a UID, a space link, a live link or a room number. Bare digits are read as a UID,
 *  because that is the form the page asks for and it is the stable id across rooms. Bilibili
 *  hands out 16-digit uids now, so the digit bound has to clear that or the typed uid is
 *  rejected before it ever reaches the network.
 *
 *  The uid is carried as a digit STRING, never as a Number: JavaScript loses precision past
 *  9007199254740991 (Number.MAX_SAFE_INTEGER), and 16-digit uids run up to 9999999999999999,
 *  so Number('9999999999999999') silently becomes 10000000000000000. Round-tripping through a
 *  float would show the wrong uid and make the "changed number must be re-resolved" guard fire
 *  on an unchanged one. Room numbers stay numeric: they are far below the safe range. */
const MAX_UID_DIGITS=18;
const UID_DIGITS_RE=`\\d{2,${MAX_UID_DIGITS}}`;
function parseAnchorInput(raw){
    const text=String(raw||'').trim();
    if(!text)return null;
    let m=text.match(new RegExp(`space\\.bilibili\\.com\\/(${UID_DIGITS_RE})`,'i'));
    if(m)return {uid:m[1]};
    m=text.match(new RegExp(`^(?:uid|UID)\\s*[:：]?\\s*(${UID_DIGITS_RE})$`));
    if(m)return {uid:m[1]};
    m=text.match(/live\.bilibili\.com\/(?:blanc\/)?(\d{2,12})/i);
    if(m)return {room:Number(m[1])};
    if(new RegExp(`^${UID_DIGITS_RE}$`).test(text))return {uid:text};
    return null;
}
/** Uids are strings here; the page has to compare them as text so a 16-digit uid is not
 *  rounded before the comparison. Anything without digits reads as 0, meaning "not set". */
function uidText(value){return value===null||value===undefined?'':String(value);}
function uidDigitsOnly(value){const t=uidText(value);return /^\d+$/.test(t)&&t!=='0'?t:'';}
/** Read a uid out of an object the native side sent back. The bridge carries both `uid` (a
 *  Java long, exact but impossible to represent in JavaScript past 2^53-1) and `uidText` (the
 *  same number as a decimal string). Prefer the string so a 16-digit uid survives; fall back to
 *  the number for replies from an older build, where it is all we get. */
function readUid(source){if(!source)return '';const text=uidText(source.uidText);return text||uidText(source.uid);}
async function resolveAnchorDraft(){
    const error=$('#anchor-error');if(error)error.textContent='';
    const button=$('#modal [data-action="resolveAnchor"]');
    const parsed=parseAnchorInput($('#anchor-room').value);
    if(!parsed){if(error)error.textContent='请填写主播 UID（如 3546729368520811）、主页链接，或直播间链接（live.bilibili.com/…）';return;}
    const room=parsed.room||0;
    // Two lookups plus the avatar download: give it longer than the default bridge timeout.
    if(button){button.disabled=true;button.textContent='正在识别…';}
    try{
        // A uid goes over the bridge as the typed digit string, so a 16-digit one cannot be
        // rounded in transit; a room number stays a number.
        const found=await api('resolveAnchor',{room,uid:parsed.uid||'',id:anchorDraft.id},60000);
        anchorDraft={...anchorDraft,...found,uid:readUid(found),resolvedRoom:Number(found.room)||0};
        $('#anchor-name').value=found.name||'';
        // Keep what the user typed when it was a uid: replacing it with the room number would
        // make the field look like it ignored the uid they just entered.
        $('#anchor-room').value=parsed.uid?parsed.uid:String(found.room);
        paintAnchorPreview();
        toast(found.avatar?'已识别主播名称与头像':'已识别主播名称，头像未能读取');
    }catch(e){if(error)error.textContent=e.message;}
    finally{if(button){button.disabled=false;button.innerHTML=`${icon('refresh')}识别主播信息`;}}
}
async function refreshAvatarDraft(){
    const error=$('#anchor-error');if(error)error.textContent='';
    if(!anchorDraft.room){if(error)error.textContent='请先填写直播间号码';return;}
    const button=$('#modal [data-action="refreshAvatar"]');
    if(button){button.disabled=true;button.textContent='正在获取…';}
    try{
        // Same resolution as 识别主播信息, but keyed to the stored room so it only refreshes art.
        const found=await api('resolveAnchor',{room:anchorDraft.room,id:anchorDraft.id},40000);
        anchorDraft={...anchorDraft,...found,uid:readUid(found),resolvedRoom:Number(found.room)||0};
        $('#anchor-name').value=found.name||$('#anchor-name').value;paintAnchorPreview();
        toast(found.avatar?'头像已更新':'未能获取头像，稍后可再试');
    }catch(e){if(error)error.textContent=e.message;}
    finally{if(button){button.disabled=false;button.innerHTML=`${icon('download')}重新下载头像`;}}
}
async function saveAnchorDraft(){
    const error=$('#anchor-error');if(error)error.textContent='';
    try{
        const parsed=parseAnchorInput($('#anchor-room').value),name=($('#anchor-name').value||'').trim();
        if(!parsed)throw new Error('请填写主播 UID 或直播间链接');
        // The resolved identity is whatever was recognised last; the typed value must not have
        // moved away from it, or the uid would be attached to a different room. A typed uid is
        // compared as text: routing it through Number would round 16-digit uids and reject an
        // unchanged number as if the user had edited it.
        const resolvedRoom=Number(anchorDraft.resolvedRoom||anchorDraft.room||0);
        const typedRoom=parsed.room?Number(parsed.room):0;
        const typedUid=uidDigitsOnly(parsed.uid);
        const resolvedUid=uidDigitsOnly(anchorDraft.uid);
        if(typedRoom>0&&typedRoom!==resolvedRoom)throw new Error('请先点“识别主播信息”，确认是这个直播间');
        if(typedUid&&resolvedUid&&typedUid!==resolvedUid)throw new Error('请先点“识别主播信息”，确认是这个主播');
        if(!resolvedUid||resolvedRoom<=0)throw new Error('请先点“识别主播信息”，确认是这个直播间');
        if(!name)throw new Error('请填写主播名称');
        await putAnchor({id:anchorDraft.id,name,uid:resolvedUid,room:resolvedRoom,enabled:anchorDraft.enabled,alarm:anchorDraft.alarm!==false,packet:anchorDraft.packet===true,allDay:anchorDraft.allDay===true});
        closeModal();toast('主播已保存');
    }catch(e){if(error)error.textContent=e.message;}
}
async function putAnchor(anchor){
    stateRevision++;
    S.anchors=await api('saveAnchor',{anchor});
    render();await window.refreshNative(true);
}
async function toggleAnchor(id){
    const a=(S.anchors||[]).find(x=>x.id===id);if(!a||busy)return;busy=true;
    try{stateRevision++;S.anchors=await api('saveAnchor',{anchor:{id:a.id,name:a.name,uid:readUid(a),room:a.room,enabled:!a.enabled,alarm:a.alarm!==false}});render();await window.refreshNative(true);}
    catch(e){toast(e.message);}finally{busy=false;}
}
/** The two per-room watches, toggled from the 主播 page rather than from the editor dialog. Both
 *  payloads carry every flag, so a save can never clear the switch the user did not touch. */
async function togglePacket(id){await putFlag(id,'packet');}
async function toggleAllDay(id){await putFlag(id,'allDay');}
async function putFlag(id,key){
    const a=(S.anchors||[]).find(x=>x.id===id);if(!a||busy)return;busy=true;
    const anchor={id:a.id,name:a.name,uid:readUid(a),room:a.room,enabled:a.enabled,alarm:a.alarm!==false,packet:a.packet===true,allDay:a.allDay===true};
    anchor[key]=!(anchor[key]===true);
    try{stateRevision++;S.anchors=await api('saveAnchor',{anchor});render();await window.refreshNative(true);}
    catch(e){toast(e.message);}finally{busy=false;}
}
async function toggleRing(id){
    const a=(S.anchors||[]).find(x=>x.id===id);if(!a||busy)return;busy=true;
    try{stateRevision++;S.anchors=await api('saveAnchor',{anchor:{id:a.id,name:a.name,uid:readUid(a),room:a.room,enabled:a.enabled,alarm:!a.alarm}});render();await window.refreshNative(true);}
    catch(e){toast(e.message);}finally{busy=false;}
}
function askDeleteAnchor(){
    const a=(S.anchors||[]).find(x=>x.id===anchorDraft.id)||anchorDraft;
    openModal('删除这位主播？',`<p class="body-copy">将从守候列表移除 ${esc(a.name||'这位主播')}，本机保存的头像与场次记录一并清除。不影响 B 站账号，也不影响其他主播。</p>`,`<div class="sheet-actions"><button class="secondary" data-action="closeModal">取消</button><button class="primary" data-action="confirmDeleteAnchor">删除</button></div>`);
}
async function removeAnchor(){
    const id=anchorDraft&&anchorDraft.id;if(!id)return;
    try{stateRevision++;S.anchors=await api('deleteAnchor',{id});anchorDraft=null;closeModal();render();await window.refreshNative(true);toast('已删除');}
    catch(e){toast(e.message);}
}
let pendingHighWindows=null;
let scheduleDraft=null,schedDraftStart=1200,schedDraftEnd=-1,schedDraftDate='';
const weekDays=['一','二','三','四','五','六','日'];
/** The schedule picture keeps one address per anchor, so a replaced picture would keep arriving
    from the WebView cache; the revision from the native side changes with the stored file. */
function scheduleImageSrc(id,revision){return '/schedule/'+id+'.img?v='+encodeURIComponent(revision||'0');}
function editSchedule(id){
    const a=(S.anchors||[]).find(x=>x.id===id);if(!a)return;
    scheduleDraft={id,entries:clone(a.schedule||[]),text:'',hasImage:!!a.scheduleImage,revision:a.scheduleImageRevision||''};
    schedDraftStart=1200;schedDraftEnd=-1;schedDraftDate='';
    openModal(a.name+' · 日程','<div id="schedule-editor"></div>','<div class="sheet-actions"><button class="secondary outline" data-action="closeModal">取消</button><button class="primary" data-action="saveSchedule">保存日程</button></div>');
    paintScheduleModal();
    api('getSchedule',{id}).then(info=>{
        if(!scheduleDraft||scheduleDraft.id!==id)return;
        scheduleDraft.text=info.text||'';scheduleDraft.hasImage=!!info.hasImage;
        if(info.entries&&info.entries.length&&!scheduleDraft.entries.length)scheduleDraft.entries=clone(info.entries);
        paintScheduleModal();
    }).catch(()=>{});
}
/** The picture branch of the editor only needs the two flags that decide whether and where to
    draw it, so a refresh must not clobber entries the user has added but not saved yet. */
async function refreshSchedulePicture(id){
    const info=await api('getSchedule',{id});
    if(!scheduleDraft||scheduleDraft.id!==id)return;
    scheduleDraft.hasImage=!!info.hasImage;scheduleDraft.revision=info.imageRevision||'';
    paintScheduleModal();
}
/** "每周 3 / 16 · 单次 2 / 24" — the two allowances are separate, so the count says which is which,
    in the same two words the cards and the editor use for the two kinds. */
function draftCounts(){
    const list=(scheduleDraft&&scheduleDraft.entries)||[];
    const weekly=list.filter(e=>!entryDate(e)).length,once=list.length-weekly;
    return '每周重复 '+weekly+' / '+MAX_WEEKLY_ENTRIES+' · 只这一次 '+once+' / '+MAX_ONCE_ENTRIES;
}
function paintScheduleModal(){
    const box=$('#schedule-editor');if(!box||!scheduleDraft)return;
    // Repaints must not wipe what the user is typing; the live value wins over the draft copy.
    const keepText=$('#sched-text')?$('#sched-text').value:null;
    const rows=scheduleDraft.entries.map(e=>{
        const dl=[0,1,2,3,4,5,6].filter(i=>e.days&(1<<i)).map(i=>weekDays[i]).join('、');
        const t=time(e.start)+(e.end>=0?' – '+time(e.end):'');
        const when=entryWhen(e);
        const head=when?when+' · '+t:t;
        const sub=(when?'':('周'+dl+' · '))+leadLabel(e.lead)+(e.note?' · '+esc(e.note):'');
        return '<div class="sched-row"><div class="grow"><div class="sched-kind-row">'+schedKindTag(when)+'<strong>'+esc(head)+'</strong></div><small class="sub">'+sub+'</small></div><button class="text-button" data-sched-del="'+esc(e.id)+'">删除 '+icon('close')+'</button></div>';
    }).join('')||'<p class="sub">还没有安排。用下面逐条添加，或粘贴周表文字识别。</p>';
    box.innerHTML='<div class="section-label"><h2>已有安排</h2><span style="display:flex;gap:10px;align-items:center"><small>'+draftCounts()+'</small>'+(scheduleDraft.entries.length?'<button class="text-button" data-action="clearScheduleDraft">清空全部</button>':'')+'</span></div>'+rows+
    '<div class="section-label"><h2>添加安排</h2><small>某一天</small></div>'+
    '<label><span class="form-label">日期</span><input class="input" type="date" id="sched-date" value="'+esc(schedDraftDate||isoToday())+'"></label>'+
    '<div class="time-inputs" style="margin-top:10px"><label><span class="form-label">开始</span><input class="input" type="time" id="sched-start" value="'+time(schedDraftStart)+'"></label><label><span class="form-label">结束（可空）</span><input class="input" type="time" id="sched-end" value="'+(schedDraftEnd>=0?time(schedDraftEnd):'')+'"></label></div>'+
    '<input class="input" id="sched-note" maxlength="40" placeholder="备注，例如：杂谈 / 游戏回" style="margin-top:10px">'+
    '<div class="setting-row"><div class="grow"><h3>提前提醒</h3><p class="sub">比开始时间早多久发一条静默通知（不响铃）</p></div><select id="sched-lead">'+
    [[0,'默认（5 分钟）'],[5,'提前 5 分钟'],[30,'提前 30 分钟'],[60,'提前 1 小时'],[180,'提前 3 小时']].map(([v,label])=>'<option value="'+v+'">'+label+'</option>').join('')+'</select></div>'+
    '<button class="secondary" data-action="addScheduleEntry" style="margin-top:12px">'+icon('plus')+'添加这条安排</button>'+
    // Manual entry is one thing only: a day. The repeating kind is still there — the parser and the
    // AI make it when the source says「周五」— but asking the user to pick a kind was a question they
    // answered by saying the schedule changes every week anyway.
    '<p class="hint">手动添加记的是<b>某一天</b>的安排（生日会、联动、特别活动）。周表上那种「每周五」的固定安排交给下面的<b>文字识别</b>或<b>AI 识图</b>：识别到「周五 20:00」就是每周五，识别到具体日期就是那一天。</p>'+
    '<div class="section-label"><h2>从文字识别</h2></div>'+
    '<textarea class="input" id="sched-text" rows="4" placeholder="粘贴周表文字，例如：周五 20:00 游戏">'+esc((keepText!==null?keepText:scheduleDraft.text)||'')+'</textarea>'+
    '<button class="secondary" data-action="parseScheduleText" style="margin-top:10px">'+icon('spark')+'识别为安排</button><p class="hint">识别结果只进上面的列表，保存前可逐条修改；也可以上传周表图片对照。</p>'+
    (scheduleDraft.hasImage?'<div class="section-label"><h2>周表图片对照</h2></div><div class="sched-img"><button class="sched-img-open" data-action="viewScheduleImage" data-anchor="'+esc(scheduleDraft.id)+'" aria-label="放大查看周表图片"><img src="'+esc(scheduleImageSrc(scheduleDraft.id,scheduleDraft.revision))+'" alt="周表图片"></button><button class="text-button" data-action="removeScheduleImage" style="margin-top:6px">删除图片 '+icon('close')+'</button></div>':'')+
    '<button class="secondary" data-action="pickScheduleImage" style="margin-top:10px">'+icon('upload')+'上传或更换周表图片</button>'+
    (scheduleDraft.hasImage?'<button class="secondary" data-action="ocrSchedule" style="margin-top:10px">'+icon('spark')+'AI 识别图片安排</button>':'')+
    '<p class="hint">图片显示在列表上方，方便边看边录；只保存在本机，应用不做图片识别。</p>'+
    '<div class="form-error" id="sched-error"></div>';
}
function addScheduleEntry(){
    const error=$('#sched-error');if(error)error.textContent='';
    if(!scheduleDraft)return;
    const start=$('#sched-start').value,end=$('#sched-end').value,note=($('#sched-note').value||'').trim();
    if(!start){if(error)error.textContent='请选择开始时间';return;}
    const date=$('#sched-date')?$('#sched-date').value:'';
    if(!date){if(error)error.textContent='请选择日期';return;}
    const minutes=s=>Number(s.slice(0,2))*60+Number(s.slice(3));
    // Manual entries are always dated; only the entry limit of that kind has to be counted here.
    let once=0;
    for(const item of scheduleDraft.entries)if(entryDate(item))once++;
    if(once>=MAX_ONCE_ENTRIES){if(error)error.textContent='每位主播最多 '+MAX_ONCE_ENTRIES+' 条某一天的日程';return;}
    const lead=Number($('#sched-lead')?$('#sched-lead').value:0)||0;
    scheduleDraft.entries.push({id:'o'+Date.now().toString(36)+once,days:0,date,start:minutes(start),end:end?minutes(end):-1,note,lead});
    paintScheduleModal();
}
function parseScheduleLine(line){
    const map={'一':0,'二':1,'三':2,'四':3,'五':4,'六':5,'日':6,'天':6,'末':-1};
    let days=0,m;const re=/周([一二三四五六日天末])|礼拜([一二三四五六日天末])|星期([一二三四五六日天末])/g;
    while((m=re.exec(line))){const ch=m[1]||m[2]||m[3];
        if(ch==='末'){days|=1<<5;days|=1<<6;}else days|=1<<map[ch];}
    const times=[];const tm=/(\d{1,2})[:：点时](\d{2})?分?/g;
    while((m=tm.exec(line))){const h=Number(m[1]),min=m[2]?Number(m[2]):0;if(h<24&&min<60)times.push(h*60+min);}
    if(!times.length)return null;
    // A line that names a date is about that one day, so the weekday is only a fallback: two sources
    // for the same question is exactly what the store refuses to keep.
    const dateToken=line.match(/\d{4}[\/\-.]\d{1,2}[\/\-.]\d{1,2}|\d{1,2}[\/\-.月]\d{1,2}日?|\d{1,2}号/);
    const date=dateToken?isoDateOf(dateToken[0]):'';
    if(!days&&!date)return null;
    let note=line.replace(re,' ').replace(/\d{4}[\/\-.]\d{1,2}[\/\-.]\d{1,2}|\d{1,2}[\/\-.月]\d{1,2}日?|\d{1,2}号/g,' ').replace(/(\d{1,2})[:：点时](\d{2})?分?/g,' ').replace(/[【\[\]\s:：\-—]+/g,' ').trim();
    const base={start:times[0],end:times.length>1?times[1]:-1,note:note.slice(0,40)};
    return date?Object.assign(base,{days:0,date}):Object.assign(base,{days,date:''});
}
async function parseScheduleIntoDraft(){
    const error=$('#sched-error');if(error)error.textContent='';
    if(!scheduleDraft)return;
    const text=($('#sched-text').value||'').trim();
    if(!text){if(error)error.textContent='请先粘贴周表文字';return;}
    try{
        const source=text;
        const parsed=source.split(/\n+/).map(parseScheduleLine).filter(Boolean);
        if(!parsed.length){if(error)error.textContent='没有识别出「周几或日期 + 时间」的组合，请手动添加或调整文字';return;}
        // Two allowances, so the check counts what is being added by kind, not the bare total.
        let weekly=0,once=0;
        for(const item of scheduleDraft.entries.concat(parsed)){if(entryDate(item))once++;else weekly++;}
        if(weekly>MAX_WEEKLY_ENTRIES||once>MAX_ONCE_ENTRIES){if(error)error.textContent='每位主播最多 '+MAX_WEEKLY_ENTRIES+' 条每周安排、'+MAX_ONCE_ENTRIES+' 条单次日程';return;}
        let n=0;for(const p of parsed){p.id='w'+Date.now().toString(36)+'p'+(n++);scheduleDraft.entries.push(p);}
        scheduleDraft.text=source;
        paintScheduleModal();toast('识别出 '+parsed.length+' 条安排，请确认后保存');
    }catch(e){if(error)error.textContent=e.message;}
}
function parseAiLine(line){
    // The model answers `weekday|start|end|date|note`; the old 4-column shape still parses.
    // A filled date column (2/15, 2026/2/15, 15号) beats the weekday column: one-off
    // schedules land on that weekday, and the date text stays visible in the note.
    const parts=String(line).split('|').map(s=>s.trim());
    if(parts.length<3)return null;
    const minutes=s=>{s=String(s).trim();const c=s.split(':');if(c.length!==2)return -1;
        const h=Number(c[0]),mm=Number(c[1]);
        return Number.isFinite(h)&&Number.isFinite(mm)&&h<24&&mm<60&&c[0].length<=2&&c[1].length===2?h*60+mm:-1;};
    const start=minutes(parts[1]);if(start<0)return null;
    const end=parts[2]?minutes(parts[2]):-1;
    let datePart='',note='';
    if(parts.length>=5){datePart=parts[3];note=parts.slice(4).join('|');}
    else note=parts.slice(3).join('|');
    // The date column is the calendar day itself now, not a hint for picking a weekday: a birthday
    // stream belongs to that day for good. The date is shown on its own card, so the note stays clean.
    const iso=datePart?isoDateOf(datePart):'';
    const d=Number(parts[0]);
    if(!iso&&!(d>=0&&d<=6))return null;
    const noteText=(note||'').slice(0,40);
    return iso?{days:0,date:iso,start,end:end>=0?end:-1,note:noteText}
              :{days:1<<d,date:'',start,end:end>=0?end:-1,note:noteText};
}

async function ocrScheduleIntoDraft(){
    const error=$('#sched-error');if(error)error.textContent='';
    if(!scheduleDraft)return;
    if(S.config.aiOcr===false){toast('请先在设置里开启 AI 图片识别');return;}
    const button=$('#modal [data-action="ocrSchedule"]');
    if(button){button.disabled=true;button.textContent='AI 识别中，约需十几秒…';}
    try{
        // Two AI attempts can legitimately take a while; the Java side caps at ~270 s.
        const text=await api('ocrScheduleImage',{id:scheduleDraft.id},300000);
        const parsed=text.split(String.fromCharCode(10)).map(s=>s.trim()).filter(Boolean).map(parseAiLine).filter(Boolean);
        if(!parsed.length){
            // Quality may be poor, but the user must not walk away empty-handed: the raw
            // answer goes into the paste box for a manual fix and a second pass.
            const box=$('#sched-text');if(box)box.value=text;
            if(error)error.textContent='AI 返回的内容没有解析出安排，原文已放入文本框；可修改后点「识别为安排」';
            return;
        }
        if(scheduleDraft.entries.length+parsed.length>16){if(error)error.textContent='加入后超过 16 条，请先删除部分再识别';return;}
        let n=0;for(const p of parsed){p.id='w'+Date.now().toString(36)+'a'+(n++);scheduleDraft.entries.push(p);}
        paintScheduleModal();toast('AI 识别出 '+parsed.length+' 条安排，请确认后保存');
    }catch(e){if(error)error.textContent=e.message;}
    finally{if(button){button.disabled=false;button.innerHTML=icon('spark')+'AI 识别图片安排';}}
}
async function saveScheduleDraft(){
    const error=$('#sched-error');if(error)error.textContent='';
    if(!scheduleDraft)return;
    try{
        await api('saveSchedule',{id:scheduleDraft.id,entries:scheduleDraft.entries});
        const box=$('#sched-text');if(box)await api('saveScheduleText',{id:scheduleDraft.id,text:box.value});
        closeModal();await window.refreshNative(true);toast('日程已保存');
    }catch(e){if(error)error.textContent=e.message;}
}
/**
 * The uploaded timetable is a large picture of small text, and the one size it cannot be read at
 * is a thumbnail, so tapping it (or 「查看大图」 on the 周表 page) opens a full-screen viewer with
 * pinch, drag and double-tap zoom. One <img> and one CSS transform is all this needs: no library,
 * nothing over the network, and nothing newer than the WebView the app already supports.
 */
let imageViewer={open:false,scale:1,min:1,max:8,x:0,y:0,drag:null,pinch:null,tapped:0,tapX:0,tapY:0};
function imageViewerEls(){return {box:$('#img-viewer'),img:$('#img-viewer-img')};}
/** The scale at which the picture fills the screen's width — the smallest useful zoom. */
function imageViewerFit(){
    const {box,img}=imageViewerEls();
    if(!box||!img||!img.naturalWidth||!box.clientWidth)return 1;
    const fit=box.clientWidth/img.naturalWidth;
    return fit>0?fit:1;
}
function imageViewerApply(){
    const {img}=imageViewerEls();if(!img)return;
    img.style.transform=`translate(${imageViewer.x}px,${imageViewer.y}px) scale(${imageViewer.scale})`;
}
/** Keep the picture reachable: centred while it is smaller than the screen, clamped otherwise. */
function imageViewerClamp(){
    const {box,img}=imageViewerEls();if(!box||!img)return;
    const vw=box.clientWidth,vh=box.clientHeight,w=img.naturalWidth*imageViewer.scale,h=img.naturalHeight*imageViewer.scale;
    imageViewer.x=w<=vw?(vw-w)/2:Math.min(0,Math.max(vw-w,imageViewer.x));
    imageViewer.y=h<=vh?(vh-h)/2:Math.min(0,Math.max(vh-h,imageViewer.y));
}
/** Zoom about a point on screen, so the spot the user is looking at stays where it is. */
function imageViewerZoomTo(scale,cx,cy){
    const before=imageViewer.scale;
    const next=Math.min(imageViewer.max,Math.max(imageViewer.min,scale));
    if(next===before)return;
    imageViewer.x=cx-(cx-imageViewer.x)*(next/before);
    imageViewer.y=cy-(cy-imageViewer.y)*(next/before);
    imageViewer.scale=next;imageViewerClamp();imageViewerApply();
}
function imageViewerFitToScreen(){
    imageViewer.min=imageViewerFit();
    // Eight times the width fit is about one image pixel per device pixel on a phone; a small
    // picture gets a floor of its own so its text can still be enlarged.
    imageViewer.max=Math.max(imageViewer.min*8,1.2);
    imageViewer.scale=imageViewer.min;imageViewer.x=0;imageViewer.y=0;
    imageViewerClamp();imageViewerApply();
}
function imageViewerActualSize(){
    const {box}=imageViewerEls();if(!box)return;
    imageViewerZoomTo(Math.min(1,imageViewer.max),box.clientWidth/2,box.clientHeight/2);
}
/** Double tap: back to fit while zoomed in, otherwise zoom in on the spot that was tapped. */
function imageViewerToggle(){
    const {box}=imageViewerEls();if(!box)return;
    const target=imageViewer.scale>imageViewer.min*1.05?imageViewer.min:Math.min(imageViewer.max,imageViewer.min*3);
    imageViewerZoomTo(target,imageViewer.tapX||box.clientWidth/2,imageViewer.tapY||box.clientHeight/2);
}
function openImageViewer(id){
    const {box,img}=imageViewerEls();if(!box||!img)return;
    const a=(S.anchors||[]).find(x=>x.id===id)||{};
    // Addressed with its revision, exactly like the thumbnail: a replaced picture must not come
    // back out of the WebView's cache as the previous one.
    img.src=scheduleImageSrc(id,a.scheduleImageRevision);
    imageViewer.open=true;box.hidden=false;
    document.body.style.overflow='hidden';
    // Hidden until it has been fitted: a big picture painted first at 1:1 would appear at the wrong
    // size for a frame and then jump, which is the one thing a viewer must not do.
    img.style.opacity='0';
    const reveal=()=>{if(!imageViewer.open)return;imageViewerFitToScreen();img.style.opacity='1';};
    img.onload=reveal;
    // A cached picture may already be complete, in which case onload never fires again.
    if(img.complete&&img.naturalWidth)reveal();else imageViewerApply();
}
function closeImageViewer(){
    const {box,img}=imageViewerEls();if(!box)return;
    imageViewer.open=false;box.hidden=true;
    imageViewer.drag=null;imageViewer.pinch=null;imageViewer.tapped=0;
    document.body.style.overflow='';
    if(img)img.onload=null;
}
function imageViewerTouchStart(event){
    if(!imageViewer.open)return;
    // The controls live inside the overlay: a touch on one of them has to stay a plain tap, or the
    // preventDefault() below would swallow the click that follows it.
    if(event.target&&event.target.closest&&event.target.closest('button'))return;
    if(event.touches.length===1){
        const t=event.touches[0];
        imageViewer.drag={x:t.clientX,y:t.clientY,ox:imageViewer.x,oy:imageViewer.y,moved:0};
        imageViewer.pinch=null;
    }else if(event.touches.length>=2){
        const a=event.touches[0],b=event.touches[1];
        imageViewer.pinch={d:Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY),scale:imageViewer.scale,
            cx:(a.clientX+b.clientX)/2,cy:(a.clientY+b.clientY)/2,x:imageViewer.x,y:imageViewer.y};
        imageViewer.drag=null;
    }
    if(event.cancelable)event.preventDefault();
}
function imageViewerTouchMove(event){
    if(!imageViewer.open)return;
    if(imageViewer.pinch&&event.touches.length>=2){
        const a=event.touches[0],b=event.touches[1];
        const d=Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY);
        const ratio=imageViewer.pinch.d>0?d/imageViewer.pinch.d:1;
        const next=Math.min(imageViewer.max,Math.max(imageViewer.min,imageViewer.pinch.scale*ratio));
        const k=next/imageViewer.pinch.scale;
        imageViewer.scale=next;
        imageViewer.x=imageViewer.pinch.cx-(imageViewer.pinch.cx-imageViewer.pinch.x)*k;
        imageViewer.y=imageViewer.pinch.cy-(imageViewer.pinch.cy-imageViewer.pinch.y)*k;
        imageViewerClamp();imageViewerApply();
    }else if(imageViewer.drag&&event.touches.length===1){
        const t=event.touches[0];
        const dx=t.clientX-imageViewer.drag.x,dy=t.clientY-imageViewer.drag.y;
        imageViewer.drag.moved=Math.max(imageViewer.drag.moved,Math.abs(dx)+Math.abs(dy));
        // A picture that already fits the screen has nowhere to go, and letting it slide just
        // looks broken, so dragging only pans once it is zoomed in.
        if(imageViewer.scale>imageViewer.min){
            imageViewer.x=imageViewer.drag.ox+dx;imageViewer.y=imageViewer.drag.oy+dy;
            imageViewerClamp();imageViewerApply();
        }
    }else return;
    if(event.cancelable)event.preventDefault();
}
function imageViewerTouchEnd(event){
    if(!imageViewer.open)return;
    const drag=imageViewer.drag;
    const moved=drag?drag.moved:99;
    if(event.touches.length===0){
        imageViewer.drag=null;imageViewer.pinch=null;
        if(moved<12){
            const now=Date.now();
            const at=(event.changedTouches&&event.changedTouches[0])||null;
            if(at){imageViewer.tapX=at.clientX;imageViewer.tapY=at.clientY;}
            if(now-imageViewer.tapped<320){imageViewer.tapped=0;imageViewerToggle();return;}
            imageViewer.tapped=now;
        }else imageViewer.tapped=0;
    }else if(event.touches.length===1){
        // One finger of a pinch was lifted: the one still down becomes a drag, from where it is.
        const t=event.touches[0];
        imageViewer.drag={x:t.clientX,y:t.clientY,ox:imageViewer.x,oy:imageViewer.y,moved:99};
        imageViewer.pinch=null;
    }
}
/* Bound once at load: the overlay belongs to the document, not to one page of it. */
(function bindImageViewer(){
    const {box}=imageViewerEls();if(!box)return;
    box.addEventListener('touchstart',imageViewerTouchStart,{passive:false});
    box.addEventListener('touchmove',imageViewerTouchMove,{passive:false});
    box.addEventListener('touchend',imageViewerTouchEnd,{passive:false});
    box.addEventListener('touchcancel',imageViewerTouchEnd,{passive:false});
    box.addEventListener('wheel',event=>{
        if(!imageViewer.open)return;
        if(event.cancelable)event.preventDefault();
        imageViewerZoomTo(imageViewer.scale*(event.deltaY<0?1.15:1/1.15),event.clientX,event.clientY);
    },{passive:false});
})();

/* A broadcaster's live flag is per anchor, so reading it alone painted every row of the week
   as 直播中. Only the arrangement that has already begun today can be the session on air: a
   planned start this near counts as begun, matching PollPlan.SOON_BEFORE_MINUTES. */
const SCHEDULE_SOON_MINUTES=30;
/** Mirrors Schedule.MAX_PER_ANCHOR / MAX_ONCE_PER_ANCHOR: two kinds, two allowances. */
const MAX_WEEKLY_ENTRIES=16, MAX_ONCE_ENTRIES=24;
/** anchorId → entryId of the one arrangement that can be on air now; anchors with no
    arrangement begun today are absent, so a live flag can no longer reach future rows. */
function airingToday(rows,today,isoDay,nowMinutes){
    const airing={},begun={};
    for(const p of rows){
        const e=p.e;
        if(!occursOn(e,today,isoDay)||e.start>nowMinutes+SCHEDULE_SOON_MINUTES)continue;
        if(begun[p.a.id]===undefined||e.start>begun[p.a.id]){begun[p.a.id]=e.start;airing[p.a.id]=e.id;}
    }
    return airing;
}
/**
 * One calendar row, shared by 今天 / 本周 / 即将到来 / 已结束 so an arrangement looks the same
 * wherever it appears. Dated rows carry their date and how early they are announced; weekly rows
 * are left exactly as they were.
 */
function schedCard(pair,live,badge){
    const a=pair.a,e=pair.e;
    const t=time(e.start)+(e.end>=0?' – '+time(e.end):'');
    const when=entryWhen(e);
    const cov=live&&a.snapshot&&a.snapshot.cover?'<img class="sched-cover" src="/cover/?u='+encodeURIComponent(a.snapshot.cover)+'" alt="">':'';
    // No footer: the kind tag and the notice lead said the same thing twice over (a dated row
    // already shows its day, a weekly one sits under its weekday) and the user read them as noise.
    // What is left is what a glance needs — the day rides in the state line, and the lead lives in
    // the editor, where it is set.
    return '<article class="card sched-card'+(when?' once':'')+(a.enabled?'':' disabled-rule')+(badge==='ended'?' sched-ended':'')+'"><div class="sched-head">'+anchorArt(a)+'<div class="grow"><div class="anchor-name">'+esc(a.name)+'</div><div class="sched-meta"><span class="dot '+(live?'live':'')+'"></span><span class="sched-state">'+(badge==='ended'?'已结束':live?'正在直播':'计划开播')+'</span>'+(when?'<span class="sched-badge">'+esc(when)+'</span>':'')+(e.note?'<span class="sched-note">'+esc(e.note)+'</span>':'')+'</div></div><div class="sched-side"><strong class="sched-time">'+t+'</strong>'+cov+'</div></div></article>';
}
/** "每周" or "单次" — the tag that tells the two kinds of arrangement apart, drawn the same way
    on the calendar and inside the editor. `when` is the dated entry's day, null for a weekly one. */
function schedKindTag(when){
    return '<span class="sched-kind '+(when?'once':'weekly')+'">'+icon(when?'calendar':'repeat')+(when?'只这一次':'每周重复')+'</span>';
}
function week(){
    const list=S.anchors||[];
    const now=new Date(S.now||Date.now());
    const today=(now.getDay()+6)%7;
    const nowMinutes=now.getHours()*60+now.getMinutes();
    const todayIso=isoToday(now);
    const rows=list.flatMap(a=>(a.schedule||[]).map(e=>({a,e})));
    const weekly=rows.filter(p=>!entryDate(p.e));
    const dated=rows.filter(p=>entryDate(p.e));
    const upcoming=dated.filter(p=>entryDate(p.e)>=todayIso).sort((x,y)=>entryDate(x.e)===entryDate(y.e)?x.e.start-y.e.start:(entryDate(x.e)<entryDate(y.e)?-1:1));
    const ended=dated.filter(p=>entryDate(p.e)<todayIso).sort((x,y)=>entryDate(x.e)<entryDate(y.e)?1:-1);
    const todayRows=rows.filter(p=>occursOn(p.e,today,todayIso)).sort((x,y)=>x.e.start-y.e.start);
    // The live flag belongs to one arrangement of one anchor: the one already begun today.
    const airing=airingToday(rows,today,todayIso,nowMinutes);
    const live=pair=>!!(pair.a.enabled&&(pair.a.snapshot||{}).status===1&&airing[pair.a.id]===pair.e.id);
    let html=heading('YOUR CALENDAR','今天，和你等的那一天。','带日期的是「只这一次」的安排，只在那一天；其余都按勾选的星期每周重复。是否响铃仍由时段与响铃开关决定。');
    if(!rows.length&&!list.some(a=>a.scheduleImage))html+='<div class="card sched-empty"><div class="row"><div class="label-icon">'+icon('calendar')+'</div><div class="grow"><strong>还没有安排</strong><p class="sub">到“主播”页，在主播卡片上点「日程」添加每周安排或某一天的日程；也可以粘贴文字识别，或上传周表图片。</p></div></div><button class="secondary" data-route="anchors">去主播页添加 '+icon('arrow')+'</button></div>';
    // The cards used to carry a footer with the kind tag and the notice lead; the user read both as
    // noise, so they are gone. The day stays (a dated row is the one that needs it), and this line
    // says the one thing the cards no longer do: where the notice lead is set.
    if(rows.length)html+='<p class="hint" style="margin-top:-8px">'+icon('info')+' 每条安排的<strong>提前提醒</strong>（开播前发一条静默通知，不响铃，默认提前 5 分钟）在<strong>编辑这条安排</strong>时选。</p>';
    if(todayRows.length){
        html+='<div class="section-label"><h2>今天</h2><small>'+todayRows.length+' 场</small></div>';
        html+=todayRows.map(p=>schedCard(p,live(p))).join('');
    }
    for(let off=1;off<7;off++){
        const d=(today+off)%7;
        const dayRows=weekly.filter(p=>p.e.days&(1<<d)).sort((x,y)=>x.e.start-y.e.start);
        if(!dayRows.length)continue;
        html+='<div class="section-label"><h2>周'+weekDays[d]+(off===1?' · 明天':'')+'</h2><small>'+dayRows.length+' 场</small></div>';
        html+=dayRows.map(p=>schedCard(p,false)).join('');
    }
    if(upcoming.length){
        html+='<div class="section-label"><h2>即将到来</h2><small>'+upcoming.length+' 条</small></div>';
        html+=upcoming.map(p=>schedCard(p,live(p))).join('');
    }
    if(ended.length){
        html+='<div class="section-label"><h2>已结束</h2><button class="text-button" data-action="clearEndedSchedule">清理 '+ended.length+' 条 '+icon('close')+'</button></div>';
        html+=ended.slice(0,6).map(p=>schedCard(p,false,'ended')).join('');
        if(ended.length>6)html+='<p class="hint" style="margin:0 0 14px">还有 '+(ended.length-6)+' 条，点上面的「清理」一次移除。</p>';
    }
    const withImg=list.filter(a=>a.scheduleImage);
    // The whole row opens the picture, not a small link inside it: the complaint that started this
    // was not finding a way to enlarge it at all.
    if(withImg.length)html+='<div class="section-label"><h2>周表图片</h2></div>'+withImg.map(a=>'<button class="card img-card" data-action="viewScheduleImage" data-anchor="'+esc(a.id)+'" aria-label="放大查看'+esc(a.name)+'的周表图片"><div class="row">'+anchorArt(a)+'<div class="grow img-card-copy"><strong>'+esc(a.name)+'</strong><small class="sub">上传的周表图片 · 点按放大</small></div><span class="chevron" aria-hidden="true">›</span></div></button>').join('');
    if(rows.length)html+='<p class="hint">'+icon('info')+' 日程由你填写或识别，可能与实际开播不同；开播提醒仍按直播状态实时判断。</p>';
    $('#content').innerHTML=html;
}

/**
 * One rule list, drawn once: the reminder windows and the high-frequency windows are the same kind
 * of rule and have to look and behave the same, so they share this renderer. `target` empty means
 * the reminder list; 'high' means the high-frequency one.
 */
function ruleCards(list,target){
    const attr=target?` data-rule-target="${target}"`:'';
    const high=target==='high';
    return (list||[]).map(w=>{
        // A rule with no weekday bits can never match the clock, and the editor always requires one,
        // so this can only come from a hand-edited or externally written config (the reader skips
        // such a rule entirely, the page still sees it in the raw config). Saying so beats drawing
        // seven empty pills.
        const dead=!w.days;
        const foot=high?'只改变检测快慢':dead?'没有生效的星期 · 不会触发':(w.end===w.start?'持续 24 小时':w.end<w.start?'跨午夜 · 结束在次日':'当天时段');
        return `<article class="card time-card ${high?'high ':''}${w.enabled?'':'disabled-rule'}"><div class="row"><div class="grow"><div class="rule-name">${icon(high?'clock':(w.start>=1080||w.start<360?'moon':'sun'))}${esc(w.name||'自定义时段')}${high?'<span class="rule-tag">高频</span>':''}</div><div class="time-range">${time(w.start)}<span class="time-arrow">—</span>${time(w.end)}</div></div><button class="switch" role="switch" aria-checked="${w.enabled}" aria-label="${esc(w.name)}启用状态" data-rule-toggle="${esc(w.id)}"${attr}></button></div><div class="rule-days">${['一','二','三','四','五','六','日'].map((d,i)=>`<span class="day-pill ${(w.days&(1<<i))?'on':''}">${d}</span>`).join('')}</div><div class="rule-footer"><small class="muted"${dead?' style="color:var(--amber)"':''}>${foot}${!dead&&w.days===127?' · 每天':''}</small><button class="text-button" data-edit-rule="${esc(w.id)}"${attr}>编辑 ${icon('arrow')}</button></div></article>`;
    }).join('');
}
/** The 24-hour bar drawing, shared by the reminder windows and the high-frequency ones. */
function timelineBars(list,allDay=false){
    if(allDay)return '<span style="left:0;width:100%"></span>';
    let bars='';
    for(const w of (list||[]).filter(x=>x.enabled)){
        if(w.end>w.start)bars+=`<span style="left:${w.start/14.4}%;width:${(w.end-w.start)/14.4}%"></span>`;
        else if(w.end===w.start)bars+='<span style="left:0;width:100%"></span>';
        else bars+=`<span style="left:${w.start/14.4}%;width:${(1440-w.start)/14.4}%"></span><span style="left:0;width:${w.end/14.4}%"></span>`;
    }
    return bars;
}
/**
 * What the pace is doing right now, in one sentence. `highInside`, `deviceInUse` and
 * `highNextBoundary` are read from the service, so the wording follows the same rule table the poll
 * loop uses. Two things may lift the pace: a window the user drew, and the phone being in use.
 */
function highFrequencyStatus(){
    const c=S.config,on=c.highFrequency===true,rate=pollLabel(c.pollSeconds),slow=pollLabel(c.lowPollSeconds||120);
    if(!on)return {on:false,line:`关闭中 · 全天每 ${rate}一次`};
    const next=S.highNextBoundary||0;
    if(S.highInside===true)return {on:true,inside:true,line:`高频时段内 · 每 ${rate}一次${next?`，${clock(next)} 起放慢到每 ${slow}一次`:''}`};
    // Outside every window the next boundary is always the START of one, so the in-use line promises
    // that rather than a slow-down it would not keep.
    if(S.deviceInUse===true)return {on:true,inUse:true,line:`手机在用 · 已按每 ${rate}一次检测${next?`；${clock(next)} 起进入高频时段`:''}`};
    return {on:true,inside:false,line:`低频时段 · 每 ${slow}一次${next?`，${clock(next)} 起恢复到每 ${rate}一次`:''}`};
}
/** The home-page card: the switch, the current pace, and the two things that may lift it. */
function highFrequencyCard(){
    const c=S.config,st=highFrequencyStatus(),windows=(c.highWindows||[]).filter(w=>w.enabled),slow=pollLabel(c.lowPollSeconds||120);
    return `<section class="card" data-high-card><div class="row"><div class="label-icon ${st.on?'amber':''}">${icon('clock')}</div><div class="grow"><h3>自动检测节奏</h3><p class="sub">${st.line}</p></div>${sw('highFrequency',st.on,'自动检测节奏')}</div>${st.on?`<div class="chips" style="margin:12px 0 0">${windows.length?windows.map(w=>`<span class="chip flat">${time(w.start)}–${time(w.end)}</span>`).join(''):`<span class="chip flat">没有启用中的高频时段</span>`}</div><div class="row between" style="margin-top:10px"><small class="muted">窗口内 · 手机在用时（亮屏/充电）提速</small><button class="text-button" data-route="schedule">编辑 ${icon('arrow')}</button></div>`:`<p class="hint" style="margin:12px 0 0">开启后：你圈定的高频时段、以及手机在用时（亮屏或充电）都按「检测间隔」检测，其余时间放慢到每 ${slow}一次省电；不改动提醒时段，也不影响响铃。</p>`}</section>`;
}
/** The editable list, on the 时段 page next to the reminder windows. */
function highFrequencySection(){
    const c=S.config,st=highFrequencyStatus(),windows=(c.highWindows||[]).filter(w=>w.enabled),slow=pollLabel(c.lowPollSeconds||120);
    const alreadySlow=Number(c.pollSeconds)>=Number(c.lowPollSeconds||120);
    return `<div class="section-label"><h2>高频时段</h2><small>窗口外放慢到 ${slow}</small></div><section class="card"><div class="row"><div class="label-icon ${st.on?'amber':''}">${icon('clock')}</div><div class="grow"><h3>自动检测节奏</h3><p class="sub">${st.line}</p></div>${sw('highFrequency',st.on,'自动检测节奏')}</div><p class="sub" style="margin-top:12px">开启后：下面的时段、以及<b>手机在用</b>时（亮屏或充电）按「检测间隔」检测，其余时间放慢到每 ${slow}一次省电。只改变<b>多久查一次</b>，不改动上面的提醒时段，也不影响响铃、暂缓与日程预告。一轮只发一次请求，所以守候多位主播不会比一位慢。${alreadySlow?`<br>当前检测间隔已经是每 ${slow}一次或更慢：开启后不会更快，窗口外也不会更慢。`:''}</p><div class="timeline">${timelineBars(c.highWindows)}</div><div class="timeline-ticks"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>24:00</span></div>${st.on&&windows.length===0?`<p class="sub" style="margin-top:12px;color:var(--amber)">没有启用中的高频时段：会一直按每 ${slow}一次检测。启用至少一条，或关掉这个开关。</p>`:''}${setting('低频检测间隔','窗口之外、且手机没在用时多久查一次。放得越慢越省电，代价是那段时间开播会被晚一点发现——想更快就选 30 秒或 1 分钟。',select('lowPollSeconds',c.lowPollSeconds||120,[[30,'每 30 秒'],[60,'每 1 分钟'],[120,'每 2 分钟'],[300,'每 5 分钟']]))}<div class="row between" style="margin-top:12px"><small class="muted" id="high-suggest">按你守候主播的历史开播时段推荐</small><button class="text-button" data-action="suggestHighWindows">按近 30 天推荐 ${icon('spark')}</button></div></section>${ruleCards(c.highWindows,'high')}<button class="add-rule" data-action="addRule" data-rule-target="high">${icon('plus')}添加高频时段</button><div class="section-label"><h2>一键添加</h2><small>高频时段</small></div><div class="chips"><button class="chip" data-preset="highMorning" data-rule-target="high">上午 08:00–12:00</button><button class="chip" data-preset="highAfternoon" data-rule-target="high">下午 14:00–16:00</button><button class="chip" data-preset="highEvening" data-rule-target="high">晚间 20:00–00:00</button></div>`;
}
function schedule(){
    const c=S.config,n=c.windows.filter(x=>x.enabled).length,bars=timelineBars(c.windows,c.allDay);
    let html=heading('YOUR QUIET HOURS','只在你选的时段里守候。','时段之外不检测、不联网，也不响铃。')+`<div class="segmented"><button data-all-day="true" class="${c.allDay?'selected':''}">全天提醒</button><button data-all-day="false" class="${!c.allDay?'selected':''}">自定义时段</button></div><section class="card"><div class="row between"><h3>24 小时时间轴</h3><small class="muted">${c.allDay?'全天开启':'选定时段的并集'}</small></div><div class="timeline">${bars}</div><div class="timeline-ticks"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>24:00</span></div><p class="sub" style="margin-top:12px">${c.allDay?'任何时间开播都可提醒。下方时段在切换到自定义模式后生效。':'按每条规则所选星期重复；重叠时段合并生效。<strong>时段之外守候会自动暂停</strong>，不检测开播、不联网。'}</p></section>${!c.allDay&&n===0?`<div class="card warning"><p>现在是自定义时段，但<strong>没有任何启用中的时段</strong>：守候会一直暂停，不会检测开播，也不会响铃。</p><button class="text-button" data-all-day="true">改为全天提醒 ${icon('arrow')}</button></div>`:''}<div class="section-label"><h2>我的提醒时段</h2><small>${c.windows.length} / 32</small></div>`;
    html+=ruleCards(c.windows,'');

    html+=`<button class="add-rule" data-action="addRule">${icon('plus')}添加提醒时段</button><div class="section-label"><h2>一键添加</h2><small>提醒时段</small></div><div class="chips"><button class="chip" data-preset="night">凌晨 01:00–06:00</button><button class="chip" data-preset="overnight">深夜 23:00–07:00</button><button class="chip" data-preset="evening">晚间 19:00–23:00</button></div>${highFrequencySection()}<div class="section-label"><h2>提醒范围与预告</h2><small>时区与补报</small></div><section class="card">${setting('提醒时区',`当前：${esc(S.zone)}`,'<button class="text-button" data-action="chooseTimezone">选择时区 ›</button>')}${setting('补报已开播','开启守候或进入时段时，若已经在播也提醒；本场仍只自动提醒一次。',sw('catchUp',c.catchUp,'补报已开播'))}${setting('按日程预告提醒','日程里安排的开播时间，默认在开播前 5 分钟发一条静默通知（每条可单独设置提前量），不响铃；仅对已开启响铃的主播生效。',sw('preStream',c.preStream!==false,'按日程预告提醒'))}</section><p class="hint">${icon('info')} 关闭补报时，仅提醒在所选时段内开始的直播。起点包含，终点不包含；跨午夜时段按开始那天的星期计算。<strong>时段之外不进行任何检测</strong>（不联网、不判读直播状态），进入时段时自动恢复。若接口缺少开播时间，则按首次检测时间判断；首次开启时不猜测已在播的开播时间。</p>`;
    $('#content').innerHTML=html;
}
function sound(){const c=S.config;let html=heading('MAKE IT YOURS','这一声，为你而响。','选喜欢的铃声，也选一个能听见的音量。所有主播共用。');
    html+=quietSection();
    html+=`<section class="card"><div class="volume-header"><div><h3>响铃音量</h3><p class="sub">使用系统闹钟音量通道</p></div><div class="volume-number" id="volume-value">${c.volume}<span>%</span></div></div><input id="volume" aria-label="响铃音量百分比" class="volume-track" type="range" min="1" max="100" value="${c.volume}"><div class="volume-labels"><span>轻一些</span><span>响一些</span></div></section><div class="section-label"><h2>选择铃声</h2><small>内置原创音色</small></div><div class="tones">${[['starlight','spark','星铃','明亮清脆 · 默认'],['morning','sun','清晨','柔和起音 · 温暖'],['urgent','bell','强提醒','节奏明显 · 更醒神'],['system','music','系统闹钟','使用手机默认铃声']].map(([id,i,name,desc])=>`<button class="tone ${c.ringtone===id?'selected':''}" data-tone="${id}" aria-pressed="${c.ringtone===id}">${icon(i)}<div class="tone-copy"><strong>${name}</strong><small>${desc}</small></div></button>`).join('')}</div><button class="audio-file" data-action="pickAudio"><div class="label-icon">${icon('file')}</div><div class="grow"><strong>从手机选择铃声 ${c.ringtone==='custom'?'✓':''}</strong><small>${esc(c.customName==='未选择'?'MP3、M4A、OGG、WAV · 30 MB 以内':c.customName)}</small></div>${icon('plus')}</button>`;
    if(c.customName!=='未选择'&&c.ringtone!=='custom')html+=`<button class="secondary" data-tone="custom" style="margin-bottom:16px">使用上次导入的铃声</button>`;
    html+=`<section class="card">${setting('渐强响铃','从轻到响，约 5 秒达到设定音量。',sw('ramp',c.ramp,'渐强响铃'))}${setting('同时振动','按节奏循环振动，直到响铃结束。',sw('vibrate',c.vibrate,'响铃振动'))}${setting('自动停止','避免错过后长时间持续响铃。',select('duration',c.duration,[[15,'15 秒'],[30,'30 秒'],[60,'1 分钟'],[120,'2 分钟'],[300,'5 分钟']]))}${setting('暂缓多久','点击“稍后提醒”后再次确认直播。',select('snoozeMinutes',c.snoozeMinutes,[[3,'3 分钟'],[5,'5 分钟'],[10,'10 分钟'],[15,'15 分钟']]))}${setting('多主播接续响铃','第一位响铃结束后，期间开播的其他主播按顺序接续提醒；关闭则只记录不补响。',sw('ringQueue',c.ringQueue===true,'多主播接续响铃'))}${setting('通话时不强响铃','检测到通话模式时，只显示通知并按设置振动。',sw('quietCalls',c.quietCalls,'通话时不强响铃'))}</section><button class="primary" data-action="testDialog">${icon('play')}试一试这声铃响</button><p class="hint">响铃期间临时调整闹钟音量；结束后恢复。若你在响铃时手动改变系统音量，应用保留你的调整。静音、勿扰及耳机的具体表现，请通过锁屏测试确认。</p>`;
    $('#content').innerHTML=html;
}
function settings(){const c=S.config,p=S.permissions,rows=[['notifications','bell','允许通知',p.notifications?'系统已允许发送通知':p.notificationPolicy==='revoked'?'系统策略拒绝授权，点按查看排查方法':p.notificationMismatch?'授权状态尚未一致，请打开系统通知设置检查':p.notificationRuntime===false?'请允许系统通知授权，并开启应用通知':'接收开播提醒与守候状态',p.notifications],['alarmChannel','sound','开播强提醒通道','请保留横幅与锁屏显示',p.alarmChannel],['fullScreen','screen','全屏提醒','锁屏时显示可操作的提醒页面',p.fullScreen],['battery','battery','后台电池权限','允许后台运行，减少检测中断',p.battery],['exact','clock','精确闹钟','用于时段边界、暂缓与锁屏测试',p.exact],['accessibility','shield','无障碍守候辅助（可选）',p.accessibilityConnected?'已连接；系统清理本应用后由系统重新拉起并恢复守候':'不读取屏幕；从最近任务划掉后恢复最快的一条路',p.accessibility],['dnd','moon','勿扰模式','如使用勿扰，请在系统中允许闹钟',!p.dnd]];
    let html=heading('A RELIABLE LITTLE WATCH','把每次提醒，照顾好。','权限是否允许，由你的手机最终决定。')+`<div class="section-label"><h2>权限检查</h2><small>点按可进入系统设置</small></div><section class="card permissions-card">${rows.map(([key,i,name,desc,ok])=>`<button class="permission-row" data-permission="${key}">${icon(i)}<span class="grow"><strong>${name}</strong><small>${desc}</small></span><span class="badge ${ok?'':'warn'}">${key==='dnd'?(ok?'未开启':'请检查'):(ok?'已就绪':key==='notifications'&&p.notificationPolicy==='revoked'?'策略限制':'去设置')}</span><span class="chevron">›</span></button>`).join('')}</section><div class="permission-actions"><button class="text-button" data-permission="notificationSettings">打开系统通知设置 ${icon('external')}</button><button class="text-button" data-action="recheckPermissions">重新检查 ${icon('refresh')}</button></div><section class="card trouble"><div class="row"><div class="label-icon amber">${icon('battery')}</div><div class="grow"><h3>检测停了？按顺序查这四项</h3><p class="sub">后台被系统掐掉时，记录页会出现「暂时无法确认直播状态 · 暂时无法解析服务器地址」，而打开应用手动检测一切正常。按顺序逐项确认，多半是第 1、2 项。</p></div></div><ol class="trouble-list"><li><span class="trouble-n">1</span><div class="grow"><strong>电池策略：不限制</strong><p class="sub">设置 → 电池 → 应用耗电管理 → VR闹钟 →「不受限制」。华为 / 荣耀再加一步：设置 → 电池 → 更多电池设置 → 打开「休眠时始终保持网络连接」，否则锁屏睡一会儿就联不上网。</p></div><button class="text-button" data-permission-jump="battery">去设置</button></li><li><span class="trouble-n">2</span><div class="grow"><strong>自启动与「自动管理」</strong><p class="sub">设置 → 应用和服务 → 应用启动管理 → 找到 VR闹钟 → 关闭「自动管理」，勾选允许自启动、允许关联启动、允许后台活动。荣耀 MagicOS 同路径；小米 / Redmi 还要在最近任务里长按本应用卡片锁定。</p></div><button class="text-button" data-permission-jump="app">去设置</button></li><li><span class="trouble-n">3</span><div class="grow"><strong>精确闹钟</strong><p class="sub">配合「持续高频守候」使用：手机休眠时只有闹钟级唤醒不会被推迟，其他唤醒最少被拖到 9 分钟以后。</p></div><button class="text-button" data-permission-jump="exact">去设置</button></li><li><span class="trouble-n">4</span><div class="grow"><strong>通知与勿扰</strong><p class="sub">允许通知、保留「开播强提醒」通道；用了勿扰模式时，在系统里允许闹钟类提醒穿透。</p></div><button class="text-button" data-permission-jump="notifications">去设置</button></li></ol><p class="hint">四项都开了仍然只报 DNS：检查是否开了「私人 DNS」，或 VPN / 广告拦截的分应用代理把本应用排除在外；再把 Wi-Fi 和移动数据各试一次。</p></section><div class="section-label"><h2>AI 图片识别（周表）</h2></div><section class="card">${setting('AI 识别周表图片','上传周表图片后可让 AI 自动提取安排；图片会上传到你配置的 AI 服务商处理，默认开启，可随时关闭。',sw('aiOcr',c.aiOcr!==false,'AI 图片识别'))}<div class="setting-row"><div class="grow"><h3>接口密钥</h3><p class="sub">DeepSeek 兼容接口；仅保存在本机</p></div></div><input class="input" data-setting="aiKey" value="${esc(c.aiKey||'')}" placeholder="sk-…" style="margin-top:6px"><div class="setting-row"><div class="grow"><h3>模型</h3><p class="sub">需要支持图片输入，例如 deepseek-flash</p></div></div><input class="input" data-setting="aiModel" value="${esc(c.aiModel||'deepseek-flash')}" style="margin-top:6px"></section><div class="section-label"><h2>小米与通知兼容</h2></div><section class="card">${setting('通知异常时仍响铃','通知未获准时，继续按时段检测并播放闹铃。通知栏与锁屏卡片可能不显示。',sw('soundWithoutNotifications',c.soundWithoutNotifications,'通知异常时仍响铃'))}<button class="permission-row" data-permission="overlay">${icon('screen')}<span class="grow"><strong>悬浮关闭按钮 · 可选</strong><small>仅响铃时显示，方便在其他应用上关闭闹铃。</small></span><span class="badge ${p.overlay?'':'warn'}">${p.overlay?'已就绪':'去设置'}</span></button></section><div class="section-label"><h2>后台守候</h2></div>${watchNoticeCard()}<section class="card">${setting('优先保证提醒','提醒时段内保持持续检测、握着手机的唤醒锁，更耗电；时段之外（靠「考子」撑着的那些小时）不会为它保持唤醒，改由闹钟按点叫醒，手机能睡。长时间守候建议接通电源。',sw('reliable',c.reliable,'优先保证提醒'))}${setting('检测间隔','只在提醒时段内按此间隔检测，时段之外守候暂停；开了「考子闹钟」的主播会让守候不停，但那些小时按「低频检测间隔」慢查，不会拿这个快档整夜查。网络异常会逐步放慢重试。开启「自动检测节奏」后，只有高频时段内或手机在用时用这个间隔，其余时间放慢省电。一轮只发一次请求，守候多位主播不会比一位慢，也不会更吵 B 站。',select('pollSeconds',c.pollSeconds,[[15,'15 秒'],[30,'30 秒'],[60,'60 秒'],[120,'120 秒']]))}${setting('持续高频守候','提醒时段内保持唤醒锁、一直按上面的间隔检查；手机休眠时用「闹钟式唤醒」按点叫醒（系统会把普通唤醒推迟到约 9 分钟，这种方式不会）。这是最不容易漏开播的设置；代价是更耗电，状态栏会出现闹钟图标，且需要「精确闹钟」权限。时段之外守候本来就暂停，不受影响：开了「考子」的主播照常守着，但那些小时按「低频检测间隔」慢查、靠闹钟按点叫醒，不会整夜快查。',sw('turbo',c.turbo!==false,'持续高频守候'))}${setting('重启后恢复守候','仅恢复此前已开启的守候；仍受系统自启动限制。',sw('boot',c.boot,'重启后恢复守候'))}${setting('定时恢复检查','约每 15 分钟确认守候服务是否还活着，中断时自动拉起；不影响正常检测节奏，系统休眠时可能推迟。时段外守候本就暂停，这项检查也一并停下，等进入时段再恢复。',sw('recovery',c.recovery!==false,'定时恢复检查'))}${S.recoveryAt?`<p class="sub" style="padding:0 4px 10px;margin-top:-8px">已安排恢复检查 · ${stamp(S.recoveryAt)}</p>`:''}${setting('从最近任务隐藏缩略图','最近任务卡片里隐藏应用内容，减少误划与窥屏；不影响提醒功能。',sw('hideRecents',c.hideRecents,'隐藏最近任务缩略图'))}</section><div class="section-label"><h2>开播那一刻</h2><small>响铃之后做什么</small></div><section class="card">${setting('开播后自动进入直播间','响铃后 5 秒自动打开该主播的直播间并结束响铃，页面上会显示倒计时，随时可以点「取消」。仅在屏幕点亮时生效。',sw('autoOpenLive',c.autoOpenLive===true,'开播后自动进入直播间'))}${setting('直播结束时告诉我','本场直播结束时发一条静默通知，不响铃。',sw('endNotice',c.endNotice===true,'直播结束时告诉我'))}</section><div class="section-label"><h2>舰长红包提醒</h2><small>试行版</small></div><section class="card"><div class="setting-row"><div class="grow"><h3>什么是舰长红包</h3><p class="sub">别人掏钱发的 0.1 折上舰红包：抢到就能用很低的价钱上舰，谁都能抢，一般几分钟就被抢光；礼物红包和电池红包不会打扰你。</p></div></div><div class="setting-row"><div class="grow"><h3>在哪些房间监测</h3><p class="sub">按房间逐个开启：到「主播」页编辑该主播，打开「舰长红包提醒」。房间开播、且你在守候时段里时保持一条弹幕连接（心跳约 30 秒一次），房间下播或时段结束就断开。</p></div></div><div class="setting-row"><div class="grow"><h3>提醒的时机</h3><p class="sub">红包广播一到就提醒，同一个红包只提醒一次；点通知直接进直播间。这是试行版：能不能收到以你真机为准，编辑主播页里有「试连一次弹幕」。</p></div></div><p class="hint" style="margin:10px 0 0">红包提醒和开播响铃互不影响：它走自己的通知通道（用系统的通知声音），不会触发全屏闹铃，也不改变「同一场只响一次」的规则。只做提醒，不做自动抢。</p></section><div class="section-label"><h2>主题与外观</h2><small>明暗、主题色与背景</small></div><section class="card">${setting('界面主题','选择舒服的明暗；「跟随系统」会随手机的深色模式实时变化。',select('theme',c.theme,[['light','奶白'],['dark','夜色'],['system','跟随系统']]))}${setting('AMOLED 纯黑模式','仅「夜色」主题生效：底色改为纯黑。奶白主题下此项不生效。',sw('amoled',c.amoled,'AMOLED 纯黑模式'))}<div class="setting-row"><div class="grow"><h3>主题色</h3><p class="sub">${c.seedColor?'当前 '+esc(c.seedColor):'使用内置蓝灰配色'}</p></div></div><div class="swatches">${[['','#516b82'],['#A65C83',seedSwatch('#A65C83',c)],['#6D7DB4',seedSwatch('#6D7DB4',c)],['#4F7FA4',seedSwatch('#4F7FA4',c)],['#447A6A',seedSwatch('#447A6A',c)],['#88743C',seedSwatch('#88743C',c)],['#B36A46',seedSwatch('#B36A46',c)],['#9865AB',seedSwatch('#9865AB',c)],['#B85872',seedSwatch('#B85872',c)],['#536B81',seedSwatch('#536B81',c)],['#6D7650',seedSwatch('#6D7650',c)],['#B2748C',seedSwatch('#B2748C',c)],['#655C74',seedSwatch('#655C74',c)]].map(([hex,bg])=>`<button class="swatch ${(c.seedColor||'')===hex?'selected':''}" style="background:${bg}" aria-label="主题色 ${hex||'默认'}" data-seed="${hex}">${(c.seedColor||'')===hex?'✓':''}</button>`).join('')}</div><input class="input" data-setting="seedColor" value="${esc(c.seedColor||'')}" placeholder="留空使用默认，或输入 #536B81" style="margin-top:10px"><p class="hint" style="margin:6px 0 0">圆点已按当前主题显示实际生效的颜色。</p></section>${petSection()}<div class="section-label"><h2>背景图片</h2><small>只读取你选择的图片，不需要访问整个相册</small></div><section class="card"><div class="setting-row"><div class="grow"><h3>${S.backgroundSet?'已设置背景':'还没有设置背景'}</h3><p class="sub">${S.backgroundSet?esc(S.backgroundName||'自选背景'):'选择一张喜欢的图片作为应用背景'}</p></div></div><div class="bg-actions"><button class="secondary" data-action="pickBackground">${S.backgroundSet?'更换图片':'选择图片'}</button>${S.backgroundSet?'<button class="secondary outline" data-action="removeBackground">移除背景</button>':''}</div><div class="slider-row"><div class="grow"><h3>背景遮罩</h3><p class="sub">压暗背景，保证文字可读</p></div><span class="muted">${c.backgroundDim||0}%</span></div><input aria-label="背景遮罩百分比" class="volume-track" type="range" min="0" max="90" value="${c.backgroundDim||0}" data-setting="backgroundDim" style="margin:8px 0 2px"><div class="slider-row"><div class="grow"><h3>卡片不透明度</h3><p class="sub">越低透出的背景越多</p></div><span class="muted">${c.cardOpacity||94}%</span></div><input aria-label="卡片不透明度百分比" class="volume-track" type="range" min="75" max="100" value="${c.cardOpacity||94}" data-setting="cardOpacity" style="margin:8px 0 2px"><p class="hint" style="margin:8px 0 0">支持 JPG、PNG、WebP，最大 20 MB。图片复制并缩放后只保存在应用内；备份不包含背景图片。</p></section><div class="section-label"><h2>数据与帮助</h2></div><section class="card" style="padding-top:5px;padding-bottom:5px">${[['notificationHelp','bell','通知授权帮助'],['notificationReport','download','导出通知排查包'],['testDialog','shield','锁屏与响铃测试'],['export','download','导出设置与诊断记录'],['import','upload','从备份恢复设置'],['privacy','info','隐私与使用说明']].map(([action,i,label])=>`<button class="plain-row" data-action="${action}">${icon(i)}<span>${label}</span><span class="chevron">›</span></button>`).join('')}</section><p class="footnote">VR闹钟 ${esc(S.version)} · 本机守候 ${enabledAnchors().length} 位主播<br>主播可在“主播”页随时增删或关闭<br>非哔哩哔哩或主播官方应用</p>`;
    if(p.powerSave)html=`<div class="card warning"><p>手机当前处于省电模式，后台提醒可能延迟。</p></div>`+html;
    $('#content').innerHTML=html;
}
// 记录页是唯一「数据要过桥」的页面，所以不能先写空壳、等回包再填列表：那样中间会真实绘制
// 一次「列表是空的」的画面，而手机上要拼最多 200 条记录，这一帧长到能看见 —— 那一下白闪就是它。
// 现在先读再画，并把已读到的记录留在内存里：进入页面只画一次，之后的刷新只替换列表、且只在
// 记录真的变了时才替换。
let historyRows=null,historyListMarkup='';
function historyEntryMarkup(e){return `<article class="history-entry"><div class="label-icon ${e.type==='live'?'green':e.type==='warning'?'amber':''}">${icon(e.type==='live'?'bell':e.type==='warning'?'info':e.type==='snooze'?'clock':e.type==='test'?'sound':'history')}</div><div class="grow"><h3>${esc(e.title)}</h3><p>${esc(e.detail)}</p><time>${stamp(e.at,true)}</time></div></article>`;}
function historyListMarkupOf(list){return list.length?list.map(historyEntryMarkup).join(''):`<div class="empty"><div class="label-icon">${icon('history')}</div><h2>故事还没开始</h2><p>开启守候或完成一次响铃测试后，<br>这里就会留下记录。</p></div>`;}
function historyShell(){return heading('EVERY LITTLE MOMENT','每一声，都有记录。','最近 200 条记录仅保存在你的手机。')+`<div class="section-label"><h2>通知与运行记录</h2><button class="text-button" data-action="clearHistory">清空</button></div><div id="history-list" class="card">${historyListMarkup}</div>`;}
/** 清空记录后必须丢掉缓存，否则会先把刚删掉的那些记录画出来再刷新。 */
function forgetHistory(){historyRows=null;historyListMarkup='';}
async function history(){
    const parent=$('#content');
    if(historyRows===null){
        // 第一次进入：先读，再一次性画出整页（包括列表），不经过「列表还是空的」那一帧。
        try{historyRows=await api('history');}catch(e){historyRows=[];toast(e.message);}
        if(route!=='history')return;
        historyListMarkup=historyListMarkupOf(historyRows);
        parent.innerHTML=historyShell();
        return;
    }
    // 再次进入或重绘：直接用已读到的内容铺满，避免闪；随后静默刷新，只替换列表，且只在内容
    // 真的变了时才写 DOM。
    parent.innerHTML=historyShell();
    try{
        const list=await api('history');
        if(route!=='history')return;
        historyRows=list;
        const markup=historyListMarkupOf(list);
        if(markup===historyListMarkup)return;
        historyListMarkup=markup;
        const box=$('#history-list');if(box)box.innerHTML=markup;
    }catch(ignored){}
}
function openModal(title,body,actions=''){const m=$('#modal');m.innerHTML=`<section class="sheet" role="dialog" aria-modal="true" aria-label="${esc(title)}"><div class="sheet-head"><h2>${esc(title)}</h2><button data-action="closeModal" aria-label="关闭弹窗">${icon('close')}</button></div>${body}${actions}</section>`;m.hidden=false;document.body.style.overflow='hidden';m.querySelector('button')?.focus();}
function closeModal(){const m=$('#modal');if(!m)return;m.hidden=true;m.innerHTML='';document.body.style.overflow='';ruleDraft=null;ruleTarget='';anchorDraft=null;}
function ruleListKey(target){return target==='high'?'highWindows':'windows';}
/** Presets for both lists: the reminder windows, then the three high-frequency hours. */
const RULE_PRESETS={night:['凌晨守候',60,360],overnight:['深夜守候',1380,420],evening:['晚间守候',1140,1380],
    highMorning:['上午高频',480,720],highAfternoon:['下午高频',840,960],highEvening:['晚间高频',1200,0]};
/**
 * The one editor for both rule lists. They are the same kind of rule, so they get the same form,
 * the same weekday picker and the same validation; only the wording and the list they save into
 * differ. `target` is 'high' for the high-frequency windows and empty for the reminder ones.
 */
function editRule(id,preset,target){
    ruleTarget=target==='high'?'high':'';
    const high=ruleTarget==='high';
    const existing=(S.config[ruleListKey(ruleTarget)]||[]).find(w=>w.id===id);
    ruleDraft=existing?clone(existing):high?{id:'h'+Date.now(),name:'高频时段',start:480,end:720,days:127,enabled:true,date:'',lead:0}
                                                   :{id:'r'+Date.now(),name:'自定义时段',start:60,end:360,days:127,enabled:true,date:'',lead:0};
    if(preset&&RULE_PRESETS[preset]){const [name,start,end]=RULE_PRESETS[preset];Object.assign(ruleDraft,{name,start,end});}
    const w=ruleDraft;
    const hint=high
        ?'结束早于开始，自动跨到次日（例如 20:00–00:00 就是晚间）。这些时段内按「检测间隔」检测，其余时间放慢省电；不影响提醒时段与响铃。'
        :'每周重复，按开始那天的星期计算。结束早于开始会自动跨到次日；开始与结束相同表示持续 24 小时。保存后会切换为自定义模式。';
    const what=high?'高频时段':'提醒时段';
    // A watch window is a weekly recurrence, and only a weekly one: the store keeps no day for a
    // window (Prefs.cleanWindows copies id/name/start/end/days/enabled and nothing else), and
    // TimeRules.Window refuses a window with no weekday at all, so a "just this day" rule could
    // never be saved — the bridge would answer with its own error and the user would only see a red
    // line. The one-off kind lives in the calendar, where the store does keep the date.
    const daysWrap=`<div class="form-label">重复星期（按开始那天计算）</div><div class="weekday-picker">${['一','二','三','四','五','六','日'].map((d,i)=>`<button data-weekday="${i}" class="${w.days&(1<<i)?'selected':''}" aria-pressed="${!!(w.days&(1<<i))}" aria-label="星期${d}">${d}</button>`).join('')}</div>`;
    openModal((existing?'编辑':'添加')+what,`<label class="form-label" for="rule-name">时段名称</label><input class="input" id="rule-name" maxlength="24" value="${esc(w.name)}"><div class="time-inputs"><label><span class="form-label">开始时间</span><input class="input" type="time" id="rule-start" value="${time(w.start)}"></label><label><span class="form-label">结束时间</span><input class="input" type="time" id="rule-end" value="${time(w.end)}"></label></div>${daysWrap}<p class="hint">${hint}</p><div class="form-error" id="rule-error"></div>`,`<div class="sheet-actions">${existing?`<button class="secondary outline" data-action="deleteRule">删除${what}</button>`:'<button class="secondary outline" data-action="closeModal">取消</button>'}<button class="primary" data-action="saveRule">保存${what}</button></div>`);
}
async function save(patch,notify=false){
    const c=await api('save',patch);
    // Paint the committed result even when an older state request is in flight.
    stateRevision++;S.config=c;configFingerprint=JSON.stringify(c);render();
    await window.refreshNative(true);if(notify)toast('已保存');
}
async function saveRule(){
    const error=$('#rule-error');
    try{
        const start=$('#rule-start').value,end=$('#rule-end').value,name=$('#rule-name').value.trim();
        if(!start||!end)throw new Error('请选择开始和结束时间');
        if(!ruleDraft.days)throw new Error('请至少选择一个星期');
        const minutes=s=>Number(s.slice(0,2))*60+Number(s.slice(3));
        const high=ruleTarget==='high';
        const what=high?'高频时段':'提醒时段';
        // date is cleared deliberately: a window cannot carry a day (see editRule), and a leftover
        // one from an older build must not travel back into the store.
        const w={...ruleDraft,name:name||(high?'高频时段':'自定义时段'),start:minutes(start),end:minutes(end),date:'',days:ruleDraft.days};
        const rules=(S.config[ruleListKey(ruleTarget)]||[]).filter(x=>x.id!==w.id);rules.push(w);
        if(high){if(rules.length>32)throw new Error('最多支持 32 个时段');}
        else{
            // Two kinds, two allowances: a calendar should not push the weekly table out of its slots.
            let weekly=0,once=0;
            for(const item of rules){if(entryDate(item))once++;else weekly++;}
            if(weekly>MAX_WEEKLY_ENTRIES)throw new Error('每位主播最多 '+MAX_WEEKLY_ENTRIES+' 条每周安排');
            if(once>MAX_ONCE_ENTRIES)throw new Error('每位主播最多 '+MAX_ONCE_ENTRIES+' 条单次日程');
        }
        // Editing the reminder list switches the app into custom mode, which is the mode the user is
        // looking at; the high-frequency list only changes the pace and no mode at all.
        await save(high?{highWindows:rules}:{windows:rules,allDay:false});
        closeModal();toast(what+'已保存');
    }catch(e){error.textContent=e.message;}
}
const zoneNames={'Asia/Shanghai':'中国 · 北京 / 上海','Asia/Tokyo':'日本 · 东京','Asia/Seoul':'韩国 · 首尔','Asia/Hong_Kong':'中国 · 香港','Asia/Taipei':'中国 · 台北','Asia/Singapore':'新加坡','Asia/Kathmandu':'尼泊尔 · 加德满都','Asia/Kolkata':'印度 · 加尔各答','Asia/Dubai':'阿联酋 · 迪拜','Asia/Bangkok':'泰国 · 曼谷','America/New_York':'美国 · 纽约 / 东部','America/Chicago':'美国 · 芝加哥 / 中部','America/Denver':'美国 · 丹佛 / 山地','America/Los_Angeles':'美国 · 洛杉矶 / 太平洋','America/Toronto':'加拿大 · 多伦多','America/Vancouver':'加拿大 · 温哥华','America/Sao_Paulo':'巴西 · 圣保罗','Europe/London':'英国 · 伦敦','Europe/Paris':'法国 · 巴黎','Europe/Berlin':'德国 · 柏林','Europe/Moscow':'俄罗斯 · 莫斯科','Australia/Sydney':'澳大利亚 · 悉尼','Australia/Perth':'澳大利亚 · 珀斯','Pacific/Auckland':'新西兰 · 奥克兰','UTC':'协调世界时'};
async function chooseTimezone(){
 zoneList=await api('zones');openModal('全球时区',`<p class="body-copy">提醒范围按所选地区当地时间计算，自动适应该地区夏令时。跟随手机会在旅行或手机时区改变时自动调整。</p><input class="input" style="margin:16px 0" id="tz-search" type="search" placeholder="搜索国家、城市或时区，如 东京 / New_York" aria-label="搜索全球时区"><div id="timezone-list"></div>`);renderZones('');
}
function renderZones(query){
 const q=query.trim().toLowerCase();const rows=zoneList.filter(z=>(z.id+' '+(zoneNames[z.id]||'')+' UTC'+z.offset).toLowerCase().includes(q));rows.sort((a,b)=>(zoneNames[a.id]?0:1)-(zoneNames[b.id]?0:1)||a.id.localeCompare(b.id));
 const device=`<button class="timezone-row" data-zone="device"><span class="grow"><strong>跟随手机时区</strong><small>${esc(S.deviceZone)}</small></span>${S.config.timezone==='device'?icon('check'):icon('refresh')}</button>`;
 $('#timezone-list').innerHTML=(!q?device:'')+rows.map(z=>`<button class="timezone-row" data-zone="${esc(z.id)}"><span class="grow"><strong>${esc(zoneNames[z.id]||z.id.replace(/_/g,' '))}</strong><small>${esc(z.id)}${z.offset?' · UTC'+esc(z.offset):''}</small></span><span class="muted small">${esc(z.time)}</span>${S.config.timezone===z.id?icon('check'):''}</button>`).join('')+(rows.length?'':'<p class="hint">没有找到，请试试英文城市名，如 London。</p>');
}
function accessibilityInfo(){openModal('可选的守候辅助',`<div class="body-copy"><p>开启后，当你已打开守候而后台服务中断时，会尝试恢复服务。主动停止守候后不会重新启动。</p><p><strong>从最近任务划掉应用后，它是恢复最快的一条路。</strong>系统会停止被划掉应用的前台服务和闹钟，但无障碍服务由系统自己重新绑定，所以系统清理本应用后仍会把它拉起来，随后由它恢复守候。自启动、电池不限制与最近任务锁定仍然是前提。</p><p><strong>不读取屏幕或聊天内容，不执行点击、手势、截屏或按键操作。</strong>只检查本应用的运行情况。</p><p>它不能替代通知、电池、自启动权限，也不能保证突破系统强制停止。部分安卓版本对手动安装应用的无障碍开关有“受限设置”保护：请确认安装包来源，再在应用系统设置中按手机指引处理。</p><p>你可以随时在系统无障碍设置中关闭“VR闹钟 · 守候辅助”。</p></div>`,`<div class="sheet-actions"><button class="secondary" data-action="closeModal">暂不开启</button><button class="primary" data-action="enableAccessibility">前往系统设置</button></div>`);}

function testDialog(){const c=S.config,quiet=quietState().on;openModal('确认这一声能听见',`<div class="card"><div class="row"><div class="label-icon">${icon('sound')}</div><div><h3>${esc(toneNames[c.ringtone])} · ${c.volume}% 闹钟音量</h3><p class="sub">${c.ramp?'渐强响铃':'立即响铃'} · ${c.duration} 秒后自动停止</p></div></div></div><p class="body-copy">会按正式提醒的方式响铃，可随时关闭。选择锁屏测试后，你有 15 秒时间锁上手机。请避免将手机或耳机贴近耳朵进行首次高音量测试。</p><p class="hint">${quiet?'静音模式开启中：<strong>这次测试也不会发声</strong>，想听声音请先在“声音”页恢复响铃。<br>':''}锁屏测试需要精确闹钟权限。勿扰、蓝牙耳机和厂商后台设置，都建议在这里实际试一次。</p>`,`<div class="sheet-actions"><button class="secondary" data-action="testLater">15 秒后锁屏测试</button><button class="primary" data-action="test">立即测试</button></div>`);}
function privacy(){openModal('隐私与使用说明',`<div class="body-copy"><p><strong>只守候你添加的主播。</strong><br>通过 B 站公开接口检测你在“主播”页添加的直播间。仅“正在直播”触发开播提醒，轮播不会触发。不需要登录，不读取 Cookie，不访问私信。删除主播后，本机保存的头像与场次记录一并清除。</p><p><strong>数据留在你的手机。</strong><br>设置、铃声、主播列表、缓存的头像和最多 200 条运行记录保存在应用内，无广告、无统计上报。自选背景图片通过系统选择器选取，复制并缩放后保存于应用内部，不需要整册相册权限；备份不包含背景图片。网络请求仅用于连接 B 站公开直播接口（读取直播状态、主播名称与头像）。点击个人空间或直播间时会打开 B 站或浏览器。</p><p><strong>强响铃有明确边界。</strong><br>使用闹钟音量通道、可选振动及全屏通知。应用不会关闭系统勿扰模式，仍需由你在系统中允许闹钟。响铃到期自动停止，同场直播会去重；暂缓提醒是你主动请求的再次提醒。</p><p><strong>通知异常兼容响铃。</strong><br>需你明确开启，通知未获准时仍按所选时段播放闹铃。系统通知权限保持真实状态。悬浮关闭按钮完全可选，仅响铃时显示；可在应用内或系统中随时关闭相关设置。</p><p><strong>无障碍守候辅助。</strong><br>完全可选。只接收本应用自身的窗口变化信号，每约 45 秒检查守候服务；发现中断时请求恢复，失败后限速重试。不读取窗口内容、不执行手势或点击、不监听按键、不截屏。只有你已开启守候时才尝试恢复，不能突破系统强制停止。</p><p><strong>不是云端推送。</strong><br>守候依靠手机后台联网检测。正常情况下延迟约为检测间隔加网络耗时；B 站限制、接口改变、断网、系统休眠或强行停止都可能造成延迟或漏报。关闭“优先保证提醒”可降低耗电，但更容易延迟。</p><p><strong>时间按所选时区计算。</strong><br>跨午夜的时段属于开始那天。缺少接口开播时间时按首次检测时间判断；补报开关允许在进入时段后提醒已经开始的直播。</p><p><strong>文件备份。</strong><br>导出含设置、主播列表、状态和运行记录，不包含自选铃声与已缓存头像本身。恢复时会一并替换时段、声音设置和主播列表，不自动开始守候。卸载会删除本地设置与铃声，请先备份。</p><p>图标使用你提供的图片。应用为个人使用制作，与哔哩哔哩及主播无官方关联。</p></div>`);}
/* --- choosing which live room to open ---------------------------------------
   The button is stage one; the panel it unfolds is stage two. The list is capped and
   scrolls by itself, so a dozen anchors cannot push the page around, and the live ones
   come first because that is what the button promises. */
let livePickerOpen=false;
let packetPickerOpen=false;
let kaoziPickerOpen=false;
function livePicker(){
    const armed=enabledAnchors();
    const live=armed.filter(a=>(a.snapshot||{}).status===1),rest=armed.filter(a=>(a.snapshot||{}).status!==1);
    const row=a=>{const s=a.snapshot||{};return `<button class="live-picker-row" data-anchor-open="${esc(a.id)}">${anchorArt(a)}<span class="grow"><strong>${esc(a.name)}</strong><small>${esc(s.status===1?(s.title||'正在直播'):'暂未开播')}</small></span><span class="chevron">›</span></button>`;};
    const head=live.length?`<span class="dot live"></span>正在直播 ${live.length} 位 · 选择要打开的直播间`:`<span class="dot"></span>当前没有主播在直播 · 选择要打开的直播间`;
    return `<section class="live-picker" id="live-picker"><div class="live-picker-head">${head}</div><div class="live-picker-list">${live.map(row).join('')}${rest.map(row).join('')}</div></section>`;
}
function toggleLivePicker(){
    if(!enabledAnchors().length){livePickerOpen=false;toast('还没有启用中的主播');render();return;}
    livePickerOpen=!livePickerOpen;render();
    if(livePickerOpen){const panel=$('#live-picker');if(panel&&panel.scrollIntoView)panel.scrollIntoView({block:'nearest'});}
}
async function perform(action,anchorId=''){
    if(action==='petHop'){petHop();return;}
    if(action==='openLive'){
        // Two callers share this action name. The ringing page's button names a room and the
        // native side opens it (falling back to the first anchor when the id is empty, which is
        // why the page is checked as well — an empty id used to leave that button doing nothing
        // at all); the home page's own button, which carries no anchor, unfolds the picker.
        if(anchorId||document.body.classList.contains('alarm-page')){await api('openLive',{id:anchorId});return;}
        toggleLivePicker();return;
    }
    if(action==='accessibilityInfo'){accessibilityInfo();return;}
    if(action==='compatibilityInfo'){compatibilityInfo();return;}
    if(action==='confirmCompatibility'){
        if(compatibilitySaving)return;compatibilitySaving=true;
        const next=compatibilityAction;
        try{await save({soundWithoutNotifications:true});compatibilityAction=null;closeModal();
            if(next==='start')await toggleWatch(true);else if(next)await perform(next);else toast('已开启通知异常兼容响铃');
        }finally{compatibilitySaving=false;}return;
    }
    if(action==='notificationReport'){notificationReport();return;}
    if(action==='reportOnly'||action==='reportWithComponents'){await api('exportNotificationReport',{includeComponents:action==='reportWithComponents'});return;}
    if(action==='notificationHelp'||action==='watchNotificationHelp'){await window.refreshNative(true);notificationHelp();return;}
    if(action==='notificationProbe'){await api('notificationProbe');toast('测试通知已提交，请下拉通知栏确认是否收到');await window.refreshNative(true);return;}
    if(action==='recheckPermissions'){await window.refreshNative(true);paintNotificationHelp();toast('已重新读取系统权限状态');return;}
    if(action==='history'){route='history';render();return;}if(action==='back'){go('home');return;}if(action==='closeModal'){closeModal();return;}
    // The add buttons on the 时段 page carry their list through data-rule-target, which reaches
    // this action as its value; a plain call is the reminder list.
    if(action==='addRule'){editRule(null,'',anchorId==='high'?'high':'');return;}if(action==='saveRule'){await saveRule();return;}
    if(action==='deleteRule'){
        const high=ruleTarget==='high',what=high?'高频时段':'时段';
        const rules=(S.config[ruleListKey(ruleTarget)]||[]).filter(w=>w.id!==ruleDraft.id);
        await save(high?{highWindows:rules}:{windows:rules,allDay:rules.some(w=>w.enabled)?S.config.allDay:true});
        closeModal();toast(what+'已删除');return;
    }
    if(action==='testDialog'){testDialog();return;}if(action==='quietDialog'){quietDialog();return;}if(action==='privacy'){privacy();return;}if(action==='chooseTimezone'){await chooseTimezone();return;}
    if(action==='clearHistory'){openModal('清空通知记录？','<p class="body-copy">将删除手机上已有的通知与运行记录，提醒设置不变。</p>','<div class="sheet-actions"><button class="secondary" data-action="closeModal">取消</button><button class="primary" data-action="confirmClear">清空记录</button></div>');return;}
    if(action==='confirmClear'){await api('clearHistory');forgetHistory();closeModal();history();return;}
    if(action==='addAnchor'){editAnchor('');return;}
    if(action==='packetPicker'){packetPickerOpen=!packetPickerOpen;kaoziPickerOpen=false;render();return;}
    if(action==='kaoziPicker'){kaoziPickerOpen=!kaoziPickerOpen;packetPickerOpen=false;render();return;}
    if(action==='probeDanmaku'){
        // The trial build's instrument: whether this device can see a room's red-packet broadcasts is
        // answered by the device itself, so the button listens to the real danmaku stream and lists
        // every command it received, any red packet verbatim.
        const box=$('#packet-probe'),button=$('#modal [data-action="probeDanmaku"]');
        if(button){button.disabled=true;button.textContent='正在试连…';}
        try{
            const text=await api('probeDanmaku',{id:(anchorDraft&&anchorDraft.id)||''},30000);
            if(box)box.innerHTML='<pre class="probe">'+esc(String(text).slice(0,2000))+'</pre>';
            toast('试连结束，下面是听到的内容');
        }catch(e){if(box)box.innerHTML='<p class="form-error">'+esc(e.message)+'</p>';}
        finally{if(button){button.disabled=false;button.innerHTML=icon('spark')+'试连一次弹幕';}}
        return;
    }
    if(action==='addScheduleEntry'){addScheduleEntry();return;}
    if(action==='parseScheduleText'){await parseScheduleIntoDraft();return;}
    if(action==='saveSchedule'){await saveScheduleDraft();return;}
        if(action==='ocrSchedule'){await ocrScheduleIntoDraft();return;}
        if(action==='clearScheduleDraft'){scheduleDraft.entries=[];paintScheduleModal();return;}
    if(action==='refreshAvatars'){
        const r=await api('refreshAvatars',{},60000);
        await window.refreshNative(true);
        toast('已为 '+r+' 位主播刷新头像');
        return;
    }
    if(action==='pickScheduleImage'){
        if(scheduleDraft){
            try{await api('pickScheduleImage',{id:scheduleDraft.id},60000);
                await refreshSchedulePicture(scheduleDraft.id);}
            catch(e){toast(e.message);}
        }
        return;
    }
    if(action==='removeScheduleImage'){if(scheduleDraft){await api('removeScheduleImage',{id:scheduleDraft.id});await refreshSchedulePicture(scheduleDraft.id);toast('图片已删除');}return;}
    if(action==='viewScheduleImage'){openImageViewer(anchorId);return;}
    if(action==='suggestHighWindows'){
        const info=await api('suggestHighWindows');
        const line=$('#high-suggest');
        if(!info.ok){
            if(line)line.textContent='近 30 天只记录了 '+info.samples+' 场开播，样本还太少，暂时不推荐';
            toast('历史样本还太少，先按现在的时段用着');
            return;
        }
        const windows=info.windows.map(w=>`<span class="chip flat">${time(w.start)}–${time(w.end)}</span>`).join('');
        openModal('按历史开播时段推荐',`<div class="body-copy"><p>根据最近 30 天记录到的 <strong>${info.samples} 场</strong>开播，把最集中的几个小时设为高频时段：</p><div class="chips" style="margin:14px 0">${windows}</div><p>这些时段覆盖了约 <strong>${info.coverage}%</strong> 的开播。替换后仍可逐条修改。</p></div>`,`<div class="sheet-actions"><button class="secondary" data-action="closeModal">取消</button><button class="primary" data-action="applyHighWindows">替换高频时段</button></div>`);
        pendingHighWindows=info.windows;
        return;
    }
    if(action==='applyHighWindows'){
        const list=(pendingHighWindows||[]).map((w,i)=>({id:'h'+Date.now().toString(36)+i,name:'高频时段 '+(i+1),start:w.start,end:w.end,days:127,enabled:true}));
        if(!list.length){closeModal();return;}
        await save({highWindows:list});
        closeModal();toast('已替换为 '+list.length+' 个高频时段');
        return;
    }
    if(action==='clearEndedSchedule'){
        // Only dated entries whose day has passed: a weekly rule keeps coming round, so it is never
        // "ended" no matter how old it is.
        const iso=isoToday();
        let removed=0;
        for(const a of (S.anchors||[])){
            const all=a.schedule||[];
            const keep=all.filter(e=>{const d=entryDate(e);if(d&&d<iso){removed++;return false;}return true;});
            if(keep.length!==all.length)await api('saveSchedule',{id:a.id,entries:keep});
        }
        await window.refreshNative(true);
        toast(removed?'已清理 '+removed+' 条已结束的日程':'没有已结束的日程');
        return;
    }
    if(action==='closeImageViewer'){closeImageViewer();return;}
    if(action==='cancelAutoOpen'){
        // The user gets the last word: cancelling stops this automatic step for this ring only.
        autoOpen.cancelled=true;
        if(autoOpen.timer)clearInterval(autoOpen.timer);
        autoOpen.timer=0;paintAutoOpen();
        toast('这次不再自动进入直播间');
        return;
    }
    if(action==='resolveAnchor'){await resolveAnchorDraft();return;}
    if(action==='refreshAvatar'){await refreshAvatarDraft();return;}
    if(action==='saveAnchorDraft'){await saveAnchorDraft();return;}
    if(action==='deleteAnchor'){askDeleteAnchor();return;}
    if(action==='pickBackground'){
        // The native side answers only after the chosen picture has been imported, so a
        // cancelled or failed pick is reported here instead of looking like a success.
        try{await api('pickBackground',{},120000);await window.refreshNative(true);toast('背景图片已更新');}
        catch(e){toast(e.message);}
        return;
    }
    if(action==='removeBackground'){
        try{await api('removeBackground',{});await window.refreshNative(true);toast('背景已移除');}
        catch(e){toast(e.message);}
        return;
    }
    if(action==='confirmDeleteAnchor'){await removeAnchor();return;}
    if(action==='enableAccessibility'){closeModal();await api('permission',{kind:'accessibility'});return;}if(action==='start'){await toggleWatch(true);return;}
    if(action==='test'||action==='testLater'){if(!alarmAvailable()){compatibilityInfo(action);return;}const value=await api(action);closeModal();if(native&&action==='testLater')toast('15 秒后响铃，现在可以锁屏');await window.refreshNative(true);return;}
    const result=await api(action,anchorId?{id:anchorId}:{});if(action==='refresh')toast('正在检查直播间…');if(action==='cancelTest')toast('锁屏测试已取消');if(action==='cancelSnooze')toast('已取消稍后提醒');await window.refreshNative(true);
}
function paintNotificationHelp(){
    const box=$('[data-notification-help]');if(!box||!S)return;
    const p=S.permissions,blocked=p.notificationPolicy==='revoked';
    const advice=$('[data-notification-advice]');if(advice)advice.textContent=blocked?'系统报告通知权限受策略限制，普通授权弹窗无法改变它。当前还不能确定是哪一个系统组件或管理策略设置了限制，请导出排查包继续定位。':'可以从下面的系统设置入口允许通知，再返回重新检查。如果开启后仍恢复关闭，请导出通知排查包。'+(S.xiaomi?' 当前手机优先打开小米通知管理。':'');
    const probe=$('#modal [data-action="notificationProbe"]');if(probe)probe.disabled=!p.notifications;
    box.innerHTML=`<p><strong>${p.notifications?'系统已允许发送通知':blocked?'通知授权受系统策略限制':'系统尚未允许发送通知'}</strong></p><p>通知授权：${p.notificationRuntime?'已允许':'未允许'}<br>应用通知开关：${p.notificationAppEnabled?'已开启':'未开启'}<br>策略检查：${blocked?'被策略拒绝':p.notificationPolicy==='not_revoked'?'未检测到策略拒绝':p.notificationPolicy==='not_applicable'?'此系统无需运行时通知授权':'暂时无法读取'}</p><p class="hint">检查时间：${esc(new Date(S.permissionsCheckedAt||S.now).toLocaleTimeString('zh-CN',{hour12:false}))}</p>`;
}
function notificationHelp(){
    openModal('通知授权帮助',`<div class="card body-copy" data-notification-help></div><p class="body-copy" data-notification-advice></p><div class="permission-actions"><button class="text-button" data-permission="notificationSettings">打开通知设置 ${icon('external')}</button><button class="text-button" data-permission="standardNotifications">备用通知设置 ${icon('external')}</button><button class="text-button" data-permission="appPermissions">应用权限管理 ${icon('external')}</button></div><p class="hint">兼容响铃仍可使用，但它不会解除系统通知限制。排查包仅保存在你选择的位置。</p><button class="text-button" data-action="notificationReport">导出通知排查包 ${icon('download')}</button><button class="text-button" data-action="compatibilityInfo">选择通知异常兼容响铃 ${icon('arrow')}</button>`,`<div class="sheet-actions"><button class="secondary" data-action="recheckPermissions">重新检查</button><button class="primary" data-action="notificationProbe">发送测试通知</button></div>`);
    paintNotificationHelp();
}
function notificationReport(){
    openModal('导出通知排查包',`<div class="body-copy"><p>用来定位通知开关无法保持的问题。包含VR闹钟的通知状态、最近检查时间、手机系统版本及指定权限组件的版本。</p><p><strong>建议选择“包含权限组件”。</strong><br>会额外复制可读取的系统权限组件安装包，方便核对这台手机使用的具体实现；不读取这些组件的应用数据、聊天、照片或其他个人文件。文件可能有数十 MB。</p><p>选择保存位置后，请把 ZIP 文件发回。不会自动上传，也不会修改系统权限策略。</p></div>`,`<div class="sheet-actions"><button class="secondary" data-action="reportOnly">仅诊断记录</button><button class="primary" data-action="reportWithComponents">包含权限组件</button></div>`);
}
async function toggleWatch(enabled){if(enabled&&!alarmAvailable()){compatibilityInfo('start');return;}if(busy)return;busy=true;try{await api('toggle',{enabled});await window.refreshNative(true);}finally{busy=false;}}
function go(next){route=next;render();window.scrollTo({top:0,behavior:'auto'});}
document.addEventListener('click',async e=>{
    const b=e.target.closest('button');if(!b)return;
    try{
        if(b.dataset.route){go(b.dataset.route);return;}
        // The silent-mode choice is a duration, not a config key, so it does not travel through
        // data-setting / data-action: it is sent as silentMinutes and turned into a deadline natively.
        if(b.dataset.quietMinutes!==undefined){await setQuiet(Number(b.dataset.quietMinutes));return;}
        if(b.dataset.imgAction){if(b.dataset.imgAction==='actual')imageViewerActualSize();else imageViewerFitToScreen();return;}
        if(b.dataset.action){await perform(b.dataset.action,b.dataset.anchor||b.dataset.ruleTarget||'');return;}
        if(b.dataset.anchorEdit){editAnchor(b.dataset.anchorEdit);return;}
        if(b.dataset.anchorSchedule){editSchedule(b.dataset.anchorSchedule);return;}
        if(b.dataset.anchorOpen){livePickerOpen=false;await api('openLive',{id:b.dataset.anchorOpen});if(route==='home')render();return;}
        if(b.dataset.anchorProfile){await api('openProfile',{id:b.dataset.anchorProfile});return;}
        if(b.dataset.toggle){const key=b.dataset.toggle;if(key==='enabled')await toggleWatch(!S.enabled);else if(key.startsWith('anchor:'))await toggleAnchor(key.slice(7));else if(key.startsWith('packet:'))await togglePacket(key.slice(7));else if(key.startsWith('allDay:'))await toggleAllDay(key.slice(7));else if(key.startsWith('ring:'))await toggleRing(key.slice(5));else if(key==='draftAlarm'){if(anchorDraft){anchorDraft.alarm=!(anchorDraft.alarm!==false);b.setAttribute('aria-checked',anchorDraft.alarm);}}else if(key==='soundWithoutNotifications'&&!S.config[key])compatibilityInfo();else await save({[key]:!S.config[key]});return;}
        if(b.dataset.allDay){await save({allDay:b.dataset.allDay==='true'});return;}
        if(b.dataset.tone){await save({ringtone:b.dataset.tone});return;}
        if(b.dataset.seed!==undefined){await save({seedColor:b.dataset.seed});return;}
        if(b.dataset.pet){await save({petCharacter:b.dataset.pet});return;}
        if(b.dataset.schedDel){scheduleDraft.entries=scheduleDraft.entries.filter(x=>x.id!==b.dataset.schedDel);paintScheduleModal();return;}
        if(b.dataset.editRule){editRule(b.dataset.editRule,'',b.dataset.ruleTarget);return;}
        if(b.dataset.preset){editRule(null,b.dataset.preset,b.dataset.ruleTarget);return;}
        if(b.dataset.ruleToggle){
            const high=b.dataset.ruleTarget==='high',key=ruleListKey(b.dataset.ruleTarget);
            const rules=clone(S.config[key]||[]),r=rules.find(x=>x.id===b.dataset.ruleToggle);
            r.enabled=!r.enabled;await save(high?{highWindows:rules}:{windows:rules});return;
        }
        if(b.dataset.weekday!==undefined){const day=Number(b.dataset.weekday);ruleDraft.days^=1<<day;b.classList.toggle('selected');b.setAttribute('aria-pressed',!!(ruleDraft.days&(1<<day)));return;}
        if(b.dataset.zone){await save({timezone:b.dataset.zone});closeModal();toast('已按所选时区计算提醒范围');return;}const jump=b.dataset.permission||b.dataset.permissionJump;if(jump){if(jump==='accessibility'){accessibilityInfo();return;}if(jump==='notifications'){await window.refreshNative(true);if(S.permissions.notificationPolicy==='revoked'){notificationHelp();return;}}await api('permission',{kind:jump});return;}
    }catch(error){toast(error.message);}
});
document.addEventListener('change',async e=>{try{if(e.target.id==='volume'){await save({volume:Number(e.target.value)});return;}const key=e.target.dataset.setting;if(key){let value=e.target.value;if(['duration','pollSeconds','snoozeMinutes','backgroundDim','cardOpacity','lowPollSeconds'].includes(key))value=Number(value);await save({[key]:value});}}catch(error){toast(error.message);render();}});
document.addEventListener('input',e=>{if(e.target.id==='tz-search')renderZones(e.target.value);if(e.target.id==='volume')$('#volume-value').innerHTML=`${Number(e.target.value)}<span>%</span>`;});
document.addEventListener('error',e=>{const img=e.target;if(img&&img.tagName==='IMG'&&img.closest('.anchor-art'))img.remove();},true);
document.addEventListener('keydown',e=>{if(e.key==='Escape')window.appBack();if(e.key==='Tab'&&$('#modal')&&!$('#modal').hidden){const items=[...$('#modal').querySelectorAll('button,input,select')].filter(x=>!x.disabled),first=items[0],last=items[items.length-1];if(e.shiftKey&&document.activeElement===first){last.focus();e.preventDefault();}else if(!e.shiftKey&&document.activeElement===last){first.focus();e.preventDefault();}}});
window.appBack=()=>{if(imageViewer.open){closeImageViewer();return;}if($('#modal')&&!$('#modal').hidden){closeModal();return;}if(route!=='home'){go('home');return;}api('minimize');};
let refreshPromise=null,refreshQueued=false,forceQueued=false,stateRevision=0;
window.refreshNative=(force=false)=>{
    // Coalesce requests without dropping a forced refresh from a save or resume.
    refreshQueued=true;forceQueued=forceQueued||!!force;
    if(refreshPromise)return refreshPromise;
    refreshPromise=(async()=>{
        do{
            const repaint=forceQueued,revision=stateRevision;
            refreshQueued=false;forceQueued=false;
            try{
                const value=await api('state');
                // A reply captured before a completed save must not restore old settings.
                if(revision!==stateRevision){refreshQueued=true;forceQueued=true;continue;}
                const old=S,changed=configFingerprint!==JSON.stringify(value.config);
                const permissionsChanged=!old||JSON.stringify(old.permissions)!==JSON.stringify(value.permissions);
                const zoneChanged=!old||old.zone!==value.zone||old.deviceZone!==value.deviceZone;
                S=value;configFingerprint=JSON.stringify(S.config);applyTheme();
                if(document.body.classList.contains('alarm-page'))renderAlarm();
                else if(!old||changed||permissionsChanged||zoneChanged||repaint)render();
                else if(route==='home'&&(!$('#modal')||$('#modal').hidden))home();
                paintNotificationHelp();
                paintWatchNotice();
            }catch(error){toast(error.message);}
        }while(refreshQueued);
    })().then(()=>{refreshPromise=null;if(refreshQueued)return window.refreshNative(forceQueued);},error=>{refreshPromise=null;throw error;});
    return refreshPromise;
};
/* --- walking into the room ---------------------------------------------------------------
   Optional and off by default. The countdown lives on this page rather than in the service
   because the page is the one surface certainly visible when an alarm fires: starting an activity
   from the background is restricted, and a page is already allowed to open one. */
let autoOpen={armed:false,left:0,timer:0,cancelled:false};
const AUTO_OPEN_SECONDS=5;
function resetAutoOpen(){
    if(autoOpen.timer)clearInterval(autoOpen.timer);
    autoOpen={armed:false,left:0,timer:0,cancelled:false};
}
function paintAutoOpen(){
    const box=$('#auto-open');if(!box)return;
    box.textContent=autoOpen.cancelled?'已取消自动进入直播间':`响铃后 ${Math.max(0,autoOpen.left)} 秒自动进入直播间`;
}
function armAutoOpen(){
    const c=S.config||{};
    if(!S.ringing||S.alarmTest||!c.autoOpenLive||autoOpen.armed||autoOpen.cancelled)return;
    autoOpen.armed=true;autoOpen.left=AUTO_OPEN_SECONDS;
    paintAutoOpen();
    autoOpen.timer=setInterval(()=>{
        if(!S.ringing){resetAutoOpen();paintAutoOpen();return;}
        autoOpen.left--;
        if(autoOpen.left<=0){
            clearInterval(autoOpen.timer);autoOpen.timer=0;
            // The same call the button makes, flagged so the record says it was automatic.
            api('openLive',{id:S.alarmAnchorId||'',auto:true}).catch(()=>{});
            return;
        }
        paintAutoOpen();
    },1000);
}
function renderAlarm(){
    const test=S.alarmTest||S.preview,active=S.ringing||S.preview,who=S.alarmAnchor||'主播';
    // Whether this alarm is allowed to make noise was decided when it started, and beginAlarm
    // stored that decision — reading it from the live setting would describe a different alarm
    // than the one sounding, e.g. after the countdown runs out mid-ring.
    const quiet=!!S.alarmSilent;
    // The alarm page keeps the original illustration: live-room covers are low-res screenshots
    // and read badly at this size. Cover thumbnails remain on the week page only.
    // The anchor id belongs in the key: it is what the 「去直播间」 button carries, and a
    // stale one would open the wrong room when the next ring keeps every other field equal.
    const key=[test,active,S.alarmTitle,S.config.snoozeMinutes,who,S.alarmAnchorId||'',quiet].join('|');
    if(alarmPainted!==key){alarmPainted=key;$('#alarm-root').innerHTML=`<div class="alarm-eyebrow"><span class="dot ${active?'live':''}"></span>${test?'RINGTONE TEST':'LIVE NOW'}</div><div class="alarm-halo"><img src="hazel.png" alt="正在提醒你的VR闹钟插画"></div><h1>${active?(test?(quiet?'静音模式的这一声。':'这一声，听见了吗？'):esc(who)+' 开播啦。'):'响铃已结束。'}</h1><p class="alarm-name">${test?(quiet?'VR闹钟 · 静音模式，本次不发声':'VR闹钟 · 和正式提醒一样响'):esc(who)}</p><p class="alarm-title">${esc(S.alarmTitle||(test?(quiet?'确认静音模式下的全屏提醒与锁屏显示':'确认声音、振动与锁屏显示'):'去直播间，和主播见个面。'))}</p>${quiet&&active?'<p class="countdown" style="color:var(--amber)">静音模式 · 只全屏提醒与发通知，不响铃、不振动</p>':''}<p class="countdown" id="remaining"></p>${active&&!test&&S.config.autoOpenLive?'<p class="countdown" id="auto-open"></p><button class="quiet-close" data-action="cancelAutoOpen">取消自动进入</button>':''}<div class="alarm-actions">${active?(test?`<button class="primary" data-action="dismiss">${icon('check')}听见了，结束测试</button>`:`<button class="primary" data-action="openLive" data-anchor="${esc(S.alarmAnchorId||'')}">${icon('external')}去直播间 · 关闭响铃</button><button class="secondary" data-action="snooze">${icon('clock')}${S.config.snoozeMinutes} 分钟后再提醒</button><button class="quiet-close" data-action="dismiss">本场不再响铃</button>`):`<button class="primary" data-action="closeAlarm">知道了</button>`}</div><p class="footnote">${active?(quiet?'返回键只收起页面，提醒仍继续。<br>请点按上方按钮或通知中的关闭按钮。':'返回键只收起页面，铃声仍继续。<br>请点按上方按钮或通知中的关闭按钮。'):'同一场直播不会再次自动响铃。'}</p>`;}
    if($('#remaining'))$('#remaining').textContent=active?(S.preview?'界面预览 · 实际响铃需安装安卓应用':`${Math.max(0,Math.ceil((S.alarmUntil-Date.now())/1000))} 秒后自动停止`):'';
    // Armed once per ring; the state poll repaints this page every couple of seconds, so the guard
    // inside armAutoOpen() is what keeps the countdown from restarting.
    if(active&&!test)armAutoOpen();else resetAutoOpen();
}
function petSection(){
    const c=S.config,manqu=petCharacter()==='manqu';
    return `<div class="section-label"><h2>桌面小宠物</h2><small>只在应用内活动</small></div><section class="card">${setting('养一只小宠物','一只在底部走来走去的小宠物，偶尔会自己跳一下。它不联网、不占后台，只在你打开应用时活动。',sw('pet',c.pet!==false,'桌面小宠物'))}<div class="setting-row"><div class="grow"><h3>选择款式</h3><p class="sub">${manqu?'满区 · 一几一几，好可爱的一条区':'绿冻 · 绿果冻，头顶一颗樱桃'}</p></div><button class="text-button" data-action="petHop">逗它一下 ${icon('heart')}</button></div><div class="pet-picker"><button class="pet-choice ${manqu?'selected':''}" data-pet="manqu" aria-label="选择满区" aria-pressed="${manqu}"><img src="pet/manqu.png" alt=""></button><button class="pet-choice ${manqu?'':'selected'}" data-pet="lvdong" aria-label="选择绿冻" aria-pressed="${manqu?'false':'true'}"><img src="pet/lvdong.png" alt=""></button></div><p class="hint" style="margin:6px 0 0">满区是「可爱满区」的桌宠本体，来回爬的时候会翻个面；绿冻是绿色那只果冻。小宠物走在下方的独立一栏里，不会挡住按钮；想逗它就点上面的「逗它一下」。</p></section>`;
}

/* --- the little pet ----------------------------------------------------------
   It walks along a lane reserved above the navigation bar, so it can never cross a
   control. Position is a single translate3d written from one rAF loop, and that loop
   only runs while the page is visible — a hidden WebView (the usual state of this
   app) must not keep animating.
   满区 is two flat images stacked: the head and the scarf. Posing them as two bones
   (the head about the neck, the scarf answering a beat later) is what stops it from
   reading as a sticker being slid around. The jelly is a single image. The tap hop
   stays a CSS keyframe, so that part remains on the compositor. */
const PET_SPEED=30, PET_TURN_MS=430, PET_HOP_MIN_MS=9000, PET_HOP_SPAN_MS=7000;
/* Deliberately small: the head is a drawing, so past a few degrees it looks twirled.
   The scarf takes a fraction of the head's angle and of its travel, which is the drag
   that sells the joint. */
const PET_LEAN_DEG=3.4, PET_WOBBLE_DEG=1.7, PET_BOB_PX=1.9, PET_NOD_HZ=0.9, PET_SWAY_HZ=0.45;
/* The drawing is not left-right symmetric, so it walks mirrored and is put back at the
   far end. */
function facePet(){if(pet.el)pet.el.classList.toggle('flip',pet.dir>0);}
const pet={el:null,art:null,rig:null,head:null,collar:null,x:0,dir:1,phase:0,last:0,raf:0,wait:0,nextHop:0,appliedOn:null,appliedCharacter:null};
function petOn(){return !!(S&&S.config&&S.config.pet!==false);}
function petCharacter(){return (S&&S.config&&S.config.petCharacter)==='lvdong'?'lvdong':'manqu';}
function petMotionOk(){return !(typeof matchMedia==='function'&&matchMedia('(prefers-reduced-motion:reduce)').matches);}
function laneSize(){const viewport=window.innerWidth||360;return {viewport,lane:Math.min(viewport,580)};}
/** How far the feet sink past the bar's top edge. The bar paints above the pet, so the fluff
    can overlap it and the silhouette ends on the bar instead of showing a seam. */
const PET_GROUND_OVERLAP=3;
/**
 * Stands the pet on the navigation bar. The bar's height comes from its own content, so it
 * changes with the device's text size — a hardcoded 80px left the pet hanging in the air on a
 * bigger-text phone, and its shadow falls behind the bar, so any gap at all reads as floating.
 * The content reserve is measured off the same numbers, which is what keeps the last card on
 * the pet's head on every device instead of only on the one the pair of constants was tuned to.
 */
function standPet(){
    if(!pet.el)return;
    const nav=document.getElementById('nav'),content=$('#content');
    const navTop=nav?nav.getBoundingClientRect().top:0;
    if(!(navTop>0))return;
    const foot=Math.round(window.innerHeight-navTop-PET_GROUND_OVERLAP);
    pet.el.style.bottom=foot+'px';
    const inside=content?parseFloat(getComputedStyle(content).paddingBottom)||0:0;
    document.documentElement.style.setProperty('--pet-clearance',(foot+(pet.el.offsetHeight||56)-inside)+'px');
}
function placePet(){
    if(!pet.el)return;
    const {viewport,lane}=laneSize(),gutter=8;
    const left=(viewport-lane)/2+gutter;
    pet.el.style.transform='translate3d('+(left+pet.x)+'px,0,0)';
}
function petTravel(){const {lane}=laneSize();return Math.max(0,lane-(pet.el.offsetWidth||56)-16);}
/** Poses the two bones. The head leans and nods; the scarf answers with a smaller
    counter-rotation, which is what reads as weight on the joint.
    The lean is written in the layers' own coordinates and is not flipped with the walk:
    the mirrored parent turns it around on screen, which is exactly the lean-into-the-
    walk the eye expects in both directions. */
function posePet(){
    if(!pet.head||!pet.collar)return;
    const nod=Math.sin(pet.phase*PET_NOD_HZ*6.2832)*PET_BOB_PX;
    const lean=-PET_LEAN_DEG+Math.sin(pet.phase*PET_SWAY_HZ*6.2832)*PET_WOBBLE_DEG;
    pet.head.style.transform='translateY('+nod.toFixed(2)+'px) rotate('+lean.toFixed(2)+'deg)';
    pet.collar.style.transform='translateY('+(nod*0.35).toFixed(2)+'px) rotate('+(-lean*0.45).toFixed(2)+'deg)';
}
function startPet(){if(pet.raf||!petOn()||document.hidden||!petMotionOk())return;pet.last=0;pet.raf=requestAnimationFrame(petTick);}
function stopPet(){if(pet.raf)cancelAnimationFrame(pet.raf);pet.raf=0;}
function petTick(ts){
    pet.raf=0;
    // Any of these means the pet must be still: a backgrounded page costs nothing.
    if(!petOn()||document.hidden||!petMotionOk())return;
    if(!pet.last)pet.last=ts;
    const dt=Math.min(120,ts-pet.last)/1000;pet.last=ts;
    // A hop now and then while it walks: the pet reads as alive without needing a tap, which
    // it cannot receive anyway (it is click-through so it never blocks a control).
    if(!pet.nextHop)pet.nextHop=ts+PET_HOP_MIN_MS+Math.random()*PET_HOP_SPAN_MS;
    if(ts>=pet.nextHop){pet.nextHop=ts+PET_HOP_MIN_MS+Math.random()*PET_HOP_SPAN_MS;petHop();}
    if(ts>=pet.wait){
        const travel=petTravel();
        pet.x+=pet.dir*PET_SPEED*dt;
        // Turning pauses briefly at each end, which reads as the pet noticing the wall.
        if(pet.x>=travel){pet.x=travel;pet.dir=-1;pet.wait=ts+PET_TURN_MS;facePet();}
        else if(pet.x<=0){pet.x=0;pet.dir=1;pet.wait=ts+PET_TURN_MS;facePet();}
        placePet();
    }
    pet.phase=(pet.phase+dt)%64;
    posePet();
    pet.raf=requestAnimationFrame(petTick);
}
function petHop(){
    if(!pet.el||!petOn())return;
    pet.el.classList.remove('hop');void pet.el.offsetWidth;pet.el.classList.add('hop');
    clearTimeout(petHop.timer);petHop.timer=setTimeout(()=>pet.el.classList.remove('hop'),470);
}
function mountPet(){
    pet.el=$('#pet');pet.art=$('#pet-art');pet.rig=$('#pet-rig');
    pet.head=$('#pet-head');pet.collar=$('#pet-collar');
    if(!pet.el||!pet.art)return;   // alarm.html has no pet: everything below is a no-op there
    document.addEventListener('visibilitychange',()=>{document.hidden?stopPet():startPet();});
    window.addEventListener('resize',()=>{if(!pet.el.hidden){placePet();standPet();}});
    syncPet();
}
/** Idempotent: the two second state poll must not yank the pet back to the left. */
function syncPet(){
    if(!pet.el||!pet.art)return;
    const on=petOn(),character=petCharacter();
    if(pet.appliedOn===on&&pet.appliedCharacter===character)return;
    pet.appliedOn=on;pet.appliedCharacter=character;
    pet.el.hidden=!on;
    if(document.body)document.body.classList.toggle('has-pet',on);
    if(!on){stopPet();return;}
    const manqu=character==='manqu';
    if(pet.rig)pet.rig.hidden=!manqu;
    pet.art.hidden=manqu;
    if(!manqu)pet.art.style.backgroundImage='url(pet/lvdong.png)';
    pet.x=0;pet.dir=1;pet.phase=0;facePet();
    placePet();
    standPet();
    posePet();
    startPet();
}
window.refreshNative();mountPet();setInterval(()=>{if(!document.hidden)window.refreshNative();},2000);document.addEventListener('visibilitychange',()=>{if(!document.hidden)window.refreshNative(true);});
