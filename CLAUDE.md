# OptiClass — CLAUDE.md

Üniversite ders programı yönetimi ve XAI destekli takvim portalı.
Android (Kotlin + Jetpack Compose) uygulaması.
Geliştirici: Berkay + Yusuf (bitirme ödevi).

---

## Mimari

Kod birden fazla dosyaya bölünmüştür. Ana package:
`com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal`

Network katmanı ayrı sub-package'tadır: `...network`

```
MainActivity.kt                  ← sadece Activity (~15 satır)
model/Models.kt                  ← data class'lar + enum'lar
state/GlobalState.kt             ← boş (tüm state AppRepository'e taşındı)
repository/AppRepository.kt      ← tüm global state + tüm sync metodları
viewmodel/AppViewModel.kt        ← AndroidViewModel; tüm API metodları burada
utils/Utils.kt                   ← DAYS, TIME_SLOTS, sha256, generateUsername, vb.
network/
  ApiService.kt                  ← Retrofit interface + tüm DTO'lar
  RetrofitClient.kt              ← BASE_URL = http://<IP>:8000/  (DHCP, değişebilir)
  TokenStore.kt                  ← kullanılmıyor, authToken AppViewModel'da
ui/components/
  UserAvatar.kt
  SummaryCard.kt
ui/screens/
  LoginScreen.kt                 ← LoginScreen (async) + ForceChangePasswordScreen (API)
  MainScaffold.kt                ← OptiClassApp + MainScaffold (tüm callback'lerin hub'ı)
  admin/
    AdminMainPage.kt             ← inbox + mesajlaşma + dashboard istatistikleri
    UpdateCalendarPage.kt        ← schedule kaydetme + bildirim gönderme API'ye bağlı
    DataImportPage.kt            ← import + SavedCoursesSection (silme dahil) API'ye bağlı
    ClassroomsPage.kt            ← add/delete/import API'ye bağlı
    InstructorAvailabilityAdminPage.kt  ← refresh butonu var (sağ üst)
    UserTransactionsPage.kt      ← add/edit/delete/resetPassword API'ye bağlı
  instructor/
    InstructorMainPage.kt        ← 30sn'de bir bildirim+schedule+mesaj polling
    MySchedulePage.kt            ← ders kodu + derslik kodu gösterir (devam slotları dahil)
    MyLecturesPage.kt
    MyAvailabilityPage.kt        ← tek buton: "Save & Send to Admin"
  common/
    SettingsPage.kt              ← ChangePasswordDialog + avatar upload API'ye bağlı
    ChatBox.kt                   ← mesaj yükleme/gönderme API'ye bağlı (5sn polling)
    GenericPage.kt
```

---

## Tech Stack

- **UI:** Jetpack Compose + Material3
- **State:** `mutableStateListOf` / `mutableStateMapOf` (SnapshotState)
- **Excel import:** Apache POI (`poi`, `poi-ooxml`)
- **Şifre:** SHA-256 hash
- **Image loading:** Coil (`coil-compose:2.6.0`) — remote http:// URL + data: base64 destekli
- **Backend:** Ubuntu VM (VirtualBox, Bridge mode), FastAPI (Python), PostgreSQL
- **Network:** Retrofit + OkHttp + Gson; `ApiService.kt` tüm endpoint'leri tanımlar
- **Auth:** JWT token — login'de alınır, `AppViewModel.authToken`'da `"Bearer xxx"` formatında tutulur

---

## Sunucu Bilgileri

- **IP:** DHCP — her oturumda değişebilir. `network/RetrofitClient.kt` `BASE_URL`'i güncelle → rebuild
- **Port:** `8000`
- **Kullanıcı:** `vboxuser` (Ubuntu VM)
- **DB:** PostgreSQL, veritabanı adı `opticlass`, kullanıcı `yusuf`
- **IP değişirse:** VM'de `ip a` ile öğren → `RetrofitClient.kt` `BASE_URL` güncelle → rebuild

---

## Her Oturumda Sunucu Başlatma (Sırayla Yap)

### 1. VirtualBox'ta VM'i Başlat
VirtualBox'u aç → `OptiClass-Server` → **Start**

