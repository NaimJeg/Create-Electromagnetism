$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $projectRoot 'build/signal-check-classes'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/main/java/com/electromagnetism/core') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$sources += Join-Path $projectRoot 'src/verification/java/com/electromagnetism/core/signal/SignalChecks.java'
& javac --release 21 -encoding UTF-8 -d $outputDir @sources
if ($LASTEXITCODE -ne 0) { throw 'Signal compilation failed' }
& java -cp $outputDir com.electromagnetism.core.signal.SignalChecks
if ($LASTEXITCODE -ne 0) { throw 'Signal checks failed' }
