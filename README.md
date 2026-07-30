# Пульсометр света / Light Pulse Meter

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/app_icon.png" width="180" alt="Light Pulse Meter icon">
</p>

## Русский

**Пульсометр света** — автономное Android-приложение для ориентировочной
оценки пульсации искусственного освещения с помощью камеры смартфона.

Приложение анализирует изменение яркости в реальном времени и показывает:

- частоту пульсации в герцах;
- коэффициент пульсации в процентах;
- достоверность измерения;
- понятную цветовую оценку результата.

Кадры обрабатываются только в оперативной памяти устройства, не сохраняются и
не передаются. Приложение поддерживает русский, английский и испанский языки,
светлую и тёмную темы.

> Результаты являются ориентировочными и зависят от характеристик камеры и
> условий измерения. Приложение не заменяет профессиональный измерительный
> прибор.

### Технологии

- Kotlin;
- Jetpack Compose и Material 3;
- CameraX;
- DataStore;
- Android 10 (API 29) и новее.

### Сборка

Требуются JDK 17 и Android SDK 35.

```powershell
.\gradlew.bat :app:assembleDebug
```

Готовый APK будет находиться в
`app/build/outputs/apk/debug/app-debug.apk`.

---

## English

**Light Pulse Meter** is an offline Android application for indicative
measurement of artificial light flicker using a smartphone camera.

The application analyzes brightness changes in real time and displays:

- flicker frequency in hertz;
- flicker coefficient as a percentage;
- measurement confidence;
- a clear color-coded result.

Camera frames are processed only in the device memory. They are never stored or
transmitted. The application supports Russian, English, and Spanish, as well as
light and dark themes.

> Results are estimates and depend on camera characteristics and measurement
> conditions. The application is not a replacement for professional measuring
> equipment.

### Technology

- Kotlin;
- Jetpack Compose and Material 3;
- CameraX;
- DataStore;
- Android 10 (API 29) or newer.

### Build

JDK 17 and Android SDK 35 are required.

```powershell
.\gradlew.bat :app:assembleDebug
```

The generated APK will be available at
`app/build/outputs/apk/debug/app-debug.apk`.
