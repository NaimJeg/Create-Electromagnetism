r"""Run dev-only fixtures against an already-running local AutoJS server endpoint.
Start .\gradlew.bat runServer first; take the endpoint URL from that server's log.
"""
import argparse
import json
from pathlib import Path
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--endpoint", default="http://127.0.0.1:25586/mcp")
args = parser.parse_args()
if not args.endpoint.startswith("http://127.0.0.1:"):
    raise SystemExit("Only a local development server is supported")

def evaluate(code):
    payload = {"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {
        "name": "eval_js", "arguments": {"side": "server", "code": code}}}
    request = urllib.request.Request(args.endpoint, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        result = json.load(response)
    text = json.loads(result["result"]["content"][0]["text"])
    if not text.get("ok"):
        raise RuntimeError(text)
    return str(text["value"])

entry = "Java.loadClass('com.electromagnetism.integration.cee.WorldChecks')"
print(evaluate(entry + ".start(Java.loadClass('net.neoforged.neoforge.server.ServerLifecycleHooks').getCurrentServer())"), flush=True)
deadline = time.monotonic() + 90
while time.monotonic() < deadline:
    status = evaluate(entry + ".status()")
    if status != "running":
        report = Path(__file__).resolve().parent.parent / "build/reports/world-checks.json"
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps({"status": status}, indent=2), encoding="utf-8")
        print(status, flush=True)
        raise SystemExit(0 if status.startswith("passed:") else 1)
    time.sleep(1)
raise SystemExit("Timed out waiting for world checks")
