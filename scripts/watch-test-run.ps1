param(
  [Parameter(Mandatory = $true)]
  [string]$Log,
  [string]$PidFile = "",
  [int]$IntervalSeconds = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not [System.IO.Path]::IsPathRooted($Log)) {
  $Log = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Log))
}
if (-not [string]::IsNullOrWhiteSpace($PidFile) -and -not [System.IO.Path]::IsPathRooted($PidFile)) {
  $PidFile = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $PidFile))
}

function Get-RunProcess {
  if ([string]::IsNullOrWhiteSpace($PidFile) -or -not (Test-Path $PidFile)) {
    return $null
  }

  $pidText = (Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
  if ([string]::IsNullOrWhiteSpace($pidText)) {
    return $null
  }

  $runPid = 0
  if (-not [int]::TryParse($pidText.Trim(), [ref]$runPid)) {
    return $null
  }

  return Get-Process -Id $runPid -ErrorAction SilentlyContinue
}

function Write-NewLogLines {
  param([ref]$LineOffset)

  if (-not (Test-Path $Log)) {
    return 0
  }

  $lines = @(Get-Content -LiteralPath $Log)
  if ($lines.Count -le $LineOffset.Value) {
    return 0
  }

  for ($i = $LineOffset.Value; $i -lt $lines.Count; $i++) {
    Write-Host $lines[$i]
  }
  $written = $lines.Count - $LineOffset.Value
  $LineOffset.Value = $lines.Count
  return $written
}

Write-Host "Watching test log: $Log"
if (-not [string]::IsNullOrWhiteSpace($PidFile)) {
  Write-Host "PID file: $PidFile"
}

$lineOffset = 0
$lastOutputAt = Get-Date
while ($true) {
  $newLines = Write-NewLogLines -LineOffset ([ref]$lineOffset)
  if ($newLines -gt 0) {
    $lastOutputAt = Get-Date
  }

  $process = Get-RunProcess
  if ($null -eq $process -and -not [string]::IsNullOrWhiteSpace($PidFile) -and (Test-Path $PidFile)) {
    $null = Write-NewLogLines -LineOffset ([ref]$lineOffset)
    Write-Host ("[{0}] Test process is no longer active." -f (Get-Date -Format "HH:mm:ss"))
    break
  }

  $idleFor = ((Get-Date) - $lastOutputAt).TotalSeconds
  $processLabel = if ($null -eq $process) { "unknown process" } else { "PID $($process.Id)" }
  Write-Host ("[{0}] WATCH {1}: no new log lines for {2:n0}s" -f (Get-Date -Format "HH:mm:ss"), $processLabel, $idleFor)
  Start-Sleep -Seconds $IntervalSeconds
}
