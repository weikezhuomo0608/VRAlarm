// One-off visual check for the appearance port: settings page with a picked accent,
// and the home page with a fake background + translucent cards.
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
// 1x1 png would tile poorly; use a small SVG gradient as the pretend wallpaper.
const fakeBg='url("data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="390" height="844"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#3d5a73"/><stop offset="1" stop-color="#1c2b38"/></linearGradient></defs><rect width="390" height="844" fill="url(%23g)"/><circle cx="320" cy="120" r="70" fill="#5b7a94"/><circle cx="80" cy="600" r="90" fill="#46637c"/></svg>')+'")';
server.listen(0,'127.0.0.1',async()=>{
    const browser=await chromium.launch({headless:true,
        ...(process.env.HAZEL_TEST_CHROMIUM?{executablePath:process.env.HAZEL_TEST_CHROMIUM}:{}),
        args:['--no-sandbox']});
    const page=await browser.newPage({viewport:{width:393,height:852},deviceScaleFactor:2});
    page.on('pageerror',e=>console.log('pageerror:',e.message));
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.evaluate(bg=>{S=clone(previewState);route='settings';render();
        S.config.seedColor='#4F7FA4';S.backgroundSet=true;S.backgroundName='夜色渐变.jpg';
        render();
        const layer=document.getElementById('bg-layer');layer.style.backgroundImage=bg;
    },fakeBg);
    await page.waitForTimeout(200);
    await page.locator('.swatches').scrollIntoViewIfNeeded();
    await page.waitForTimeout(150);
    await page.screenshot({path:path.join(outDir,'shot-appearance-settings.png')});
    await page.evaluate(()=>{route='home';render();});
    await page.waitForTimeout(200);
    await page.screenshot({path:path.join(outDir,'shot-appearance-home.png')});
    // Dark + AMOLED with background
    await page.evaluate(bg=>{S.config.theme='dark';S.config.amoled=true;render();
        const layer=document.getElementById('bg-layer');layer.style.backgroundImage=bg;},fakeBg);
    await page.waitForTimeout(200);
    await page.screenshot({path:path.join(outDir,'shot-appearance-dark.png')});
    // The add-anchor dialog, to check that the uid-first wording and placeholder read clearly.
    await page.evaluate(()=>{S.config.theme='light';S.config.amoled=false;route='anchors';render();});
    await page.waitForTimeout(150);
    await page.locator('[data-action="addAnchor"]').click();
    await page.waitForTimeout(150);
    await page.screenshot({path:path.join(outDir,'shot-anchor-add.png')});
    await browser.close();
    console.log('saved shots');
    server.close();process.exit(0);
});
