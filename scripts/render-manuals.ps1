# LFTY JGTM SCGC YARS DMFY — Native Word export for Windows without LibreOffice.
param([string]$Directory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'documentacion'))
. (Join-Path $PSScriptRoot 'toolchain.ps1')
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    foreach ($file in (Get-ChildItem -LiteralPath $Directory -Filter '*.docx')) {
        $output = Join-Path $VoxoraRoot ('target\manual-qa\' + $file.BaseName)
        New-Item -ItemType Directory -Path $output -Force | Out-Null
        $document = $word.Documents.Open($file.FullName, $false, $true)
        try { $document.ExportAsFixedFormat((Join-Path $output ($file.BaseName + '.pdf')), 17) }
        finally { $document.Close(0) }
        & (Join-Path $env:JAVA_HOME 'bin\java.exe') -cp (Join-Path $VoxoraRoot 'target\lib\*') (Join-Path $PSScriptRoot 'RenderPdf.java') (Join-Path $output ($file.BaseName + '.pdf')) $output
        if ($LASTEXITCODE -ne 0) { throw 'No se pudieron renderizar las paginas.' }
    }
} finally { $word.Quit() }
