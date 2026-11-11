param([string]$Path, [string]$OutTsv)
$src = Get-Content $Path -Raw -Encoding UTF8

$prev = $null
while ($prev -ne $src) {
    $prev = $src
    $src = [regex]::Replace($src, '"((?:[^"\\]|\\.)*)"\s*\+\s*\r?\n\s*"(?=(?:[^"\\]|\\.)*")', '"$1')
    $src = [regex]::Replace($src, '"((?:[^"\\]|\\.)*)"\s*\+\s*"(?=(?:[^"\\]|\\.)*")', '"$1')
}

$litRe = [regex]'"((?:[^"\\]|\\.)*)"'
$cyr = '[\u0430-\u044F\u0410-\u042F\u0451\u0401]'
$found = [ordered]@{}
$lines = New-Object System.Collections.Generic.List[string]
$ms = $litRe.Matches($src)
foreach ($m in $ms) {
    $text = $m.Groups[1].Value
    if ($text -notmatch $cyr) { continue }
    if ($text -match '\$\{') { continue }   # шаблоны - вручную
    if (-not $found.Contains($text)) {
        $key = 't{0:D3}' -f ($found.Count + 1)
        $found[$text] = $key
        $lines.Add("$key`t$text")
    }
}
foreach ($kv in $found.GetEnumerator()) {
    $needle = '"' + $kv.Key + '"'
    $repl = 'getString(R.string.' + $kv.Value + ')'
    $src = $src.Replace($needle, $repl)
}
Set-Content -Path $Path -Value $src -Encoding UTF8 -NoNewline
Set-Content -Path $OutTsv -Value ($lines -join "`n") -Encoding UTF8
"extracted: $($found.Count)"
