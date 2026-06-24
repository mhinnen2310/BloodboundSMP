$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$packPath = Join-Path $root "BloodboundSMP-resourcepack.zip"
if (!(Test-Path -LiteralPath $packPath)) {
    throw "Resource pack not found: $packPath"
}

$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 8123)
$listener.Start()
Write-Host "Bloodbound resource pack: http://127.0.0.1:8123/BloodboundSMP-resourcepack.zip"

try {
    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            $stream = $client.GetStream()
            $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::ASCII, $false, 1024, $true)
            $requestLine = $reader.ReadLine()
            while (($line = $reader.ReadLine()) -ne $null -and $line.Length -gt 0) {
            }

            if ($requestLine -match '^GET /BloodboundSMP-resourcepack\.zip(?:\?.*)? HTTP/') {
                $bytes = [IO.File]::ReadAllBytes($packPath)
                $header = "HTTP/1.1 200 OK`r`nContent-Type: application/zip`r`nContent-Length: $($bytes.Length)`r`nCache-Control: no-cache`r`nConnection: close`r`n`r`n"
                $headerBytes = [Text.Encoding]::ASCII.GetBytes($header)
                $stream.Write($headerBytes, 0, $headerBytes.Length)
                $stream.Write($bytes, 0, $bytes.Length)
            } else {
                $body = [Text.Encoding]::UTF8.GetBytes("Not found")
                $header = "HTTP/1.1 404 Not Found`r`nContent-Type: text/plain`r`nContent-Length: $($body.Length)`r`nConnection: close`r`n`r`n"
                $headerBytes = [Text.Encoding]::ASCII.GetBytes($header)
                $stream.Write($headerBytes, 0, $headerBytes.Length)
                $stream.Write($body, 0, $body.Length)
            }
            $stream.Flush()
        } finally {
            $client.Dispose()
        }
    }
} finally {
    $listener.Stop()
}
