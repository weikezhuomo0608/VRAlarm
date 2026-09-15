// Visual check for the two-layer pet: parks it mid-lane, screenshots the lane and the card.
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
server.listen(0,'127.0.0.1',async()=>{
    const browser=await chromium.launch({headless:true,
        ...(process.env.HAZEL_TEST_CHROMIUM?{executablePath:process.env.HAZEL_TEST_CHROMIUM}:{}),
        args:['--no-sandbox']});
    const page=await browser.newPage({viewport:{width:393,height:852},deviceScaleFactor:2});
    const errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.waitForFunction(()=>typeof window.refreshNative==='function');
    await page.waitForTimeout(1200);

    // Park it mid-lane and pose it at a lean, so the joint is visible in the still.
    await page.evaluate(()=>{pet.x=petTravel()*0.45;pet.dir=1;pet.phase=0.18;placePet();posePet();});
    await page.waitForTimeout(150);
    const state=await page.evaluate(()=>({
        rig:!document.getElementById('pet-rig').hidden,
        head:document.getElementById('pet-head').style.transform,
        collar:document.getElementById('pet-collar').style.transform,
        size:[document.getElementById('pet').offsetWidth,document.getElementById('pet').offsetHeight]
    }));
    console.log('posed :',JSON.stringify(state));
    const box=await page.locator('#pet').boundingBox();
    await page.screenshot({path:path.join(outDir,'shot-pet-home.png'),fullPage:true});
    // A tight crop of the lane, blown up, is what actually shows whether the layers line up.
    await page.screenshot({path:path.join(outDir,'shot-pet-lane.png'),
        clip:{x:Math.max(0,box.x-60),y:Math.max(0,box.y-40),width:box.width+120,height:box.height+80}});

    // The other character.
    await page.locator('[data-route="settings"]').last().click();
    await page.waitForTimeout(300);
    await page.locator('.pet-choice[data-pet="lvdong"]').click();
    await page.waitForTimeout(200);
    const jelly=await page.evaluate(()=>({
        rig:!document.getElementById('pet-rig').hidden,
        art:document.getElementById('pet-art').hidden,
        image:document.getElementById('pet-art').style.backgroundImage,
        char:previewState.config.petCharacter
    }));
    console.log('jelly :',JSON.stringify(jelly));
    const jellyBox=await page.locator('#pet').boundingBox();
    if(jellyBox)await page.screenshot({path:path.join(outDir,'shot-pet-jelly.png'),
        clip:{x:Math.max(0,jellyBox.x-40),y:Math.max(0,jellyBox.y-30),width:jellyBox.width+80,height:jellyBox.height+60}});

    await page.locator('.pet-choice[data-pet="manqu"]').click();
    await page.waitForTimeout(200);
    const cardEl=page.locator('section.card').filter({hasText:'养一只小宠物'}).first();
    await cardEl.screenshot({path:path.join(outDir,'shot-pet-card.png')});
    console.log('page errors:',errors.length?errors:'none');
    await browser.close();
    server.close();process.exit(0);
});
