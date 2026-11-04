# Политика конфиденциальности / Privacy Policy

**Приложение:** Кӗске Тӳме / кТӳме (`Kĕske Tüme / kTüme`, `org.chuvash.ktume`)
**Исходный код и обратная связь:** https://github.com/vutnar21-cmd/ktume-keyboard
**Последнее обновление:** 2026-11-04

## Русский

Клавиатура **«Кӗске Тӳме» (кТӳме)** работает полностью локально на устройстве пользователя и не собирает,
не хранит на внешних серверах и никому не передаёт никакие персональные или пользовательские данные.

1. **Отсутствие доступа к сети.** Приложение не запрашивает системное разрешение `android.permission.INTERNET`
   и физически не может отправлять набранный текст, содержимое буфера обмена или настройки по сети.
2. **Никакой аналитики и трекеров.** В приложении нет рекламных, аналитических или облачных SDK.
3. **Локальные настройки.** Настройки оформления и пользовательские раскладки сохраняются исключительно
   во внутреннем хранилище приложения на вашем устройстве (Android Jetpack DataStore).
4. **Буфер обмена.** При нажатии пользователем кнопки буфера обмена текст читается только для локального
   отображения на верхней панели клавиатуры и вставки в активное поле ввода; он никуда не сохраняется
   и не передаётся.
5. **Разрешения.** Приложение не запрашивает у пользователя ни одного разрешения. Тактильный отклик
   клавиш выполняется системным механизмом Android и отключается в настройках клавиатуры или телефона.
6. **Автозамена и личный словарь (экспериментальная функция, выключена по умолчанию).** Словари
   автозамены (чувашский и русский) встроены в приложение — сеть для их работы не нужна, набранный
   текст проверяется только на устройстве. Если включён пункт «Запоминать отменённые слова», слово,
   которое вы вернули клавишей `⌫` после автозамены, сохраняется в личный словарь — только во внутреннем
   хранилище приложения на устройстве; никуда не передаётся. Личный словарь можно просмотреть, удалить из
   него отдельные слова или очистить целиком в настройках клавиатуры (вкладка «Эксперименты»). В полях,
   где приложение запрашивает режим инкогнито (`IME_FLAG_NO_PERSONALIZED_LEARNING`), словарь не пополняется.

---

## English

**Kĕske Tüme / kTüme** (`org.chuvash.ktume`) operates 100% locally on your device. It does not collect,
transmit, share, or store any personal data or typed text on external servers.

1. **No Network Access.** The application does not request the `android.permission.INTERNET` permission
   and is technically incapable of transmitting typed characters, clipboard items, or preferences over the network.
2. **No Analytics or Trackers.** The app contains zero advertising, analytics, telemetry, or proprietary cloud SDKs.
3. **Local Preferences Only.** Layout customizations and theme settings are stored solely inside the app's
   private on-device storage (Android Jetpack DataStore).
4. **Clipboard.** Clipboard content is accessed on-device only when you explicitly tap the clipboard button
   or paste action; it is never logged, persisted, or transmitted anywhere.
5. **Permissions.** The app asks the user for no permissions at all. Key-press haptic feedback uses the
   standard Android system mechanism and can be turned off in the keyboard or phone settings.
6. **Autocorrect and personal dictionary (experimental, off by default).** The autocorrect dictionaries
   (Chuvash and Russian) are bundled with the app, so no network is needed and typed text is checked on-device
   only. If "Remember reverted words" («Запоминать отменённые слова») is enabled, a word you restore with `⌫` right after an autocorrection
   is saved to the personal dictionary — solely inside the app's private on-device storage; it is never
   transmitted anywhere. You can view the personal dictionary, delete individual words or clear it entirely
   in the keyboard settings ("Experiments" / «Эксперименты» tab). In fields where an app requests incognito mode
   (`IME_FLAG_NO_PERSONALIZED_LEARNING`), no words are learned.
