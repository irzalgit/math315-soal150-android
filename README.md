# Math315 Soal150 – Android tahap awal

Proyek ini membungkus salinan `soal150.html` ke aplikasi Android menggunakan Capacitor. File website asli tidak diubah.

## Yang sudah disiapkan
- `www/index.html`: salinan file Soal150 yang diunggah untuk versi aplikasi.
- App ID: `id.math315.soal150`.
- Nama aplikasi: `Math315 Soal150`.
- Tautan checkout pada salinan aplikasi diarahkan ke `https://math315.id/checkout.html`.
- GitHub Actions untuk menghasilkan APK debug saat workflow dijalankan manual.

## Build APK lewat GitHub Actions
1. Unggah isi folder proyek ini ke repositori GitHub terpisah atau folder baru di repositori yang sudah ada.
2. Buka tab **Actions**.
3. Pilih workflow **Build Android APK**.
4. Tekan **Run workflow**.
5. Setelah selesai, unduh artifact `math315-soal150-debug-apk` dan ekstrak file `app-debug.apk`.
6. Pasang APK pada HP Android untuk pengujian.

## Catatan sebelum pengujian
- File HTML memakai Firebase Auth `signInWithPopup`. Login Google di Android WebView berpotensi tidak berjalan; perlu integrasi autentikasi native/redirect dan pengujian pada perangkat.
- Firebase, KaTeX, dan beberapa pustaka dimuat dari internet, sehingga koneksi internet diperlukan.
- Checkout diarahkan ke situs Math315. Ini untuk pengujian awal; sebelum publikasi Play Store, kebijakan pembayaran Google Play harus ditinjau.
- APK debug hanya untuk uji coba. Publikasi Play Store memerlukan AAB release yang ditandatangani dan pengujian kebijakan/privasi.
