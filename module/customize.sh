#!/system/bin/sh
#
# Magisk / KSU customize.sh
# Installs the bundled LSPOSED companion APK (Nezha Leica Enabler) during flash.
# The system props are applied by Magisk/KSU from system.prop automatically.
#
# NOTE: flashing runs inside Android (Magisk app / KSU-Next), where `pm` exists,
# so the APK installs at flash time. Recovery flashing has no `pm` and will skip
# the APK install (props still apply).

APK="$MODPATH/NezhaLeicaEnabler.apk"

ui_print "- Nezha Leica Enabler"
ui_print "- Applying Leica system properties"

if [ -f "$APK" ]; then
  ui_print "- Installing LSPOSED module APK (Nezha Leica Enabler)"
  if command -v pm >/dev/null 2>&1 && pm install -r -g "$APK" >/dev/null 2>&1; then
    ui_print "  APK installed."
    ui_print "  >> Open LSPosed and enable 'Nezha Leica Enabler',"
    ui_print "  >> fixed scope: Camera + Gallery editor + Settings. Reboot."
    rm -f "$APK"
  else
    ui_print "! APK auto-install failed (possible signature mismatch)."
    ui_print "! Install it manually after boot:"
    ui_print "!   /data/adb/modules/$MODID/NezhaLeicaEnabler.apk"
    ui_print "! If APK signatures differ, uninstall the OLD app manually, then install this APK."
    ui_print "! Re-enable LSPosed; fixed scope: Camera + Gallery editor + Settings."
  fi
else
  ui_print "! Bundled APK not found — props-only install."
fi
