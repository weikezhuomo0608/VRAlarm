const fs=require('fs');
const path=require('path');
const esbuild=require('esbuild');
const root=path.resolve(__dirname,'..');
const source=fs.readFileSync(path.join(root,'ui-src/app.js'),'utf8');
fs.writeFileSync(path.join(root,'app/src/main/assets/app.js'),esbuild.transformSync(source,{target:'chrome60',charset:'utf8',minify:false}).code);
console.log('Compiled offline Android UI for WebView 60+.');
