# OptiClass — Kapsamlı Teknik Rapor Prompt'u

Sen deneyimli bir yazılım mimarı ve teknik yazarsın. Aşağıdaki dosyaları okuyarak OptiClass projesine ait, **bölüm başlıklı, akıcı Türkçe yazılmış, akademik kalitede** bir teknik rapor hazırlayacaksın.

## Önce şu dosyaları oku:

```
CLAUDE.md
app/src/main/java/com/example/opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal/viewmodel/AppViewModel.kt
app/src/main/java/com/example/opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal/repository/AppRepository.kt
app/src/main/java/com/example/opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal/network/ApiService.kt
app/src/main/java/com/example/opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal/ui/screens/MainScaffold.kt
app/src/main/java/com/example/opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal/ui/screens/admin/UpdateCalendarPage.kt
app/src/main/java/com/example/opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal/ui/screens/admin/WeeklySchedulePage.kt
app/src/main/java/com/example/opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal/ui/screens/common/ChatBotPage.kt
web/app.js
web/api.js
rasa_chatbot/domain.yml
rasa_chatbot/data/nlu.yml
opticlass.sql
```

## Raporun içermesi gereken bölümler:

### 1. Yönetici Özeti
Projenin ne olduğunu, hangi problemi çözdüğünü ve öne çıkan özelliklerini 3-4 paragrafta anlat. Teknik olmayan bir okuyucunun da anlayabileceği netlikte yaz.

### 2. Proje Genel Bakış
- Projenin amacı ve kapsamı
- Hedef kullanıcı kitlesi (Admin, Instructor, Super Admin rolleri)
- Bitirme ödevi bağlamı ve akademik önemi
- Sistemin çözdüğü gerçek dünya problemleri

### 3. Mimari Genel Bakış
- Sistemin katmanlı mimarisini açıkla (Android Client ↔ FastAPI Backend ↔ PostgreSQL ↔ RASA NLU)
- Bileşenler arası iletişim akışını ASCII diyagram ile göster
- Android tarafında MVVM + Repository pattern kullanımını açıkla
- Web Admin Panel'in SPA mimarisini açıkla

### 4. Teknoloji Yığını
Her teknolojiyi neden seçildiğiyle birlikte tablo formatında listele:

| Katman | Teknoloji | Versiyon / Açıklama | Seçilme Gerekçesi |
|--------|-----------|---------------------|-------------------|
| ...    | ...       | ...                 | ...               |

