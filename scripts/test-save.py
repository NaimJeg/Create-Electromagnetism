"""Prepare a heater checkpoint, restart the dev server, then verify disk persistence/cooling."""
import argparse
import json
import math
from pathlib import Path
import urllib.request

parser=argparse.ArgumentParser()
parser.add_argument("action",choices=["prepare","verify"])
parser.add_argument("--endpoint",default="http://127.0.0.1:25586/mcp")
args=parser.parse_args()
if not args.endpoint.startswith("http://127.0.0.1:"):
    raise SystemExit("Only a local development server is supported")
root=Path(__file__).resolve().parent.parent / "build/reports"
root.mkdir(parents=True,exist_ok=True)
record=root/'save-checkpoint.json'

def evaluate(code):
    p={"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"eval_js","arguments":{"side":"server","code":code}}}
    r=urllib.request.Request(args.endpoint,data=json.dumps(p).encode(),headers={"Content-Type":"application/json"})
    with urllib.request.urlopen(r,timeout=20) as response: result=json.load(response)
    text=json.loads(result['result']['content'][0]['text'])
    if not text.get('ok'): raise RuntimeError(text)
    return str(text['value'])

base="var s=Java.loadClass('net.neoforged.neoforge.server.ServerLifecycleHooks').getCurrentServer();var pos=new (Java.loadClass('net.minecraft.core.BlockPos'))(-2,150,8);var sd=Java.loadClass('com.george_vi.electroenergetics.devices.device.DevicesSavedData').load(s.overworld());var device=sd.getDevice(pos);var tag=new (Java.loadClass('net.minecraft.nbt.CompoundTag'))();"
if args.action=='prepare':
    result=json.loads(evaluate(base+"if(device==null)throw 'Place a fresh heater at -2,150,8 first';tag.putFloat('Temperature',100);device.read(tag);device.write(tag);var config=Java.loadClass('com.electromagnetism.config.ModConfigs').server();s.saveEverything(true,false,true);s.halt(false);JSON.stringify({tick:s.overworld().getGameTime(),temperature:tag.getFloat('Temperature'),capacity:config.heatingCoilMass.getF(),conductance:config.heatingCoilMaxPower.getF()/(600-22)})"))
    record.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print('saved checkpoint and stopped dev server; restart before verify',result)
else:
    before=json.loads(record.read_text(encoding='utf-8'))
    after=json.loads(evaluate(base+"if(device==null)throw 'Saved heater not restored';device.write(tag);JSON.stringify({tick:s.overworld().getGameTime(),temperature:tag.getFloat('Temperature')})"))
    elapsed=(after['tick']-before['tick'])/20
    assert elapsed>=0,(before,after)
    expected=22+(before['temperature']-22)*math.exp(-before['conductance']/before['capacity']*elapsed)
    tolerance=0.1+abs(before['temperature']-22)*(1-math.exp(-before['conductance']/before['capacity']/20))
    assert abs(after['temperature']-expected)<=tolerance,(before,after,expected)
    evaluate(base+"sd.removeDevice(pos);s.overworld().setBlockAndUpdate(pos,Java.loadClass('net.minecraft.world.level.block.Blocks').AIR.defaultBlockState());'cleaned'")
    result={"before":before,"after":after,"expectedTemperature":expected,"passed":True}
    (root/'save-checks.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result))
