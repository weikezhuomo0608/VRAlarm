// One-off visual check: render the anchors page with a rich preview anchor and
// screenshot the first anchor card.  node tests/shot-anchors.cjs [outfile]
const http=require('http'),fs=require('fs'),path=require('path');
const {chromium}=require('playwright');
const root=path.join(__dirname,'..','app','src','main','assets');
const out=process.argv[2]||path.join(__dirname,'..','build','shot-anchor.png');
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
        previewState.anchors=[{id:'kioi',name:'示例主播A',uid:'10000001',room:'20000001',enabled:true,alarm:true,avatar:false,
            snapshot:{status:1,checkedAt:now-40000,title:'夜间杂谈回'},
            stats:{count7:1,count30:1,last:now-3600000,lastMinutes:60},
            schedule:[{id:'s1',days:16,start:1200,end:-1,note:'游戏回'},{id:'s2',days:32,start:830,end:-1,note:'早播'}]},
            {id:'hazel',name:'灰泽满 Hazel',uid:'1298779265',room:'1713546334',enabled:true,alarm:true,avatar:false,
            snapshot:{status:0,checkedAt:now-120000},stats:{count7:0,count30:3,last:now-86400000,lastMinutes:1440},schedule:[]}];
        S=clone(previewState);route='anchors';render();
    });
    await page.waitForTimeout(300);
    await page.locator('article.anchor-card').first().screenshot({path:out});
    await browser.close();
    console.log('saved',out);
    server.close();
    process.exit(0);
});