### 5. Veri Modeli
- Tüm veritabanı tablolarını (users, classrooms, courses, schedules, availabilities, messages, notifications, schedule_history, app_settings) ilişkileriyle açıkla
- Önemli tasarım kararlarını vurgula (ör. courses PK'sı neden (code, department, email) üçlüsü?)
- Faz sistemi için app_settings tablosunun rolünü açıkla

### 6. API Katmanı
- Tüm endpoint gruplarını (auth, users, classrooms, courses, schedules, availabilities, messages, notifications, history, settings, XAI, chatbot, logs) özetle
- JWT auth mekanizmasını açıkla
- Kritik endpoint'lerin istek/yanıt formatlarını örneklerle göster

### 7. Dinamik Faz (Scheduling Phase) Sistemi
Bu projenin en özgün özelliklerinden biri. Detaylıca açıkla:
- Fazların nasıl otomatik hesaplandığını (priority değerlerinden)
- Faz geçiş mekanizmasını
- Her fazda hangi kısıtların devreye girdiğini
- MyAvailabilityPage ve UpdateCalendarPage'e etkisini
- semesterBlockedSlots ve semesterConflict mantığını

### 8. XAI (Açıklanabilir Yapay Zeka) Modülü
Projenin en yenilikçi bileşeni. Şunları açıkla:
- Decision Tree tabanlı slot öneri algoritmasını (9 node, hard vs soft constraint ayrımı)
- Her DT node'unun ne yaptığını ve skorlamayı nasıl etkilediğini
- Accordion UI ile önerilerin nasıl sunulduğunu
- Haftalık XAI (suggest-weekly) modülünü ve 4 farklı stratejiyi (By Student Count, By Lecture Duration, COMMON First, COMMON Spread)
- XAI'nin şeffaflık sağlama amacını (neden bu slotu önerdi?)

### 9. RASA NLU ChatBot
- 23 intent'in kategorik dağılımını ve örnekleri
- DIETClassifier + FallbackClassifier pipeline'ını
- FastAPI proxy mimarisini (Android/Web → FastAPI → RASA)
- Graceful fallback mekanizmasını
- Chatbot'un hangi kullanım senaryolarını karşıladığını

### 10. Android Mobil Uygulama
- Ekran başlığı altında tüm sayfaları ve işlevlerini açıkla
- Admin ve Instructor rol ayrımını
- Polling mekanizmalarını (30sn, 5sn)
- EncryptedSharedPreferences ile session kalıcılığını
- Excel import akışını (Apache POI)
- Avatar yönetimini (base64 encode/decode, Coil)

### 11. Web Admin Paneli
- 8 sayfayı ve işlevlerini özetle
- SUPER_ADMIN rolü ve Audit Logs özelliğini
- SSE (Server-Sent Events) ile gerçek zamanlı log akışını
- SheetJS ile browser-side Excel import'unu
- Import validasyon sistemini (format kontrolü, tip kontrolü, duplicate tespiti)
- nginx reverse proxy kurulumunu

### 12. Güvenlik
- SHA-256 şifre hashleme
- JWT token yönetimi (7 günlük süre)
- EncryptedSharedPreferences (AES256-GCM)
- Rol bazlı erişim kontrolü (RBAC)
- Audit log sistemi
- Import validasyonunun güvenlik boyutu

### 13. Bilinen Limitasyonlar ve Teknik Borç
CLAUDE.md'deki "Bilinen Limitation" bölümünden ve geliştirme sürecinden edinilen bilgilerle dürüst bir değerlendirme yap:
- Cross-instructor draft classroom conflict sorunu
- Token yenileme mekanizması eksikliği
- setSchedulingPhase silent desync riski
- Uvicorn'un systemd service olarak kurulmamış olması

### 14. Geliştirme Süreci ve Kararlar
CLAUDE.md'deki "Önemli Kararlar ve Tercihler" bölümünden yola çıkarak:
- En kritik 5 mimari kararı ve gerekçelerini açıkla
- Refactor edilen / kaldırılan özellikler (encodeUsername, labSuggestionPending, vb.)
- Tutarsızlıklar ve bilinçli ödünleşimler

### 15. Deployment ve Operasyon
- VirtualBox + Ubuntu VM kurulumu
- nginx + uvicorn + PostgreSQL stack'inin çalıştırılması
- Sunucu başlatma adımları
- SCP ile güncelleme süreci
- opticlass.sql ile sıfırdan DB kurulumu

### 16. Sonuç ve Gelecek Çalışmalar
- Projenin mevcut durumunu değerlendir
- Sistemin üretim ortamına alınması için yapılması gerekenleri listele
- Önerilen geliştirmeler (token refresh, systemd service, CI/CD, Docker, vb.)
- Akademik katkı ve özgün yönler

---

## Yazım kuralları:

- **Dil:** Türkçe (teknik terimler İngilizce kalabilir)
- **Ton:** Akademik ama akıcı; bir bitirme tezi ekine girebilecek kalitede
- **Uzunluk:** Her bölüm için yeterince derinlikte yaz; toplam rapor en az 3000 kelime olsun
- **Format:** Markdown — başlıklar, tablolar, kod blokları, maddeler kullan
- **Kod örnekleri:** Her bölümde en az 1 anlamlı kod/veri örneği göster (Kotlin, Python veya SQL)
- **Nesnel ol:** Güçlü yönleri vurgula ama zayıf yönleri de dürüstçe yaz
- **Bölümler arası bağlantı:** Bölümler birbirini referans alabilir (ör. "Faz sistemi, Bölüm 7'de anlatıldığı üzere...")

Raporu şimdi yaz.
