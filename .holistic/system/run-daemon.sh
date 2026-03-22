#!/usr/bin/env sh
cd 'C:\Users\lweis\Documents\newsthread' || exit 1
'C:\Users\lweis\Documents\newsthread\.holistic\system\restore-state.sh' || true
'C:\Program Files\nodejs\node.exe' 'C:\Users\lweis\Documents\holistic\dist\daemon.js' --interval 30 --agent unknown
