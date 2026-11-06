param([string]$Serial='emulator-5554', [switch]$SkipBuild)
& "$PSScriptRoot/test-suite.ps1" -Suite diagnostics -Serial $Serial -SkipBuild:$SkipBuild -Selector 'com.topodroid.TDX.RenderPerfInstrumentedTest'
