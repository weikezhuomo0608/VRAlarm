// First-look screenshots for the 七七闹钟 fork: home / anchors / settings with
// 辉月七七 as the watched anchor.  node tests/_shot_nana.cjs
const http=require('http'),fs=require('fs'),path=require('path');
const {chromium}=require('playwright');
const root=path.join(__dirname,'..','app','src','main','assets');
const dir=path.join(__dirname,'..','build');
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png'};
const server=http.createServer((req,res)=>{
    const p=req.url==='/'?'/index.html':req.url.split('?')[0];
    fs.readFile(path.join(root,p),(e,b)=>{if(e){res.writeHead(404);res.end();return;}
        res.writeHead(200,{'content-type':types[path.extname(p)]||'application/octet-stream'});res.end(b);});
});
server.listen(0,'127.0.0.1',async()=>{
    const port=server.address().port;
    const browser=await chromium.launch({headless:true,
        ...(process.env.HAZEL_TEST_CHROMIUM?{executablePath:process.env.HAZEL_TEST_CHROMIUM}:{}),
        args:['--no-sandbox']});
    const page=await browser.newPage({viewport:{width:393,height:852},deviceScaleFactor:2});
    page.on('pageerror',e=>console.log('pageerror:',e.message));
    await page.goto(`http://127.0.0.1:${port}/`);
    await page.evaluate(()=>{
        const now=Date.now();
        previewState.anchors=[{id:'nana',name:'辉月七七official',uid:'3747561928134440',room:'1767669852',enabled:true,alarm:true,avatar:false,
            snapshot:{status:1,checkedAt:now-40000,title:'【边苔桑】杂谈回'},
            stats:{count7:2,count30:5,last:now-3600000,lastMinutes:95},
            schedule:[{id:'s1',days:16,start:1200,end:-1,note:'游戏回'}]}];
        previewState.enabled=true;previewState.running=true;previewState.version='1.1.0';
        S=clone(previewState);route='home';render();
    });
    await page.waitForTimeout(400);
    await page.screenshot({path:path.join(dir,'nana-home.png')});
    await page.evaluate(()=>{route='anchors';render();});
    await page.waitForTimeout(300);
    await page.screenshot({path:path.join(dir,'nana-anchors.png')});
    await page.evaluate(()=>{route='settings';render();});
    await page.waitForTimeout(300);
    await page.screenshot({path:path.join(dir,'nana-settings.png'),fullPage:false});
    await browser.close();
    console.log('saved 3 shots to build/');
    server.close();
    process.exit(0);
});
