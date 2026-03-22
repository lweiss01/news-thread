$ErrorActionPreference = 'Stop'
$node = 'C:\Program Files\nodejs\node.exe'
$daemon = 'C:\Users\lweis\Documents\holistic\dist\daemon.js'
$working = 'C:\Users\lweis\Documents\newsthread'
& 'C:\Users\lweis\Documents\newsthread\.holistic\system\restore-state.ps1'
& $node $daemon --interval 30 --agent unknown
