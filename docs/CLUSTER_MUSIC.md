# Трек Яндекса на приборной панели

Включение: Настройки → Основные → Трек Яндекса на приборной панели. По умолчанию выключено.

Источник: MediaSession Яндекс Музыки и Яндекс Навигатора. Передаются название, исполнитель (если прошивка поддерживает), состояние и прогресс. Обложка недоступна через штатный провайдер. Символы и устройства разрешаются по SDK текущей прошивки; обязательные отсутствуют — запись отключена. Другой активный проигрыватель получает управление штатной карточкой.

Основа: BYDMate v3.19.6, commit 6170b34131baed93f7d2a0757a739fcf85b537fd. Required Notice: Copyright AndyShaman (https://github.com/AndyShaman/BYDMate). PolyForm Noncommercial License 1.0.0, полные условия включены в APK assets/BYDMate-LICENSE.txt.

Проверка на машине: включить доступ к уведомлениям, запустить Яндекс Музыку, открыть штатную карточку музыки на приборке; проверить смену трека, паузу, прогресс, переход на другой проигрыватель и выключение настройки. В эмуляторе проверяется сборка и отсутствие записи без SDK BYD; физическая приборка требуется для проверки отображения.

Carlink отложен: шестая кнопка скрыта, код приёмника и материалы сохранены.

## Проверка обложек на рабочем DiLink — 2026-10-13

На DiLink5.0 / Android 12 доступ к уведомлениям BYD Split выдан, служба MaxCallNotificationService находится в списке Live notification listeners. Повторная выдача через `cmd notification allow_listener` успешна. Отказ системного экрана разрешений не означает отсутствия доступа.

Исследован установленный `/system/app/MediaController/MediaController.apk`, версия 2.5.0.0.0d843db. В `com.byd.mediacontroller.local.MediaInfoSender` методы `sendCover` и `sendRemoteCover` передают `device`, `source`, `cover` через `ContentResolver.update(MainProvider.URI_INFO, ...)`. `sendBlackThirdPartyAppMediaInfo` использует источник 26 и вызывает `sendCover(null)`. Текст и прогресс передаются отдельно через Instrument FID.

Провайдер `content://com.byd.mediacenter.provider/info` принадлежит `com.byd.mediacontroller.MainProvider`. READ_PROVIDER / WRITE_PROVIDER имеют protectionLevel signature. Попытка чтения из shell UID 2000 возвращает SecurityException с требованием этих разрешений. Штатный MediaController работает под UID 1000. Дополнительный обычный доступ к уведомлениям не открывает канал записи обложки. Текущая реализация BYD Split передаёт текст и прогресс; интеграция обложек через разрешённый системный интерфейс пока не найдена.
