"""Check a wired 2Hz microphone/scope on a connected development client."""
import argparse
import json
from pathlib import Path
import time
import urllib.request
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument("--endpoint", default="http://127.0.0.1:25587/mcp")
parser.add_argument("--position", nargs=3, type=int, default=[-6, 90, -4])
args = parser.parse_args()
if not args.endpoint.startswith("http://127.0.0.1:"):
    raise SystemExit("Only a local development client is supported")

def evaluate(code):
    payload = {"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {
        "name": "eval_js", "arguments": {"side": "client", "code": code}}}
    request = urllib.request.Request(args.endpoint, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        result = json.load(response)
    result = json.loads(result["result"]["content"][0]["text"])
    if not result.get("ok"):
        raise RuntimeError(result)
    return str(result["value"])

x,y,z = args.position
setup = f"var mc=Java.loadClass('net.minecraft.client.Minecraft').getInstance();var pos=new (Java.loadClass('net.minecraft.core.BlockPos'))({x},{y},{z});var instrument=mc.level.getBlockEntity(pos);"
evaluate(setup+"mc.setScreen(new (Java.loadClass('com.electromagnetism.client.OscilloscopeScreen'))(instrument));'opened'")
time.sleep(2)
report = json.loads(evaluate("var samples=instrument.samples();var measures=Java.loadClass('com.electromagnetism.core.signal.WaveformMeasurements');var stats=measures.statistics(samples);var hz=measures.frequency(samples,0,true);JSON.stringify({samples:samples.size(),rms:stats.isPresent()?stats.get().rms():-1,hz:hz.isPresent()?hz.getAsDouble():-1,width:mc.screen.width,height:mc.screen.height})"))
assert 2 <= report['samples'] <= 1024, report
assert abs(report['hz']-2) < 0.05 and 0.02 < report['rms'] < 0.05, report
output = Path(__file__).resolve().parent.parent / "build/reports"
output.mkdir(parents=True, exist_ok=True)

def screenshot(name):
    path = output / name
    evaluate("var image=Java.loadClass('net.minecraft.client.Screenshot').takeScreenshot(mc.getMainRenderTarget());image.writeToFile(Java.loadClass('java.nio.file.Path').of("+json.dumps(path.as_posix())+"));image.close();'saved'")
    return path

screenshot('scope-live.png')
w,h = report['width'],report['height']
pw,ph = min(480,w-20),min(310,h-12)
left,top = (w-pw)//2,(h-ph)//2
plot_height = max(40,ph-152)
row = top+32+plot_height+48
clicked = evaluate(f"mc.screen.children().getFirst().mouseClicked({left+12},{row+51},0)")
assert clicked == 'true', clicked
time.sleep(1.5)
a=screenshot('scope-triggered.png')
time.sleep(0.3)
b=screenshot('scope-triggered-repeat.png')
with Image.open(a) as ia, Image.open(b) as ib:
    sx,sy=ia.width/w,ia.height/h
    region=(round((left+42)*sx),round((top+32)*sy),round((left+pw-16)*sx),round((top+32+plot_height)*sy))
    assert ia.crop(region).tobytes()==ib.crop(region).tobytes(), 'Triggered waveform did not hold'
report['triggerHeld']=True
(output/'client-checks.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report),flush=True)
