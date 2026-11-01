# Политика конфиденциальности / Privacy Policy

**Приложение:** Кӗсье Тӳме / кТӳме (`Kĕsje Tüme / kTüme`, `org.chuvash.ktume`)
**Исходный код и обратная связь:** https://github.com/vutnar21-cmd/ktume-keyboard
**Последнее обновление:** 2026-11-01

## Русский

Клавиатура **«Кӗсье Тӳме» (кТӳме)** работает полностью локально на устройстве пользователя и не собирает,
не хранит на внешних серверах и никому не передаёт никакие персональные или пользовательские данные.

1. **Отсутствие доступа к сети.** Приложение не запрашивает системное разрешение `android.permission.INTERNET`
   и физически не может отправлять набранный текст, содержимое буфера обмена или настройки по сети.
2. **Никакой аналитики и трекеров.** В приложении нет рекламных, аналитических или облачных SDK.
3. **Локальные настройки.** Настройки оформления и пользовательские раскладки сохраняются исключительно
   во внутреннем хранилище приложения на вашем устройстве (Android Jetpack DataStore).
4. **Буфер обмена.** При нажатии пользователем кнопки буфера обмена текст читается только для локального
   отображения на верхней панели клавиатуры и вставки в активное поле ввода; он никуда не сохраняется
   и не передаётся.
5. **Разрешения.** Единственное запрашиваемое разрешение — `android.permission.VIBRATE` (используется
   исключительно для тактильного отклика при нажатии клавиш и может быть отключено в настройках).

---

## English

**Kĕsje Tüme / kTüme** (`org.chuvash.ktume`) operates 100% locally on your device. It does not collect,
transmit, share, or store any personal data or typed text on external servers.

1. **No Network Access.** The application does not request the `android.permission.INTERNET` permission
   and is technically incapable of transmitting typed characters, clipboard items, or preferences over the network.
2. **No Analytics or Trackers.** The app contains zero advertising, analytics, telemetry, or proprietary cloud SDKs.
3. **Local Preferences Only.** Layout customizations and theme settings are stored solely inside the app's
   private on-device storage (Android Jetpack DataStore).
4. **Clipboard.** Clipboard content is accessed on-device only when you explicitly tap the clipboard button
   or paste action; it is never logged, persisted, or transmitted anywhere.
5. **Permissions.** The only requested permission is `android.permission.VIBRATE`, used solely for optional
   key-press haptic feedback.
