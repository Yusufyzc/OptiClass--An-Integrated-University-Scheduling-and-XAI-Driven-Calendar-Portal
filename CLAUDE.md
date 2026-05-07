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
    UpdateCalendarPage.kt        ← schedule kaydetme + faz yönetimi + bildirim gönderme
    DataImportPage.kt            ← import + SavedCoursesSection (silme dahil) API'ye bağlı
    ClassroomsPage.kt            ← add/delete/import API'ye bağlı
    InstructorAvailabilityAdminPage.kt  ← refresh butonu var (sağ üst)
    UserTransactionsPage.kt      ← add/edit/delete/resetPassword API'ye bağlı
  instructor/
    InstructorMainPage.kt        ← 30sn'de bir bildirim+schedule+mesaj polling
    MySchedulePage.kt            ← ders kodu + derslik kodu gösterir (devam slotları dahil)
    MyLecturesPage.kt
    MyAvailabilityPage.kt        ← faz bazlı erişim kontrolü + turuncu slot uyarısı
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
data class CourseImport(code, name, lecturer, department, email, duration, classroomId?, semester, studentCount)
  // duration = -1: "continuation slot" işaretçisi (UI-internal)
  // lecturer: API sync'ten sonra lecturerUsername (plain) gelir
  // department == "COMMON" → ortak ders. isCommon ayrı field DEĞİL, her yerde department == "COMMON" kontrolü yap
  // semester: Int = 1 (kaçıncı dönem)
  // studentCount: Int = 0 (dersi alan öğrenci sayısı)
data class Classroom(id, roomCode, capacity)
data class Message(sender, recipient, content, isRead, timestamp)
data class AppNotification(id, text, isRead, recipientName)
  // recipientName = username (plain text)
data class Availability(instructorName, slots: Map<String, Set<String>>)
  // instructorName = username (plain text)