### 2. SSH ile Bağlan (Windows CMD)
```
ssh vboxuser@<IP>
```
> IP için önce VM ekranında `ip a` yaz.

### 3. API'yi Başlat (SSH terminalinde)
```bash
cd ~/opticlass-api && source venv/bin/activate && uvicorn main:app --host 0.0.0.0 --port 8000
```

### 4. Çalıştığını Doğrula
Tarayıcıda aç: `http://<IP>:8000` → `{"message":"OptiClass API Online!"}` görünmeli.

### main.py Güncellemesi Gerekirse
Windows CMD'de (yeni pencere):
```
scp C:\Users\BERKAY\Desktop\main.py vboxuser@<IP>:/home/vboxuser/opticlass-api/main.py
```
Sonra SSH'da uvicorn'u yeniden başlat (Ctrl+C → yukarıdaki komutu tekrar çalıştır).

---

## Roller

| Rol | Ekranlar |
|---|---|
| `ADMIN` | Main (Inbox), Data Import, User Transactions, Update Calendar, Instructor Availability, Settings |
| `INSTRUCTOR` | Main (Dashboard), My Schedule, My Lectures, My Availability, Notifications (Chat), Settings |

Database'deki varsayılan kullanıcılar:
- `admin` / `admin123` → ADMIN
- `instructor` / (Yusuf'un belirlediği şifre) → INSTRUCTOR

---

## Veri Modelleri

```kotlin
data class User(username, password, role, fullName, email, avatarUri?, department, mustChangePassword, courses, schedule)
  // password: API sync'ten sonra "" — yerel doğrulama yapma, API'ye gönder
  // username: plain text — encode/decode yok, encodeUsername/decodeUsername kaldırıldı
data class CourseImport(code, name, lecturer, department, email, duration, classroomId?)
  // duration = -1: "continuation slot" işaretçisi (UI-internal)
  // lecturer: API sync'ten sonra lecturerUsername (plain) gelir
data class Classroom(id, roomCode, capacity)
data class Message(sender, recipient, content, isRead, timestamp)
data class AppNotification(id, text, isRead, recipientName)
  // recipientName = username (plain text)
data class Availability(instructorName, slots: Map<String, Set<String>>)
  // instructorName = username (plain text)
data class ScheduleChange(changedBy, timestamp, instructorUsername, instructorFullName, day, timeSlot, previousCourse?, newCourse?)
```

---

## AppRepository Sync Metodları

```kotlin
syncUsers(List<UserDto>)           // users listesini replace eder (avatarUri dahil)
syncClassrooms(List<ClassroomDto>) // classrooms listesini replace eder
syncCourses(List<CourseDto>)       // courseImports + her user.courses'u günceller
syncSchedule(username, flatSlots)  // tek instructor'ın user.schedule'ını günceller
                                   // flatSlots key formatı: "Mon_08:00 AM"
syncAvailabilities(List<AvailabilityDto>) // availabilities listesini replace eder
syncNotifications(List<NotificationDto>)  // notifications listesini replace eder
syncHistory(List<ScheduleHistoryDto>)     // scheduleHistory listesini replace eder (DB'den)
syncMessages(dtos, withUser)       // belirli konuşmayı replace eder
syncAllMessages(dtos)              // tüm messages'ı replace eder
markMessageRead(timestamp)         // tek mesaj lokal okundu işareti
markAllMessagesReadFrom(sender)    // sender'dan gelen tüm mesajları lokal okundu yapar
```

---

## AppViewModel Metodları

```kotlin
// Auth
login(username, password, onResult)
logout()
changePassword(current, new, onResult)
clearMustChangePassword()

// User CRUD
addUser(username, password, role, fullName, email, onResult)
deleteUser(username, onResult)
updateUser(username, fullName, email, role, onResult)
resetUserPassword(username, newPassword, onResult)

// Courses
importCourseData(courses, onResult)  // instructor oluştur + courses/bulk + schedule yenile
deleteCourse(code, onResult)

// Classrooms
addClassroom(classroom, onResult)
deleteClassroom(id, onResult)
importClassrooms(classrooms, onResult)

// Schedule
saveSchedule(username, draftSchedule, historyEntries)  // PUT /schedules/{username} + POST /history

// Availability
submitAvailability(username, slots, onResult)  // PUT /availabilities/{username}
refreshAvailabilities()                        // GET /availabilities → syncAvailabilities

// Messages
loadMessages(withUser?, onResult?)  // GET /messages veya GET /messages?with_user=...
sendMessage(toUser, content, onResult)
markMessagesRead(sender)            // lokal + PATCH /messages/mark-read?sender=...

// Notifications
sendNotification(recipientUsername, text)
refreshNotifications()
markNotificationRead(id)           // PATCH /notifications/{id}/read

// Polling
refreshInstructorData()            // notifications + schedule + messages (30sn'de bir, InstructorMainPage)

// Avatar
updateAvatar(username, dataUrl, onResult)  // PUT /users/{username}/avatar

// fetchInitialData (login/restore sonrası otomatik çağrılır):
//   users, classrooms, courses, schedules, availabilities, notifications, history yüklenir
```

---

## Schedule Formatı

`user.schedule`: `SnapshotStateMap<String, SnapshotStateMap<String, CourseImport?>>`
- Outer key: DAYS ("Mon", "Tue", ...)
- Inner key: TIME_SLOTS ("08:00 AM", ...)

API'ye gönderilirken flat map'e çevrilir:
- Key format: `"Mon_08:00 AM"` → `CourseDto?`

---

## Callback Zinciri (MainScaffold)

Tüm API callback'leri `OptiClassApp → MainScaffold → ilgili ekran` zincirine geçilir.
`MainScaffold` parametreleri: `onChangePassword`, `onAddUser`, `onDeleteUser`, `onUpdateUser`, `onResetPassword`, `onImportCourses`, `onDeleteCourse`, `onLoadMessages`, `onLoadAllMessages`, `onSendMessage`, `onMarkMessagesRead`, `onSubmitAvailability`, `onSaveSchedule`, `onSendNotification`, `onAddClassroom`, `onDeleteClassroom`, `onImportClassrooms`, `onMarkNotificationRead`, `onUpdateAvatar`, `onRefreshInstructorData`, `onRefreshAvailabilities`

---

## Sabitler

```kotlin
val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
val TIME_SLOTS = listOf("08:00 AM", ..., "05:00 PM")  // 10 slot
```

Bu listeleri asla inline olarak tekrar yazma.

---

## Önemli Kararlar ve Tercihler

- **Sadece istenen şeyi yap.** Ekstra refactor, cleanup, yorum ekleme yapma.
- **Şifre doğrulama:** `user.password` API sync'ten sonra boş. Yerel sha256 karşılaştırması yapma.
- **authToken:** `"Bearer xxx"` formatında, API çağrılarında direkt geç.
- **Username:** Plain text — `encodeUsername`/`decodeUsername` kaldırıldı, hiçbir yerde kullanma.
- **`AvailabilityTable`** hem editable hem read-only modu destekler (`isReadOnly` parametresi).
- **`SchedulingGridEnhanced`** `onSlotCleared` callback'i alır. Her slotta ders kodu + derslik kodu gösterilir (devam slotları dahil, biraz soluk renkte).
- **Horizontal scroll:** `horizontalScroll` + `width(65.dp)` day column (weight(1f) değil).
- **Email validasyonu:** `isValidEmail()` helper kullan.
- **Snackbar:** `snackbarHostState` MainScaffold'da tutulur, sayfalara parametre geçilir.
- **`CredentialsDialog`:** `LazyColumn` + `heightIn(max=400.dp)`.
- **DataImportPage preview:** Yerel `previewList` state kullanır (AppRepository.courseImports değil).
- **Mesaj polling:** ChatBox açıkken 5sn'de bir GET /messages. InstructorMainPage açıkken 30sn'de bir bildirim+schedule+mesaj yüklenir.
- **Mesaj okundu takibi:** ChatBox açılınca `markMessagesRead(sender)` çağrılır → lokal state hemen güncellenir + PATCH /messages/mark-read sunucuya gönderilir. `loadMessages` callback'i tamamlandıktan SONRA çağrılır (timing önemli).
- **Yeni mesaj snackbar:** `latestMsgTimestamp` (max timestamp) izlenerek tetiklenir — `messages.size` değil. Bu sayede `syncMessages` removeAll+readd döngüsünden etkilenmez.
- **Avatar:** `data:image/jpeg;base64,...` formatında DB'de saklanır. Settings'te fotoğraf seçilince max 256px + JPEG %70 ile encode edilip `PUT /users/{username}/avatar` ile kaydedilir. `UserAvatar` bileşeni sırasıyla: http:// → Coil, data: → base64 decode, content:// → contentResolver.
- **classroomConflict:** Sadece DİĞER instructor'ların kaydedilmiş programlarına bakar (mevcut instructor hariç tutulur). Kaydedilmemiş draft'lar arası cross-instructor çakışma tespit edilmez — bilinen limitation.
- **`onSaveSchedule` imzası:** `(username, draft, historyEntries: List<ScheduleChange>)` — history girişleri ViewModel'de API'ye POST edilir.
- **`importCourseData`:** Sonunda `getAllSchedules` çağrılır — `syncUsers` schedule'ları sıfırladığı için zorunlu.
- **MyAvailabilityPage:** Tek buton "Save & Send to Admin". Draft yoksa `AppRepository.availabilities`'dan mevcut veriyi yükler.
- **InstructorAvailabilityAdminPage:** Sağ üstte refresh butonu var (`onRefresh` → `refreshAvailabilities()`).

---

## API Bağlantı Durumu

### ✅ Tamamlanan
- Login / Logout / ForceChangePassword
- Kullanıcı listesi yükleme, ekleme, silme, düzenleme, şifre sıfırlama
- Derslik listesi yükleme, ekleme, silme, import
- Course import (instructor oluşturma + bulk), course silme
- Schedule kaydetme (UpdateCalendar) + açılışta yükleme
- Availability gönderme + açılışta yükleme + admin refresh butonu
- Mesajlaşma (ChatBox) + polling + okundu takibi (lokal + sunucu)
- Bildirim gönderme + mark-as-read + açılışta yükleme
- Şifre değiştirme (Settings + ForceChange)
- Schedule history DB'ye kaydetme (`POST /history`) + açılışta yükleme
- Avatar upload (Settings) → base64 encode → `PUT /users/{username}/avatar` → DB
- Mesaj okundu işaretleme sunucuya yazılıyor (`PATCH /messages/mark-read`)
- Instructor ana sayfasında 30sn polling (bildirim + schedule + mesaj)

### ⚠️ Yapılacak / Ertelenen
1. **XAI — Schedule Suggestion** — `POST /schedule/suggest/{username}` endpoint + UpdateCalendar'da "Suggest" butonu (ertelendi)
2. **Cross-instructor draft classroom conflict** — kaydedilmemiş iki instructor draft'ı arasında sınıf çakışması tespit edilemiyor (bilinen limitation, global state gerektirir)

### 🔧 Deployment Notu
`avatar_url` kolonu DB'de var mı kontrol et:
```bash
psql -U yusuf opticlass -c "\d users"
```
Yoksa: `ALTER TABLE users ADD COLUMN avatar_url TEXT;`

---

## Bağımlılıklar (app/build.gradle)

```kotlin
implementation("org.apache.poi:poi:5.2.5")
implementation("org.apache.poi:poi-ooxml:5.2.5")
implementation("androidx.compose.material:material-icons-extended")
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("androidx.security.crypto:security-crypto:...")
implementation("io.coil-kt:coil-compose:2.6.0")
// Firebase KULLANILMIYOR — backend Ubuntu VM + FastAPI + PostgreSQL
```

---

## Notlar

- `AvailabilityTable` tıklanabilir sütun başlıkları içerir — sadece `isReadOnly = false` durumunda.
- `ScheduleChange` history dialogu `UpdateCalendarPage`'in sağ üstünde "History" butonuyla açılır.
- Sunucu her VM yeniden başlatıldığında uvicorn manuel başlatılmalı (systemd service kurulmadı).
- `main.py` güncellenince SCP ile VM'e göndermek gerekir (IP'yi güncel tut).
