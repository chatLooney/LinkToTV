package dev.linktotv

/** Sender page served by the TV, so an iPhone (or any browser) needs nothing installed. */
const val PAGE = """<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="apple-mobile-web-app-capable" content="yes"><meta name="theme-color" content="#1C1C1E"><title>Link to TV</title>
<style>
body{font:17px -apple-system,system-ui,sans-serif;margin:0;padding:24px;background:#1C1C1E;color:#FAFAF7;max-width:520px;margin:0 auto;min-height:100vh;box-sizing:border-box;display:flex;flex-direction:column;gap:12px}
h1{font-size:24px;margin:0;display:flex;align-items:center;gap:12px}h2{font-size:13px;letter-spacing:.08em;color:#B8B8B2;margin:18px 0 0}
input,button{font:inherit;box-sizing:border-box}
input{width:100%;height:56px;padding:0 16px;border-radius:14px;border:1px solid #46464A;background:#2C2C2F;color:#FAFAF7}
.go{width:100%;height:56px;border:0;border-radius:14px;background:#FFD23F;color:#1C1C1E;font-weight:800;font-size:18px}
.row{display:flex;gap:8px;align-items:center;background:#2C2C2F;border-radius:14px;margin-top:8px}
.lnk{flex:1;min-width:0;text-align:left;background:none;border:0;color:#FAFAF7;padding:14px 16px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.star{background:none;border:0;color:#FFD23F;font-size:24px;width:52px;height:48px}
.mut{color:#B8B8B2;font-size:15px}#s{min-height:22px;color:#B8B8B2;font-size:15px}
a{color:#FFD23F}footer{margin-top:auto;padding-top:24px;font-size:14px;color:#B8B8B2}
</style></head><body>
<h1><svg width="40" height="40" viewBox="0 0 120 120" aria-hidden="true"><rect width="120" height="120" rx="28" fill="#2C2C2F"/><path d="M32 28 V84 H54" fill="none" stroke="#FFD23F" stroke-width="14" stroke-linecap="round" stroke-linejoin="round"/><polygon points="56,64 56,104 92,84" fill="#FFD23F" stroke="#FFD23F" stroke-width="6" stroke-linejoin="round"/></svg>Link to TV</h1>
<div id="pair"><p class="mut">First time: type the code shown on your TV.</p>
<input id="code" inputmode="numeric" placeholder="6-digit code on the TV" aria-label="Pairing code">
<button class="go" style="margin-top:8px" onclick="pairIt()">Pair</button></div>
<div id="main">
<input id="url" type="url" placeholder="Paste a video link (.mp4, .m3u8)" aria-label="Video link">
<button class="go" style="margin-top:8px" onclick="send(g('url').value.trim())">Send to TV</button>
</div>
<div id="s"></div>
<div id="lib" style="display:none"><h2>FAVOURITES</h2><div id="favs"></div><h2>RECENTLY SENT</h2><div id="hist"></div></div>
<footer>Made by Looney · <a href="https://looney.is-a.dev">looney.is-a.dev</a></footer>
<script>
var T=localStorage.getItem('tok');
function g(i){return document.getElementById(i)}
function say(m){g('s').textContent=m}
function post(p,b){var h={'Content-Type':'application/x-www-form-urlencoded'};if(T)h['X-Token']=T;return fetch(p,{method:'POST',headers:h,body:b})}
function unpair(){localStorage.removeItem('tok');T=null;show();say('The TV forgot this phone. Pair again.')}
function show(){g('pair').style.display=T?'none':'block';g('main').style.display=T?'block':'none';g('lib').style.display=T?'block':'none';if(T)load()}
function pairIt(){post('/pair','code='+encodeURIComponent(g('code').value.trim()))
  .then(function(r){return r.ok?r.json():Promise.reject()})
  .then(function(j){T=j.token;localStorage.setItem('tok',T);show();say('Paired. You can send links now.')})
  .catch(function(){say('Wrong code. Check the TV screen and try again.')})}
function send(u){
  if(!/^https?:\/\/\S+$/.test(u)){say('Enter a link starting with http:// or https://');return}
  post('/play','url='+encodeURIComponent(u)).then(function(r){
    if(r.ok){say('Sent. It should start on the TV.');load()}
    else if(r.status==401)unpair();else say('Could not send ('+r.status+').')})
  .catch(function(){say('Could not reach the TV. Same Wi-Fi?')})}
function load(){fetch('/library',{headers:{'X-Token':T}}).then(function(r){return r.status==401?Promise.reject(401):r.json()})
  .then(function(d){draw('favs',d.favs,d.favs,'Tap the star on a recent link to save it.');
    draw('hist',d.history.map(function(h){return h.url}),d.favs,'Nothing sent yet.')})
  .catch(function(e){if(e===401)unpair()})}
function label(u){try{var x=new URL(u);return x.host+x.pathname}catch(e){return u}}
function draw(id,list,favs,empty){var box=g(id);box.textContent='';
  if(!list.length){var e=document.createElement('div');e.className='mut';e.textContent=empty;box.appendChild(e);return}
  list.forEach(function(u){var row=document.createElement('div');row.className='row';
    var b=document.createElement('button');b.className='lnk';b.textContent=label(u);b.onclick=function(){send(u)};
    var on=favs.indexOf(u)>=0,s=document.createElement('button');s.className='star';s.textContent=on?'\u2605':'\u2606';
    s.setAttribute('aria-label',on?'Remove favourite':'Save as favourite');
    s.onclick=function(){post('/fav','url='+encodeURIComponent(u)).then(load)};
    row.appendChild(b);row.appendChild(s);box.appendChild(row)})}
show();
</script></body></html>"""
