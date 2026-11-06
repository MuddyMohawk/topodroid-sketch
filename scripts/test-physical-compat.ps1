param([string]$Serial='', [switch]$SkipBuild)
if([string]::IsNullOrWhiteSpace($Serial)){throw 'physical requires an explicit -Serial; no tablet is selected automatically.'}
# Explicit target only. Installed Vanilla is required; no private or public resets.
& "$PSScriptRoot/test-suite.ps1" -Suite physical -PhysicalSerial $Serial -SkipBuild:$SkipBuild
