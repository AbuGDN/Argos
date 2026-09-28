#!/usr/bin/env bash
# Teste de fumaça: instala o APK no emulador, abre o app, navega pelas telas e mostra os crashes.
set -x
APK=android/app/build/outputs/apk/release/app-release.apk
PKG=com.abugdn.wid
tap() { python3 .github/smoke/tap.py "$1"; sleep "${2:-4}"; }
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
tap "Arquivo" 4; shot 09-arquivo
tap "Salvos" 3
tap "Dossiês" 3; shot 09b-dossies
tap "Novo dossiê" 2; adb shell input text "Ira"; tap "Cancelar" 2
tap "Previsões" 3; shot 09c-previsoes
tap "Hoje" 4
adb shell input tap 540 900; sleep 6; shot 10-noticia   # abre uma notícia da lista
adb shell input swipe 500 1600 500 700 300; sleep 2; shot 11-noticia-rolada
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
