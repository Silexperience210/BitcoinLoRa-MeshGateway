# 📱 BitcoinMesh Android App

> **Envoi direct de transactions Bitcoin via Bluetooth vers T-Beam LoRa**

## 🎨 Design

- **Thème sombre élégant** avec accents orange néon (#FF6B00)
- **Interface minimaliste** : coller la TX → cliquer → envoyé
- **Effets électriques** sur les boutons et composants

## 📋 Fonctionnalités

1. **Connexion Bluetooth** directe au T-Beam Meshtastic
2. **Découpage automatique** des transactions en chunks de 190 caractères
3. **Envoi séquentiel** avec délai de 3 secondes entre chaque chunk
4. **Progress bar** avec statut en temps réel
5. **Logs colorés** pour suivre la progression

## 🔧 Compilation

### Prérequis

- Android Studio Hedgehog (2023.1.1) ou plus récent
- JDK 17
- Android SDK 34

### Étapes

1. Ouvrir le dossier `android/` dans Android Studio
2. Attendre la synchronisation Gradle
3. Build > Build Bundle(s) / APK(s) > Build APK(s)
4. L'APK sera dans `app/build/outputs/apk/debug/`

### Compilation en ligne de commande

```bash
cd android
./gradlew assembleDebug
```

## 📲 Installation

1. Activer "Sources inconnues" sur Android
2. Transférer l'APK sur le téléphone
3. Installer et accepter les permissions Bluetooth

## 🚀 Utilisation

1. **Activer Bluetooth** sur le téléphone
2. **Appairer le T-Beam** dans les paramètres Bluetooth Android
3. **Ouvrir BitcoinMesh**
4. **Sélectionner** le T-Beam dans le menu déroulant
5. **Cliquer "Connecter"**
6. **Coller** la transaction Bitcoin (hex)
7. **Cliquer "⚡ ENVOYER SUR LORA"**
8. Attendre que tous les chunks soient envoyés ✅

## 🔌 Protocole

L'app envoie via Bluetooth SPP (Serial Port Profile) :

```
BTX:1/3:chunk_1_data...
BTX:2/3:chunk_2_data...
BTX:3/3:chunk_3_data...
```

Le T-Beam reçoit les messages et les transmet sur le mesh LoRa via le module `BitcoinTxModule`.

## ⚠️ Notes

- Le T-Beam doit avoir le firmware Meshtastic avec BitcoinTxModule
- Chaque chunk fait max 190 caractères
- Délai de 3 secondes entre chaque envoi pour éviter la congestion
- Les transactions longues peuvent prendre plusieurs minutes

## 📄 License

MIT - By @ProfEduStream / Silexperience
# Auto-build trigger
