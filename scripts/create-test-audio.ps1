param([string]$Destination = (Join-Path (Split-Path -Parent $PSScriptRoot) '.local-data\fixtures\prueba-es.wav'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$synthesizer = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $voice = $synthesizer.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name.StartsWith('es-') } | Select-Object -First 1
    if (-not $voice) { throw 'No se encontro una voz sintetica en espanol de Windows. Usa un WAV de prueba propio.' }
    $Destination = [IO.Path]::GetFullPath($Destination)
    New-Item -ItemType Directory -Path (Split-Path -Parent $Destination) -Force | Out-Null
    $synthesizer.SelectVoice($voice.VoiceInfo.Name)
    $synthesizer.Rate = -1
    $synthesizer.SetOutputToWaveFile($Destination)
    $synthesizer.Speak('Se reviso la bomba del taller. Se cambio la junta de goma. No se observaron fugas. No se midio presion. El equipo quedo operativo.')
    $synthesizer.SetOutputToNull()
    Write-Output "Audio sintetico guardado en $Destination"
} finally { $synthesizer.Dispose() }
