param([string]$Serial='emulator-5554', [switch]$SkipBuild, [string[]]$Feature=@())
& "$PSScriptRoot/test-suite.ps1" -Suite full -Serial $Serial -SkipBuild:$SkipBuild -Feature $Feature
