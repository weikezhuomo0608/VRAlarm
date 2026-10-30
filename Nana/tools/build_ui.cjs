// Bundles ui-src/app.js into the packaged asset. With no arguments it does the normal thing
// (ui-src/app.js -> app/src/main/assets/app.js). With two arguments it reads and writes the given
// paths instead, which is how the 1.2.0 line builds its own stripped copy (see tools/build_apk.py).
const fs=require('fs');
const path=require('path');
const esbuild=require('esbuild');
const root=path.resolve(__dirname,'..');
const args=process.argv.slice(2);
const input=args[0]?path.resolve(args[0]):path.join(root,'ui-src/app.js');
const output=args[1]?path.resolve(args[1]):path.join(root,'app/src/main/assets/app.js');
const source=fs.readFileSync(input,'utf8');
fs.writeFileSync(output,esbuild.transformSync(source,{target:'chrome60',charset:'utf8',minify:false}).code);
console.log('Compiled offline Android UI for WebView 60+:',path.relative(root,input),'->',path.relative(root,output));
