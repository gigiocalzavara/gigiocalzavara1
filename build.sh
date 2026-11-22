#!/bin/sh
# Gera index.html (documento completo) a partir de src/app.html
cd "$(dirname "$0")"
{ printf '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="theme-color" content="#0e1115"><style>:root{padding-top:env(safe-area-inset-top);padding-bottom:env(safe-area-inset-bottom)}body{margin:0}img{max-width:100%%}[hidden]{display:none!important}</style></head><body>\n'; cat src/app.html; printf '\n</body></html>\n'; } > index.html
