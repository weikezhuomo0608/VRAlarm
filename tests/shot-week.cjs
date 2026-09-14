// Visual check for the weekly-timeline redesign: several days, a live entry with a cover
// thumbnail, long and short notes, and a narrow viewport for the small-screen breakpoint.
const http=require('http'),fs=require('fs'),path=require('path');
const {chromium}=require('playwright');
const root=path.join(__dirname,'..','app','src','main','assets');
const outDir=path.join(__dirname,'..','build');
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png'};
const server=http.createServer((req,res)=>{
    const p=req.url==='/'?'/index.html':req.url.split('?')[0];
    fs.readFile(path.join(root,p),(e,b)=>{if(e){res.writeHead(404);res.end();return;}
        res.writeHead(200,{'content-type':types[path.extname(p)]||'application/octet-stream'});res.end(b);});
});
// A stand-in live-room cover so the thumbnail branch is exercised.
const cover='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="128" height="80"><rect width="128" height="80" fill="#4b6b80"/><circle cx="96" cy="26" r="20" fill="#8fb0c4"/><text x="8" y="70" font-size="14" fill="#e8f0f5">LIVE</text></svg>');

const day=1<<2; // Wednesday
const state={
    anchors:[
        {id:'a1',name:'羽啾chu2u',uid:1,room:2,enabled:true,alarm:true,avatar:false,snapshot:{status:1,cover},
         schedule:[
            {id:'s1',days:day,start:12*60,end:14*60,note:'宇宙歌杂'},
            {id:'s2',days:day,start:22*60,end:0,note:'宇宙杂谈 · 一起聊聊最近的动画和游戏，还有大家的投稿'},
         ]},
        {id:'a2',name:'灰泽满 Hazel',uid:3,room:4,enabled:true,alarm:true,avatar:false,snapshot:{status:0},
         schedule:[{id:'s3',days:day,start:19*60+30,end:21*60,note:''}]},
    ],
    config:null,enabled:true,running:true,ringing:false,snapshot:{},networkError:'',serviceError:'',startError:'',
    inside:true,permissions:{notifications:true,alarmChannel:true,battery:true,fullScreen:true,exact:true,dnd:false},
    zone:'Asia/Shanghai',deviceZone:'Asia/Shanghai',version:'1.1.5',now:Date.now(),backgroundSet:false,backgroundName:'',recoveryAt:0,
};

async function shoot(page,file){
    await page.evaluate(s=>{S=clone(previewState);Object.assign(S,JSON.parse(s));S.config=clone(defaults);route='week';render();},JSON.stringify(state));
    await page.waitForTimeout(250);
    await page.screenshot({path:path.join(outDir,file),fullPage:true});
}

server.listen(0,'127.0.0.1',async()=>{
    const browser=await chromium.launch({headless:true,
        ...(process.env.HAZEL_TEST_CHROMIUM?{executablePath:process.env.HAZEL_TEST_CHROMIUM}:{}),
        args:['--no-sandbox']});
    const url=`http://127.0.0.1:${server.address().port}/`;

    const tall=await browser.newPage({viewport:{width:393,height:900},deviceScaleFactor:2});
    tall.on('pageerror',e=>console.log('pageerror:',e.message));
    await tall.goto(url);
    await shoot(tall,'shot-week.png');

    const narrow=await browser.newPage({viewport:{width:320,height:900},deviceScaleFactor:2});
    narrow.on('pageerror',e=>console.log('pageerror:',e.message));
    await narrow.goto(url);
    await shoot(narrow,'shot-week-narrow.png');

    await browser.close();
    console.log('saved week shots');
    server.close();process.exit(0);
});
