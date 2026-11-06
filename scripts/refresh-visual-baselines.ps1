param(
  [string]$Serial='emulator-5554', [switch]$SkipBuild,
  [string[]]$Selector=@(), [string]$CandidateDirectory='', [switch]$Apply
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$goldens=Join-Path $repo 'app/src/androidTest/assets/goldens/emulator_2560x1600_320dpi_font1.0'
if ($Apply) {
  if (!$CandidateDirectory) { throw '-Apply requires a reviewed -CandidateDirectory; it never records new images.' }
  $manifest=Get-Content (Join-Path $CandidateDirectory 'baseline-manifest.json') -Raw | ConvertFrom-Json
  if (!$manifest -or @($manifest).Count -eq 0) {throw 'No approved stable candidates.'}
  foreach ($entry in $manifest) {
    if ($entry.name -notmatch '^[A-Za-z0-9_.-]+[.]png$' -or !$entry.stable) {throw 'Invalid or unstable candidate'}
    $candidate=Join-Path $CandidateDirectory $entry.name
    if ((Get-FileHash $candidate -Algorithm SHA256).Hash -ne $entry.sha256) {throw "Candidate changed since review: $candidate"}
    $target=Join-Path $goldens $entry.name
    if ((Get-FileHash $target -Algorithm SHA256).Hash -ne $entry.originalSha256) {throw "Approved asset changed since staging: $target"}
  }
  foreach ($entry in $manifest) {Copy-Item -LiteralPath (Join-Path $CandidateDirectory $entry.name) -Destination (Join-Path $goldens $entry.name) -Force}
  Write-Host "Applied $(@($manifest).Count) explicitly reviewed stable candidates."
  return
}
if (!$CandidateDirectory) {$CandidateDirectory=Join-Path $repo ('workbench/artifacts/baseline-candidates-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))}
$CandidateDirectory=[IO.Path]::GetFullPath($CandidateDirectory)
. "$PSScriptRoot/test-suite-common.ps1"
Assert-QaEmptyOutputDirectory $CandidateDirectory
if(!(Get-Command python -ErrorAction SilentlyContinue)){throw 'Python 3 with Pillow is required to review baseline candidates.'}
& python -c 'from PIL import Image, ImageChops, ImageFile; assert hasattr(Image.new("RGB",(1,1)),"get_flattened_data")'
if($LASTEXITCODE -ne 0){throw 'The baseline review runtime requires Python 3 with a current Pillow installation.'}
New-Item -ItemType Directory -Force $CandidateDirectory | Out-Null
$run=Join-Path $CandidateDirectory 'run'
$failure=$null
try {& "$PSScriptRoot/test-suite.ps1" -Suite refresh -Authoring -Serial $Serial -SkipBuild:$SkipBuild -Selector $Selector -OutputDirectory $run} catch {$failure=$_}
$entries=@()
$authored=@{}
foreach($log in @(Get-ChildItem $run -File -Filter '*.instrumentation.log')) {
  foreach($match in [regex]::Matches((Get-Content $log.FullName -Raw),'QA_BASELINE\|([^|\r\n]+)\|([^\r\n]+)')) {
    $authored[$match.Groups[2].Value+'/'+$match.Groups[1].Value]=$true
  }
}
# Three identical captures are required for each screen independently. A later
# unrelated export assertion cannot invalidate already validated screen structure.
foreach ($file in @(Get-ChildItem $run -Recurse -File -Filter '*.stable-3.png')) {
  $name=$file.Name -replace '[.]stable-3[.]png$',''
  if(!$authored.ContainsKey($file.Directory.Name+'/'+$name)){continue}
  $siblings=@(1..3 | ForEach-Object {Join-Path $file.DirectoryName ($name+".stable-$_.png")})
  if (@($siblings | Where-Object {!(Test-Path -LiteralPath $_)}).Count) {continue}
  $hashes=@($siblings | ForEach-Object {(Get-FileHash $_ -Algorithm SHA256).Hash} | Select-Object -Unique)
  if ($hashes.Count -ne 1) {continue}
  $validated=@(Get-ChildItem $run -Recurse -File -Filter $name | Where-Object {$_.FullName -match '[\\/]recorded-goldens[\\/]' -and (Get-FileHash $_.FullName -Algorithm SHA256).Hash -eq $hashes[0]})
  if (!$validated.Count) {continue}
  if (!(Test-Path (Join-Path $goldens $name))) {continue}
  Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $CandidateDirectory $name)
  $entries+=[pscustomobject]@{name=$name;sha256=$hashes[0];originalSha256=(Get-FileHash (Join-Path $goldens $name) -Algorithm SHA256).Hash;stable=$true;source=$file.FullName}
}
$entries | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $CandidateDirectory 'baseline-manifest.json')
if ($entries.Count -gt 0) {& python "$PSScriptRoot/compare-baseline-candidates.py" $goldens $CandidateDirectory; if($LASTEXITCODE -ne 0){throw 'Candidate diff generation failed'}}
Write-Host "Staged $($entries.Count) stable candidates: $CandidateDirectory. Review images/diffs, then explicitly use -Apply -CandidateDirectory."
if ($failure) {throw $failure}
if (!$entries.Count) {throw 'No stable candidates were staged.'}
