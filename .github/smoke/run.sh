#!/usr/bin/env bash
# Teste de fumaça: instala o APK no emulador, abre o app, navega pelas telas e mostra os crashes.
set -x
APK=android/app/build/outputs/apk/release/app-release.apk
PKG=com.abugdn.wid
tap() { python3 .github/smoke/tap.py "$1"; sleep "${2:-4}"; }
# O emulador padrão tem 320x640: coordenadas fixas (y=1500) caíam fora da tela. Tudo relativo ao tamanho real.
read -r W H < <(adb shell wm size | head -1 | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
echo "tela ${W}x${H}"
swipeup() { adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 400; sleep "${1:-2}"; }
# Reabre o app na frente e fecha o pop-up de novidades, se aparecer.
reopen() { adb shell am start -n $PKG/.ui.MainActivity; sleep 6; tap "Entendi" 2; }
# Abre uma ferramenta pela busca da tela Ferramentas.
tool() { tap "Hoje" 3; tap "Ferramentas" 3; tap "Buscar ferramenta" 2; adb shell input text "$1"; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 1; tap "$2" 5; }
shot() { adb exec-out screencap -p > "shots/$1.png"; }
mkdir -p shots
adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -n $PKG/.ui.MainActivity
sleep 45                      # primeira sincronização (feed + radar + tradução)
shot 01-abertura
tap "Fechar" 2; tap "Entendi" 3; tap "Depois" 2
shot 02-hoje
tap "Mapa" 6; shot 03-mapa
tap "Focos" 4; shot 04-focos
tap "Satélite" 5; shot 04b-satelite
python3 .github/smoke/tap.py "Focos" swipe; sleep 2
tap "Militares" 4; shot 04c-militares
tap "Porta-aviões" 4; shot 04d-porta-avioes
tap "Frente" 8; shot 04e-frente
# O mapa sai da tela e volta com as camadas ligadas (derrubava o app na 1.0.27).
tap "Tendência" 3; tap "Mapa" 6; shot 04f-mapa-de-volta
tap "Radar" 3; tap "Mapa" 6; shot 04g-mapa-de-volta-2
tap "Radar" 5; shot 05-radar
for t in Mercados "Números" Vozes; do tap "$t" 4; shot "06-radar-$t"; done
# Vozes termina com a Análise; Números com o Contexto (eram abas à parte até a 1.0.45).
for i in 1 2 3 4 5 6; do swipeup 1; done; shot 06b-radar-vozes-analise
tap "Números" 4
for i in 1 2 3 4 5 6 7 8; do swipeup 1; done; shot 06c-radar-numeros-contexto
tap "Israel" 6; shot 07-quem-manda
tap "Benjamin Netanyahu" 8; shot 08-cartao-pessoa
adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Irã" 6; shot 07b-quem-manda-ira
tap "Biblioteca" 4; shot 09-biblioteca
tap "Dossiês" 3; shot 09b-dossies
tap "Novo dossiê" 2; adb shell input text "Ira"; tap "Cancelar" 2
tap "Previsões" 3; shot 09c-previsoes
tap "Arquivo" 4; shot 09d-arquivo
python3 .github/smoke/tap.py "Previsões" swipe; sleep 2
tap "Lidas" 3; shot 09e-lidas
tap "Hoje" 4; shot 10-hoje-painel
tap "Ferramentas" 3; shot 11-ferramentas
tap "Linha de frente" 8; shot 12-ferramenta-frente
tap "Hoje" 3
tap "Ajustes" 3; shot 13-ajustes
tap "Tela Hoje" 3; shot 14-ajustes-tela-hoje
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2
# Os dois "voltar" acima fecham o app (o roteiro seguia na tela inicial do Android desde o passo 15):
# reabre antes de continuar.
reopen
tap "PRINCIPAL DO DIA" 6; shot 15-noticia   # abre a principal do dia
tap "Cobertura" 3; shot 16-noticia-cobertura
tap "Contexto" 3; shot 17-noticia-contexto
swipeup; shot 18-noticia-rolada
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input tap $((W/2)) $((H*2/3)); sleep 6; shot 19-noticia-lista   # uma notícia da lista
# Telas da 1.0.45: atenção do mundo, guerras esquecidas, gastos militares, arsenais nucleares,
# busca e grupos das Ferramentas, temas da tela Hoje.
adb shell input keyevent KEYCODE_BACK; sleep 2
reopen
tap "Hoje" 3; tap "Temas" 3; shot 20a-temas; adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Ferramentas" 3; shot 20b-ferramentas-grupos; adb shell input keyevent KEYCODE_BACK; sleep 2
tool "wikipedia" "Atenção do mundo"; shot 20-atencao
tap "Guerras esquecidas" 3; shot 21-esquecidas
swipeup; shot 21b-esquecidas-rolada
adb shell input keyevent KEYCODE_BACK; sleep 2
tool "gastos" "Gastos militares"; shot 22-gastos
tap "% do PIB" 3; shot 22b-gastos-pib
tap "Guerras e Brasil" 3; shot 22c-gastos-brasil
adb shell input keyevent KEYCODE_BACK; sleep 2
tool "ogivas" "Arsenais nucleares"; shot 23-nuclear
adb shell input keyevent KEYCODE_BACK; sleep 2
sleep 20
echo "=== processo vivo? ==="
adb shell pidof $PKG || echo "APP NÃO ESTÁ RODANDO"
echo "=== CRASHES ==="
adb logcat -d -b crash | tee crash.txt
echo "=== ERROS DO APP ==="
adb logcat -d -v time | grep -E "AndroidRuntime|FATAL|ANR in|$PKG" | grep -iE "exception|error|fatal|anr" | tail -80
adb logcat -d -v time > logcat.txt
# Falha o teste se o app caiu (o erro aparece acima, em CRASHES).
if [ -s crash.txt ] && grep -q "FATAL EXCEPTION" crash.txt; then exit 1; fi
exit 0
