package dev.linktotv

/** Sender page served by the TV, so an iPhone (or any browser) needs nothing installed. */
const val PAGE = """<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="apple-mobile-web-app-capable" content="yes"><title>Link to TV</title>
<style>
body{font:18px -apple-system,system-ui,sans-serif;margin:0;padding:24px;background:#101820;color:#f2f5f7}
h1{font-size:28px;margin:0 0 20px}input,button{font:inherit;width:100%;padding:14px;border-radius:12px;border:0;margin:8px 0;box-sizing:border-box}
input{background:#1d2a36;color:#fff}button{background:#4cc38a;color:#06210f;font-weight:600}
#s{margin-top:16px;color:#a9b8c4;min-height:24px}
</style></head><body>
<h1>Link to TV</h1>
<div id="pair"><p>First time: type the code shown on your TV.</p>
<input id="code" inputmode="numeric" placeholder="6-digit code on the TV">
<button onclick="pairIt()">Pair</button></div>
<input id="url" type="url" placeholder="Paste a video link (.mp4, .m3u8 ...)">
<button onclick="sendIt()">Send to TV</button>
<div id="s"></div>
<script>
var T=localStorage.getItem('tok');
function say(m){document.getElementById('s').textContent=m}
function show(){document.getElementById('pair').style.display=T?'none':'block'}
function post(p,b,h){var hd={'Content-Type':'application/x-www-form-urlencoded'};for(var k in (h||{}))hd[k]=h[k];
  return fetch(p,{method:'POST',headers:hd,body:b})}
function pairIt(){var c=document.getElementById('code').value.trim();
  post('/pair','code='+encodeURIComponent(c)).then(function(r){return r.ok?r.json():Promise.reject()})
  .then(function(j){T=j.token;localStorage.setItem('tok',T);show();say('Paired. You can send links now.')})
  .catch(function(){say('Wrong code. Check the TV screen and try again.')})}
function sendIt(){var u=document.getElementById('url').value.trim();
  if(!/^https?:\/\/\S+${'$'}/.test(u)){say('Enter a link starting with http:// or https://');return}
  if(!T){say('Pair with the TV first.');return}
  post('/play','url='+encodeURIComponent(u),{'X-Token':T}).then(function(r){
    if(r.ok)say('Sent. It should start on the TV.');
    else if(r.status==401){localStorage.removeItem('tok');T=null;show();say('The TV forgot this phone. Pair again.')}
    else say('Could not send ('+r.status+').')}).catch(function(){say('Could not reach the TV. Same Wi-Fi?')})}
show();
</script></body></html>"""
