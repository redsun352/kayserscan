# Kayser Yüzey Tarama (`com.kayser.areascan`)

Telefonun dahili manyetometresini kullanarak yüzey/alan taraması yapan, taranan veriyi
2D/3D olarak (Grid + RBF enterpolasyonlu yüzey + Marching Cubes isosurface) görselleştiren,
isteğe bağlı ARCore desteğine sahip native Android uygulaması.

Bu proje, Thuban Lodestar APK'sının "Yüzey Tarama" (AreaScan) modülünün decompile/analiz
edilmesinden çıkarılan mimari ve sensör bulgularına dayanılarak sıfırdan yazıldı — kod
kopyalanmadı, sadece sensör tipi/sample rate ve genel ekran/menü yapısı referans alındı.

## Modüller

```
core/      → Veri modelleri (ScanPoint, GridConfig, RbfConfig, vb.) + RBF interpolasyon matematiği
sensor/    → MagnetometerService (TYPE_MAGNETIC_FIELD_UNCALIBRATED, SENSOR_DELAY_GAME)
             + HardIronCalibrator + ScanSessionRepository
render/    → OpenGL ES 2.0 (ModelSurfaceView, shader'lar, grid mesh, colormap)
             + Marching Cubes algoritması
             + ARCore session yönetimi (opsiyonel)
app/       → UI: MainActivity, AreaScanActivity, 7 ayar fragment'ı, ViewModel
```

## Sensör Mimarisi — Önemli Teknik Notlar

- **Sensör tipi:** `Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED` (tip kodu 14). Thuban'daki orijinal
  serviste de bu tip kullanılıyordu. Kalibre edilmemiş sensör, ham X/Y/Z + sensörün kendi
  bias tahminini ayrı ayrı verir (`values[0..2]` = ham, `values[3..5]` = bias).
- **Örnekleme hızı:** `SensorManager.SENSOR_DELAY_GAME` (~50 Hz).
- **Servis mimarisi:** Activity'den ayrı bir foreground `Service` (`MagnetometerService`),
  Android 13+ için `POST_NOTIFICATIONS` izni gerekiyor (foreground bildirim göstermek için).
- **Hard-iron kalibrasyon:** `HardIronCalibrator`, min/max merkezleme yöntemiyle ofset hesaplar.
  Soft-iron (eliptik distorsiyon) düzeltmesi şu an YOK — gelecek faz.

## Eksikler / Yapılacaklar (henüz tamamlanmadı)

Bu, "tek seferde tüm modülleri kur" talebiyle hızlıca inşa edilen bir **iskelet/MVP**.
Gerçek bir cihazda derlenip çalıştırılmadan önce şunlar kesinlikle gözden geçirilmeli:

1. **Konum/X-Y izleme eksik** — `AreaScanActivity.observeSensorSamples()` içinde şu an
   sahte/placeholder bir zaman-bazlı X/Y üretiliyor (`elapsedSeconds % width` gibi). Gerçek
   tarama için kullanıcının fiziksel konumu gerekiyor: ya dokunmatik grid üzerinde "buradayım"
   işaretlemesi, ya step-counter/IMU dead-reckoning, ya da GPS (RTK olmadan açık alanda
   ~3-5m hassasiyet, kapalı/yakın menzilli arkeolojik tarama için yetersiz olabilir).
2. **GLSurfaceView grid çizimi** — sadece dolu (interpolasyonlu) yüzey çiziliyor; ızgara
   çizgileri (`GridConfig.showGridLines`) ve bounding box görselleştirmesi henüz GPU tarafında
   render edilmiyor (config alanları var, çizim kodu eklenmedi).
3. **Marching Cubes ile gerçek veri bağlantısı yok** — `MarchingCubesAlgorithm` çalışan ve test
   edilebilir durumda, ama `AreaScanViewModel` şu an sadece 2D grid mesh üretiyor; 3B voxel
   field'ı RBF'den nasıl türeteceğimiz (örn. derinlik ekseni B-Scan'den mi gelecek, yoksa
   tek katmanlı 2.5D mi olacak) henüz kararlaştırılmadı.
4. **ARCore entegrasyonu sığ** — `ArSessionManager` session açıp kapatabiliyor ama kamera
   feed'ini `ModelSurfaceView` ile birleştiren (background camera texture + 3D overlay) kod
   henüz yok.
5. **Dosya kaydetme (CSV export) yok** — Thuban'daki "Dosya Kaydet" butonuna karşılık gelen
   ekran/mantık eklenmedi.
6. **Test edilmedi** — bu kod hiç derlenmedi/çalıştırılmadı. Gradle/AGP/Kotlin sürüm
   uyumsuzlukları, eksik import'lar, ViewBinding/findViewById tutarsızlıkları olabilir.
   Termux'ta ilk `./gradlew assembleDebug` çalıştırmasında hatalar bekleyin, normal.

## Termux'ta Derleme

```bash
pkg install openjdk-21 gradle  # veya wrapper kullan
cd kayser-areascan
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Not: Gradle wrapper (`gradlew` + `gradle-wrapper.jar`) repoya dahil edildi, ama Termux'ta
ARM mimarisi ve bellek kısıtları nedeniyle ilk derlemede `--offline` olmayan bir bağlantı
ve yeterli RAM (en az 4GB önerilir) gerekir. Sorun olursa `org.gradle.jvmargs` değerini
`gradle.properties` içinde düşürebilirsin (örn. `-Xmx1024m`).

## GitHub'a Push

```bash
git init
git add .
git commit -m "Initial scaffold: Kayser AreaScan - sensor/render/core/app modules"
git remote add origin https://github.com/<kullanici-adi>/<repo-adi>.git
git branch -M main
git push -u origin main
```

Kimlik doğrulama için Termux'ta GitHub Personal Access Token (PAT) kullanman gerekecek
(`git push` sırasında parola istendiğinde PAT'i gir, ya da `gh auth login` ile `gh` CLI kurup
kullanabilirsin).
