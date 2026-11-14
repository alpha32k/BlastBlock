param([string]$OutputDirectory = (Join-Path $PSScriptRoot '../app/src/main/res/raw'))

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

function Write-Effect([string]$Name, [double[]]$Notes, [double]$Length, [double]$Spacing) {
    $sampleRate = 22050
    $count = [int][Math]::Ceiling(($Length + ($Notes.Length - 1) * $Spacing) * $sampleRate)
    $stream = [IO.File]::Create((Join-Path $OutputDirectory "$Name.wav"))
    $writer = [IO.BinaryWriter]::new($stream)
    try {
        $writer.Write([Text.Encoding]::ASCII.GetBytes('RIFF'))
        $writer.Write([int](36 + 2 * $count))
        $writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
        $writer.Write([int]16)
        $writer.Write([int16]1)
        $writer.Write([int16]1)
        $writer.Write([int]$sampleRate)
        $writer.Write([int](2 * $sampleRate))
        $writer.Write([int16]2)
        $writer.Write([int16]16)
        $writer.Write([Text.Encoding]::ASCII.GetBytes('data'))
        $writer.Write([int](2 * $count))
        for ($i = 0; $i -lt $count; $i++) {
            $value = 0.0
            for ($n = 0; $n -lt $Notes.Length; $n++) {
                $time = $i / $sampleRate - $n * $Spacing
                if ($time -lt 0 -or $time -ge $Length) { continue }
                $phase = 2 * [Math]::PI * $Notes[$n] * $time
                $envelope = [Math]::Min($time / .004, 1) * [Math]::Pow(1 - $time / $Length, 2)
                $value += $envelope * ([Math]::Sin($phase) + .18 * [Math]::Sin(2 * $phase))
            }
            $writer.Write([int16][Math]::Round(9200 * $value))
        }
    } finally {
        $writer.Dispose()
    }
}

Write-Effect 'block_placed' @(349.23) .07 0
Write-Effect 'line_clear' @(523.25, 659.25, 783.99) .13 .045
Write-Effect 'line_combo' @(659.25, 987.77, 1318.51) .16 .072
Write-Effect 'game_lost' @(392.00, 293.66, 196.00) .24 .11
