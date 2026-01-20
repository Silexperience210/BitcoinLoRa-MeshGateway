[app]
title = BitcoinMesh
package.name = bitcoinmesh
package.domain = org.silexperience
source.dir = .
source.include_exts = py,png,jpg,kv,atlas
version = 1.0.0
requirements = python3,kivy
orientation = portrait
fullscreen = 0
android.permissions = INTERNET
android.api = 31
android.minapi = 21
android.accept_sdk_license = True
android.arch = armeabi-v7a

# App icon
#icon.filename = %(source.dir)s/icon.png

# Presplash
#presplash.filename = %(source.dir)s/presplash.png

# Android specific
android.allow_backup = True

[buildozer]
log_level = 2
warn_on_root = 1
