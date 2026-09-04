import subprocess, os, time

AB = "C:/Users/asvs/.workbuddy/binaries/node/workspace/node_modules/.bin/agent-browser.cmd"
ENV = dict(os.environ)
ENV["AGENT_BROWSER_EXECUTABLE_PATH"] = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"

JS_CLICK = "(function(){var b=[].slice.call(document.querySelectorAll('button')).find(function(x){return x.textContent.trim()==='加入购物车';}); if(!b) return 'NO_BUTTON'; b.click(); return 'CLICKED';})()"
JS_READ = "(function(){var a=document.querySelector('.alert-modal'); if(!a) return 'NO_ALERT'; var h=a.querySelector('h3'); var m=a.querySelector('.modal-message'); return 'ALERT|'+(h?h.textContent:'')+'|'+(m?m.textContent:'');})()"
JS_HAS_ALERT = "(function(){return document.querySelector('.alert-modal')?'YES':'NO';})()"

def run(args):
    p = subprocess.run([AB]+args, env=ENV, capture_output=True, text=True, timeout=90)
    return p.stdout.strip()

print("open:", run(["open", "http://localhost:5173/"]))
time.sleep(1.2)
print("click:", run(["eval", JS_CLICK]))
time.sleep(0.8)
print("read:", run(["eval", JS_READ]))
print("close:", run(["close"]))
