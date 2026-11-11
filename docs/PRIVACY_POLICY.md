# Политика конфиденциальности / Privacy Policy

**Приложение:** Кӗсье Тӳме / кТӳме (`Kĕsje Tüme / kTüme`, `org.chuvash.ktume`)
**Исходный код и обратная связь:** https://github.com/vutnar21-cmd/ktume-keyboard
**Последнее обновление:** 2026-11-11

## Русский

Клавиатура **«Кӗсье Тӳме» (кТӳме)** работает полностью локально на устройстве пользователя и не собирает,
не хранит на внешних серверах и никому не передаёт никакие персональные или пользовательские данные.

1. **Отсутствие доступа к сети.** Приложение не запрашивает системное разрешение `android.permission.INTERNET`
   и физически не может отправлять набранный текст, содержимое буфера обмена или настройки по сети.
2. **Никакой аналитики и трекеров.** В приложении нет рекламных, аналитических или облачных SDK.
3. **Только локальное хранение (без облачных бэкапов).** Настройки оформления и пользовательские раскладки
   сохраняются исключительно во внутреннем хранилище приложения на вашем устройстве (Android Jetpack DataStore).
   Автоматическое облачное резервное копирование отключено (`android:allowBackup="false"`), поэтому данные
   клавиатуры и личный словарь не выгружаются в облачные сервисы.
4. **Буфер обмена.** При нажатии пользователем кнопки буфера обмена текст читается только для локального
   отображения на верхней панели клавиатуры и вставки в активное поле ввода; он никуда не сохраняется
   и не передаётся.
5. **Разрешения.** Приложение не запрашивает у пользователя ни одного разрешения. Тактильный отклик
   клавиш выполняется системным механизмом Android и отключается в настройках клавиатуры или телефона.
6. **Автозамена и личный словарь.** Частотные словари автозамены (чувашский и русский) и список имён
   собственных встроены прямо в приложение — сеть для их работы не нужна, набранный текст проверяется
   исключительно на устройстве. Автозамена включена по умолчанию и в любой момент отключается в настройках
   клавиатуры (вкладка «Ввод»). Если включён пункт «Запоминать отменённые слова», слово, которое вы вернули
   клавишей `⌫` сразу после автозамены, сохраняется в личный словарь — только во внутреннем хранилище
   приложения на устройстве; оно никуда не передаётся. Личный словарь можно просмотреть, удалить из него
   отдельные слова или очистить целиком на вкладке «Ввод». В полях паролей и в полях с режимом инкогнито
   (`IME_FLAG_NO_PERSONALIZED_LEARNING`) словарь не пополняется.

---

## English

**Kĕsje Tüme / kTüme** (`org.chuvash.ktume`) operates 100% locally on your device. It does not collect,
transmit, share, or store any personal data or typed text on external servers.

1. **No Network Access.** The application does not request the `android.permission.INTERNET` permission
   and is technically incapable of transmitting typed characters, clipboard items, or preferences over the network.
2. **No Analytics or Trackers.** The app contains zero advertising, analytics, telemetry, or proprietary cloud SDKs.
3. **Local Storage Only (No Cloud Backup).** Layout customizations and theme settings are stored solely inside
   the app's private on-device storage (Android Jetpack DataStore). Cloud auto-backup is disabled
   (`android:allowBackup="false"`), ensuring neither preferences nor personal dictionary words are uploaded
   to external cloud storage.
4. **Clipboard.** Clipboard content is accessed on-device only when you explicitly tap the clipboard button
   or paste action; it is never logged, persisted, or transmitted anywhere.
5. **Permissions.** The app asks the user for no permissions at all. Key-press haptic feedback uses the
   standard Android system mechanism and can be turned off in the keyboard or phone settings.
6. **Autocorrect and Personal Dictionary.** The autocorrect dictionaries (Chuvash and Russian) and proper-name
   lists are bundled directly with the app, so no network is needed and typed text is checked strictly on-device.
   Autocorrect is enabled by default and can be turned off at any time in the keyboard settings ("Input" / «Ввод»
   tab). If "Remember reverted words" («Запоминать отменённые слова») is enabled, a word you restore with `⌫`
   right after an autocorrection is saved to your personal dictionary — solely inside the app's private on-device
   storage; it is never transmitted anywhere. You can view the personal dictionary, delete individual words, or
   clear it completely in the "Input" («Ввод») tab. In password fields and fields requesting incognito mode
   (`IME_FLAG_NO_PERSONALIZED_LEARNING`), no words are learned.
