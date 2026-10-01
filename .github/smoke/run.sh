#!/usr/bin/env bash
# Teste de fumaça: instala o APK no emulador, abre o app, navega pelas telas e mostra os crashes.
set -x
APK=android/app/build/outputs/apk/release/app-release.apk
PKG=com.abugdn.wid
tap() { python3 .github/smoke/tap.py "$1"; sleep "${2:-4}"; }
# Rola a tela para baixo até achar o texto (lista longa, como Ferramentas) e toca nele.
scrolltap() { for i in 1 2 3 4 5 6 7 8 9 10; do python3 .github/smoke/tap.py "$1" && { sleep "${2:-5}"; return; }; adb shell input swipe 500 1500 500 700 300; sleep 1; done; echo "NAO ACHOU $1"; }
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
for t in Mercados "Números" Vozes "Análise" Contexto; do tap "$t" 4; shot "06-radar-$t"; done
adb shell input swipe 500 1800 500 500 400; sleep 2
adb shell input swipe 500 1800 500 500 400; sleep 2
adb shell input swipe 500 1800 500 400 400; sleep 2
adb shell input swipe 500 1800 500 400 400; sleep 2
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
tap "PRINCIPAL DO DIA" 6; shot 15-noticia   # abre a principal do dia
tap "Cobertura" 3; shot 16-noticia-cobertura
tap "Contexto" 3; shot 17-noticia-contexto
adb shell input swipe 500 1600 500 700 300; sleep 2; shot 18-noticia-rolada
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input tap 540 1700; sleep 6; shot 19-noticia-lista   # uma notícia da lista
adb shell input keyevent KEYCODE_BACK; sleep 2
# Telas da 1.0.45: atenção do mundo, guerras esquecidas, gastos militares, arsenais nucleares.
tap "Hoje" 3; tap "Ferramentas" 3
scrolltap "Atenção do mundo"; shot 20-atencao
tap "Guerras esquecidas" 3; shot 21-esquecidas
adb shell input swipe 500 1600 500 600 300; sleep 2; shot 21b-esquecidas-rolada
adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Hoje" 3; tap "Ferramentas" 3
scrolltap "Gastos militares"; shot 22-gastos
tap "% do PIB" 3; shot 22b-gastos-pib
tap "Guerras e Brasil" 3; shot 22c-gastos-brasil
adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Hoje" 3; tap "Ferramentas" 3
scrolltap "Arsenais nucleares"; shot 23-nuclear
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