data class ScheduleChange(changedBy, timestamp, instructorUsername, instructorFullName, day, timeSlot, previousCourse?, newCourse?)
```

---

## Ortak Ders (COMMON) Mantığı

- `department == "COMMON"` olan dersler ortak derstir. Ayrı boolean field YOKTUR.
- Excel'de Dept sütununa `"COMMON"` yazılırsa o ders ortak ders olarak işlenir.
- Ortak dersler faz sisteminin merkezindedir (aşağıda açıklanır).

---

## Faz (Scheduling Phase) Sistemi

### Fazlar
| Faz | Anlamı |
|---|---|
| `PHASE_1` | Sadece COMMON dersli hocalar availability doldurabilir. Admin UpdateCalendar'da sadece COMMON dersli hocaları görür ve yalnızca onlara ders atar. |
| `PHASE_2` | Tüm hocalar availability doldurabilir. Admin tüm hocaları görür. COMMON ders slotlarına aynı dönem başka ders atanamaz. |

### Faz Geçişi
- Admin `UpdateCalendarPage`'de ortak dersleri atayıp kaydetince sayfanın üstündeki "→ Faz 2'ye Geç" butonu ile geçiş yapar.
- Geçiş `PUT /settings/scheduling_phase` endpointi ile DB'ye yazılır.
- Faz bilgisi uygulama açılışında `GET /settings/scheduling_phase` ile çekilir ve `AppRepository.schedulingPhase`'e yazılır.

### Faz Kısıtları — UpdateCalendarPage
- **PHASE_1:** Hoca dropdown'unda sadece en az bir COMMON dersi olan hocalar listelenir.
- **PHASE_2:** Tüm hocalar listelenir. Atama dialogunda yeni kontrol: seçilen slot(lar)da başka bir hocaya atanmış `department == "COMMON"` ders varsa VE seçilmek istenen kurs `semester` değeri o COMMON ders ile aynıysa → atama engellenir, hata mesajı gösterilir. Farklı semester ise serbest.

### Faz Kısıtları — MyAvailabilityPage
- **PHASE_1:** Kullanıcının hiçbir kursu `department != "COMMON"` ise tablo kilitlenir: "Ortak ders planlaması devam ediyor. Henüz erişim açılmadı." mesajı gösterilir, kaydet butonu gizlenir.
- **PHASE_2:** Tüm hocalar erişir. `AvailabilityTable`'a `commonOccupiedSlots: Map<String, Set<String>>` parametresi eklendi. COMMON ders atanmış slotlar turuncu gösterilir. Hoca o slotu seçmeye çalışırsa uyarı dialogu açılır:
  > "Bu saatte ortak ders planlanmıştır. Aynı dönem öğrencileri bu saatte meşgul olacak. Yine de seçmek istiyor musunuz?"
  > [İptal] [Yine de Seç]

### commonOccupiedSlots Hesaplama
`AppRepository` içinde computed property veya sync sonrası doldurulan `commonCourseSlots: Map<String, Set<String>>`.
Tüm kullanıcıların schedule'larından `department == "COMMON"` olan dersler taranır, `{day: Set<timeSlot>}` map'i üretilir.
AppViewModel'da `fetchCommonCourseSlots()` → `GET /common_course_slots` ile sunucudan da çekilebilir (schedule sync sonrası güncellenir).

---

## Excel İmport Formatı

7 sütun (sırayla):
| # | Sütun | Örnek |
|---|---|---|
| 0 | Code | C101 |
| 1 | Name | Intro to CS |
| 2 | Lecturer | John Doe |
| 3 | Dept | CS veya **COMMON** |
| 4 | Email | john@uni.edu |
| 5 | Semester | 3 |
| 6 | StudentCount | 120 |

- Dept == `"COMMON"` → ortak ders
- `importExcelData` fonksiyonu `row.getCell(5)` → semester (Int), `row.getCell(6)` → studentCount (Int) okur
- Validation: code ve name dolu + email geçerli olmalı; semester/studentCount yoksa default (1 / 0) kullanılır

---

## Classroom Filtresi (UpdateCalendar Atama Dialogu)

- Dropdown'da sadece `room.capacity >= selectedCourseToAssign.studentCount` olan sınıflar listelenir.
- `studentCount == 0` ise filtre uygulanmaz (tüm sınıflar gösterilir).
- Her sınıf item'ında kapasite gösterilir: `"B101  (cap: 45)"` formatında.
- Dolu (occupied) sınıflar hâlâ disabled ve `"(occupied)"` etiketi gösterilir — bu kontrol değişmedi.

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
// Yeni:
schedulingPhase: String            // "PHASE_1" veya "PHASE_2" — AppRepository'de mutableState
commonCourseSlots: Map<String, Set<String>>  // COMMON ders atanmış {day: Set<slot>}
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

// Faz Yönetimi (YENİ)
fetchSchedulingPhase()             // GET /settings/scheduling_phase → AppRepository.schedulingPhase
setSchedulingPhase(phase, onResult) // PUT /settings/scheduling_phase
fetchCommonCourseSlots()           // GET /common_course_slots → AppRepository.commonCourseSlots

// fetchInitialData (login/restore sonrası otomatik çağrılır):
//   users, classrooms, courses, schedules, availabilities, notifications, history,
//   schedulingPhase, commonCourseSlots yüklenir
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
`MainScaffold` parametreleri: `onChangePassword`, `onAddUser`, `onDeleteUser`, `onUpdateUser`, `onResetPassword`, `onImportCourses`, `onDeleteCourse`, `onLoadMessages`, `onLoadAllMessages`, `onSendMessage`, `onMarkMessagesRead`, `onSubmitAvailability`, `onSaveSchedule`, `onSendNotification`, `onAddClassroom`, `onDeleteClassroom`, `onImportClassrooms`, `onMarkNotificationRead`, `onUpdateAvatar`, `onRefreshInstructorData`, `onRefreshAvailabilities`, `onSetSchedulingPhase`

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
- **`AvailabilityTable`** hem editable hem read-only modu destekler (`isReadOnly` parametresi). Ayrıca `commonOccupiedSlots` parametresi alır (PHASE_2'de turuncu boyama için). İmzası: `AvailabilityTable(days, timeSlots, selectedSlots, isReadOnly, commonOccupiedSlots)`.
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
- **MyAvailabilityPage:** Tek buton "Save & Send to Admin". Draft yoksa `AppRepository.availabilities`'dan mevcut veriyi yükler. PHASE_1'de COMMON dersi olmayan hocalar için kilitli ekran gösterilir.
- **InstructorAvailabilityAdminPage:** Sağ üstte refresh butonu var (`onRefresh` → `refreshAvailabilities()`).
- **Ortak ders tespiti:** `course.department == "COMMON"` — ayrı `isCommon` field YOKTUR, her yerde bu kontrolü yap.
- **Faz geçişi:** UpdateCalendarPage'de sayfanın üstünde PHASE_1'de görünen "→ Faz 2'ye Geç" butonu. Basılınca `onSetSchedulingPhase("PHASE_2")` çağrılır. Onay dialogu açılır ("Bu işlem geri alınamaz. Emin misiniz?").
- **Dönem çakışması (PHASE_2):** Atama dialogunda `outOfBounds`, `classroomConflict` vb. yanına yeni kontrol: atanmak istenen slotlarda `department == "COMMON"` ve aynı `semester` ders varsa `semesterConflict = true` → Assign butonu disabled, hata mesajı gösterilir.

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
- **CourseImport + CourseDto**: `semester`, `studentCount` alanları eklendi
- **ApiService**: faz endpoint'leri (`GET/PUT /settings/scheduling_phase`) + `GET /common_course_slots` eklendi
- **AppRepository**: `schedulingPhase` (mutableStateOf), `commonCourseSlots` (mutableStateMapOf), `recomputeCommonCourseSlots()` eklendi; `syncCourses` ve `syncSchedule` semester/studentCount taşıyor
- **AppViewModel**: `fetchSchedulingPhase()`, `setSchedulingPhase()` eklendi; `fetchInitialData` faz + commonSlots çekiyor; backend yokken graceful fallback var
- **DataImportPage**: Excel parser 7 sütun (col 5→semester, col 6→studentCount); format kartı güncellendi; preview kartta "COMMON" badge + "Term X" + öğrenci sayısı gösteriliyor
- **UpdateCalendarPage**: Phase 1 banner + "Go to Phase 2 →" butonu; PHASE_1'de sadece COMMON dersli hocalar listelenir; CourseItemSelectable'da "Term X" + öğrenci sayısı; classroom dropdown kapasite filtresi + kapasite gösterimi; semesterConflict kontrolü; faz onay dialogu
- **MyAvailabilityPage**: PHASE_1'de COMMON dersi olmayan hocalara kilitli ekran; PHASE_2'de turuncu bilgi banner'ı
- **AvailabilityTable**: `commonOccupiedSlots` parametresi; turuncu slot rengi; tıklamada "Common Course Time Slot" uyarı dialogu → "Select Anyway" / "Cancel"
- **MainScaffold**: `onSetSchedulingPhase` callback'i eklendi ve `UpdateCalendarPage`'e bağlandı

### ✅ main.py Güncellendi (`C:\Users\BERKAY\Desktop\main.py`)
- `CourseDto`'ya `semester: int = 1`, `studentCount: int = 0` eklendi
- `SchedulingPhaseDto` modeli eklendi
- `GET /courses` ve `POST /courses/bulk` semester/student_count taşıyor
- `GET /settings/scheduling_phase`, `PUT /settings/scheduling_phase` eklendi
- `GET /common_course_slots` eklendi (schedule'lardan COMMON dersler taranır)
- `create_tables` startup: `app_settings` tablosu + default `PHASE_1` otomatik oluşturulur

### 🚧 Yapılacak — DB Migration (tek kalan adım)

VM açık ve SSH bağlıyken psql'de çalıştır:
```sql
ALTER TABLE courses ADD COLUMN semester INT DEFAULT 1;
ALTER TABLE courses ADD COLUMN student_count INT DEFAULT 0;
```
`app_settings` tablosu uvicorn startup'ında otomatik oluşur — elle çalıştırmaya gerek yok.

Sonra deploy et:
```
scp C:\Users\BERKAY\Desktop\main.py vboxuser@<IP>:/home/vboxuser/opticlass-api/main.py
```
SSH'da uvicorn'u yeniden başlat.

### ⚠️ Ertelenen
1. **XAI — Schedule Suggestion** — `POST /schedule/suggest/{username}` endpoint + UpdateCalendar'da "Suggest" butonu (ertelendi)
2. **Cross-instructor draft classroom conflict** — kaydedilmemiş iki instructor draft'ı arasında sınıf çakışması tespit edilemiyor (bilinen limitation, global state gerektirir)

### 🔧 Deployment Notları
`avatar_url` kolonu DB'de var mı kontrol et:
```bash
psql -U yusuf opticlass -c "\d users"
```
Yoksa: `ALTER TABLE users ADD COLUMN avatar_url TEXT;`

Faz sistemi için `app_settings` tablosu gerekli — yukarıdaki migration SQL'ini çalıştır.

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
- Faz geçişi tek yönlüdür (PHASE_1 → PHASE_2). Geri dönüş UI'dan yoktur; gerekirse psql'den elle değiştirilebilir.
