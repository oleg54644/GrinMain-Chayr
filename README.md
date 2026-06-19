# GrinMain — Android Messenger

> **Kotlin/Java мессенджер с WebRTC звонками, группами и автоматической сборкой APK через GitHub Actions**

![Build Status](https://github.com/YOUR_USERNAME/GrinMain/workflows/Build%20%26%20Release%20GrinMain%20APK/badge.svg)

---

## Быстрый старт

### 1. Задать адрес сервера

Перед сборкой откройте `app/build.gradle` и поменяйте IP:
```groovy
buildConfigField "String", "DEFAULT_SERVER", "\"http://192.168.1.100:5000\""
```

### 2. Загрузить на GitHub и получить APK автоматически

```bash
git init
git add .
git commit -m "Initial GrinMain"
git remote add origin https://github.com/YOUR_USERNAME/GrinMain.git
git push -u origin main
```

GitHub Actions **автоматически** соберёт APK. Зайди в:
> **GitHub → Actions → Build & Release GrinMain APK → Artifacts**

Там будет файл `GrinMain-debug-xxxx.apk` — скачай и установи на телефон.

### 3. Для релизного APK с тегом версии

```bash
git tag v1.0.0
git push origin v1.0.0
```

Это создаст **GitHub Release** с APK файлом прямо для скачивания.

---

## Запуск сервера

```bash
cd server
pip install flask flask-socketio flask-cors requests python-socketio
python3 server.py
```

Сервер запустится на `http://0.0.0.0:5000`

---

## Функции приложения

| Функция | Описание |
|---|---|
| 🔐 **Авторизация** | Регистрация и вход по логину/паролю |
| 💾 **Автовход** | Токен + пароль сохраняются, вход без ввода |
| 🔒 **Привязка сервера** | После первого входа сервер вшит в приложение |
| 💬 **Личные чаты** | DM между пользователями |
| 👥 **Группы** | Создание групп с несколькими участниками |
| 📞 **Аудио звонки** | WebRTC P2P аудио без сервера |
| 🎥 **Видео звонки** | WebRTC P2P видео (фронталка + тыл) |
| 👥📞 **Групповые звонки** | Несколько участников одновременно |
| 🌐 **Без домена** | Работает в локальной сети по IP |
| 🟢 **Онлайн статусы** | Реальное время через WebSocket |

---

## Настройка подписи APK (опционально)

Для подписанного релизного APK добавь секреты в GitHub:
> **Settings → Secrets and variables → Actions**

| Секрет | Значение |
|---|---|
| `SIGNING_KEY` | Base64-encoded `.keystore` файл |
| `ALIAS` | Alias ключа |
| `KEY_STORE_PASSWORD` | Пароль keystore |
| `KEY_PASSWORD` | Пароль ключа |

Создать keystore:
```bash
keytool -genkey -v -keystore grinmain.keystore -alias grinmain -keyalg RSA -keysize 2048 -validity 10000
# Конвертировать в base64:
base64 -w 0 grinmain.keystore
```

---

## Структура проекта

```
GrinMain/
├── .github/workflows/
│   └── build.yml              # GitHub Actions — автосборка APK
├── app/src/main/
│   ├── java/com/grinmain/
│   │   ├── GrinMainApp.kt     # Application класс
│   │   ├── data/
│   │   │   ├── Prefs.kt       # Зашифрованные настройки
│   │   │   └── Models.kt      # Data классы
│   │   ├── network/
│   │   │   ├── ApiClient.kt   # REST API (OkHttp)
│   │   │   └── SocketManager.kt # WebSocket (Socket.IO)
│   │   ├── call/
│   │   │   └── WebRtcManager.kt # WebRTC звонки
│   │   └── ui/
│   │       ├── SplashActivity.kt
│   │       ├── AuthActivity.kt
│   │       ├── MainActivity.kt
│   │       ├── ChatActivity.kt
│   │       ├── CallActivity.kt
│   │       └── *Adapter.kt
│   └── res/                   # Layouts, drawables, values
├── server/
│   └── server.py              # Python Flask сервер
└── app/build.gradle           # ← Поменяй DEFAULT_SERVER здесь
```

---

## Технологии

- **Kotlin** — основной язык приложения  
- **Socket.IO Client** — реалтайм чат и сигналинг  
- **WebRTC (libwebrtc)** — P2P аудио/видео звонки  
- **OkHttp** — REST API запросы  
- **EncryptedSharedPreferences** — безопасное хранение токена  
- **GitHub Actions** — автосборка APK в облаке  
- **Python Flask** — лёгкий сервер с WebSocket  
