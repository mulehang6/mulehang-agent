1..5000 | ForEach-Object { Write-Output "out-$_"; [Console]::Error.WriteLine("err-$_") }
