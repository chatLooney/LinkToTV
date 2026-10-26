package dev.linktotv

/** Sender page served by the TV, so an iPhone (or any browser) needs nothing installed. */
const val PAGE = """<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="apple-mobile-web-app-capable" content="yes"><meta name="theme-color" content="#1C1C1E"><title>Link to TV</title>
<style>
body{font:17px -apple-system,system-ui,sans-serif;margin:0 auto;padding:24px;background:#1C1C1E;color:#FAFAF7;max-width:520px;min-height:100vh;box-sizing:border-box;display:flex;flex-direction:column;gap:12px}
h1{font-size:24px;margin:0;display:flex;align-items:center;gap:12px}h2{font-size:13px;letter-spacing:.08em;color:#B8B8B2;margin:18px 0 0}
input,button{font:inherit;box-sizing:border-box}
input[type=text],input[type=url],input[type=tel]{width:100%;height:56px;padding:0 16px;border-radius:14px;border:1px solid #46464A;background:#2C2C2F;color:#FAFAF7}
.go{width:100%;height:56px;border:0;border-radius:14px;background:#FFD23F;color:#1C1C1E;font-weight:800;font-size:18px;margin-top:8px}
.alt{width:100%;height:48px;border:1px solid #46464A;border-radius:14px;background:none;color:#FAFAF7;font-weight:700;margin-top:8px}
.row{display:flex;gap:4px;align-items:center;background:#2C2C2F;border-radius:14px;margin-top:8px}
.lnk{flex:1;min-width:0;text-align:left;background:none;border:0;color:#FAFAF7;padding:14px 16px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.ic{background:none;border:0;color:#FFD23F;font-size:22px;width:48px;height:48px}
.mut{color:#B8B8B2;font-size:15px}#s{min-height:22px;color:#B8B8B2;font-size:15px}
details{background:#2C2C2F;border-radius:14px;padding:12px 16px;margin-top:8px}summary{font-weight:700}
ol{padding-left:20px;line-height:1.5;color:#B8B8B2;font-size:15px}code{color:#FAFAF7}
a{color:#FFD23F}footer{margin-top:auto;padding-top:24px;font-size:14px;color:#B8B8B2}
</style></head><body>
<h1><svg width="40" height="40" viewBox="0 0 120 120" aria-hidden="true"><rect width="120" height="120" rx="28" fill="#2C2C2F"/><path d="M32 28 V84 H54" fill="none" stroke="#FFD23F" stroke-width="14" stroke-linecap="round" stroke-linejoin="round"/><polygon points="56,64 56,104 92,84" fill="#FFD23F" stroke="#FFD23F" stroke-width="6" stroke-linejoin="round"/></svg>Link to TV</h1>
<div id="pair"><p class="mut">First time: type the code shown on your TV.</p>
<input id="code" type="tel" inputmode="numeric" placeholder="6-digit code on the TV" aria-label="Pairing code">
<button class="go" onclick="pairIt()">Pair</button></div>
<div id="main" style="display:none">
<input id="url" type="url" placeholder="Paste a video link (.mp4, .mkv, .m3u8)" aria-label="Video link">
<button class="go" onclick="send(g('url').value.trim())">Send to TV</button>
<button class="alt" onclick="qadd(g('url').value.trim())">Add to queue</button>
<h2>SEND A FILE</h2>
<input id="file" type="file" accept="video/*,image/*" aria-label="Choose a file" style="color:#B8B8B2;margin-top:8px">
<button class="alt" onclick="upload()">Upload to TV and play</button>
</div>
<div id="s"></div>
<div id="lib" style="display:none">
<h2>QUEUE</h2><div id="queue"></div>
<div style="display:flex;gap:8px"><button class="go" style="flex:2" onclick="qplay()">Play all</button><button class="alt" style="flex:1;margin-top:8px" onclick="qclear()">Clear</button></div>
<h2>FAVOURITES</h2><div id="favs"></div><h2>RECENTLY SENT</h2><div id="hist"></div>
<details><summary>Share menu setup (iPhone)</summary>
<ol><li>Open the Shortcuts app, tap <code>+</code>, name it Send to TV.</li>
<li>Tap the <code>i</code> button, turn on Show in Share Sheet, and allow URLs.</li>
<li>Add Get Contents of URL. URL: copy the address below. Method: POST.</li>
<li>Request Body: Form. Add a Text field named <code>url</code> with value Shortcut Input.</li>
<li>Add the header <code>X-Token</code> with the token below.</li>
<li>Now Share any link in Safari and choose Send to TV.</li></ol>
<input id="scAddr" type="text" readonly aria-label="Shortcut address" onclick="this.select()" style="margin-top:6px">
<input id="scTok" type="text" readonly aria-label="Shortcut token" onclick="this.select()" style="margin-top:8px">
<p class="mut">The address changes if the TV gets a new IP. Reserve its IP in your router to keep it fixed.</p></details>
</div>
<footer>Made by Looney · <a href="https://looney.is-a.dev">looney.is-a.dev</a></footer>
<script>
var T=localStorage.getItem('tok');
function g(i){return document.getElementById(i)}
function say(m){g('s').textContent=m}
function post(p,b){var h={'Content-Type':'application/x-www-form-urlencoded'};if(T)h['X-Token']=T;return fetch(p,{method:'POST',headers:h,body:b||''})}
function enc(u){return 'url='+encodeURIComponent(u)}
function ok(u){return /^https?:\/\/\S+$/.test(u)}
function unpair(){localStorage.removeItem('tok');T=null;show();say('The TV forgot this phone. Pair again.')}
function show(){g('pair').style.display=T?'none':'block';g('main').style.display=T?'block':'none';g('lib').style.display=T?'block':'none';
  if(T){g('scAddr').value=location.origin+'/play';g('scTok').value=T;load()}}
function pairIt(){post('/pair','code='+encodeURIComponent(g('code').value.trim()))
  .then(function(r){return r.ok?r.json():Promise.reject()})
  .then(function(j){T=j.token;localStorage.setItem('tok',T);show();say('Paired. You can send links now.')})
  .catch(function(){say('Wrong code. Check the TV screen and try again.')})}
function act(path,u,msg){
  if(u!==null&&!ok(u)){say('Enter a link starting with http:// or https://');return}
  post(path,u===null?'':enc(u)).then(function(r){
    if(r.ok){say(msg);load()}else if(r.status==401)unpair();else if(r.status==400)say('Nothing to do. Is the queue empty?');else say('Could not reach the TV ('+r.status+').')})
  .catch(function(){say('Could not reach the TV. Same Wi-Fi?')})}
function send(u){act('/play',u,'Sent. It should start on the TV.')}
function qadd(u){act('/queue/add',u,'Added to the queue.')}
function qplay(){act('/queue/play',null,'Playing the queue on the TV.')}
function qclear(){act('/queue/clear',null,'Queue cleared.')}
function upload(){var f=g('file').files[0];if(!f){say('Choose a file first.');return}
  var x=new XMLHttpRequest();x.open('POST','/upload');x.setRequestHeader('X-Token',T);
  x.upload.onprogress=function(e){if(e.lengthComputable)say('Uploading '+Math.round(e.loaded*100/e.total)+'%')};
  x.onload=function(){if(x.status==200)say('Sent. Opening on the TV.');else if(x.status==401)unpair();else say('Upload failed ('+x.status+').')};
  x.onerror=function(){say('Upload failed. Same Wi-Fi?')};
  var d=new FormData();d.append('file',f);x.send(d)}
function load(){fetch('/library',{headers:{'X-Token':T}}).then(function(r){return r.status==401?Promise.reject(401):r.json()})
  .then(function(d){var f=d.favs||[];
    drawQ(d.queue||[]);draw('favs',f,f,'Tap the star on a recent link to save it.');
    draw('hist',(d.history||[]).map(function(h){return h.url}),f,'Nothing sent yet.')})
  .catch(function(e){if(e===401)unpair()})}
function label(u){try{var x=new URL(u);return x.host+x.pathname}catch(e){return u}}
function btn(cls,txt,aria,fn){var b=document.createElement('button');b.className=cls;b.textContent=txt;if(aria)b.setAttribute('aria-label',aria);b.onclick=fn;return b}
function note(box,t){var e=document.createElement('div');e.className='mut';e.textContent=t;box.appendChild(e)}
function drawQ(list){var box=g('queue');box.textContent='';if(!list.length){note(box,'Add links with Add to queue or the + button.');return}
  list.forEach(function(u,i){var r=document.createElement('div');r.className='row';
    r.appendChild(btn('lnk',(i+1)+'. '+label(u),null,function(){}));
    r.appendChild(btn('ic','\u2715','Remove from queue',function(){act('/queue/remove',u,'Removed.')}));box.appendChild(r)})}
function draw(id,list,favs,empty){var box=g(id);box.textContent='';if(!list.length){note(box,empty);return}
  list.forEach(function(u){var r=document.createElement('div');r.className='row';var on=favs.indexOf(u)>=0;
    r.appendChild(btn('lnk',label(u),null,function(){send(u)}));
    r.appendChild(btn('ic',on?'\u2605':'\u2606',on?'Remove favourite':'Save as favourite',function(){act('/fav',u,on?'Removed from favourites.':'Saved.')}));
    r.appendChild(btn('ic','+','Add to queue',function(){qadd(u)}));box.appendChild(r)})}
show();
</script></body></html>"""
