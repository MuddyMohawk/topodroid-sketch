param([string]$Serial='emulator-5554', [switch]$SkipBuild, [string[]]$Feature=@())
& "$PSScriptRoot/test-suite.ps1" -Suite fast -Serial $Serial -SkipBuild:$SkipBuild -Feature $Feature
