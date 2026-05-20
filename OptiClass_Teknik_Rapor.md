# OptiClass — Kapsamlı Teknik Rapor

**Proje:** OptiClass — Üniversite Ders Programı Yönetim ve XAI Destekli Takvim Portalı  
**Tür:** Lisans Bitirme Ödevi  
**Geliştiriciler:** Berkay & Yusuf  
**Tarih:** Mayıs 2026

---

## 1. Yönetici Özeti

OptiClass, üniversite ders programlama sürecinin getirdiği karmaşıklığı dijital ortamda çözmek amacıyla geliştirilmiş bütünleşik bir yazılım sistemidir. Geleneksel yöntemlerde ders programı oluşturma; hoca müsaitlikleri, derslik kapasiteleri, dönem çakışmaları ve bölümler arası ortak dersler gibi onlarca kısıtın elle dengelenmesini gerektiren, zaman alan ve hataya açık bir süreçtir. OptiClass bu süreci hem dijitalleştirir hem de yapay zeka destekli öneri mekanizmalarıyla kısmen otomatize eder.

Sistemin en özgün özelliği, ders atamalarını kademeli ve öncelik tabanlı bir faz sistemiyle yönetmesidir. Yüksek öncelikli dersler (örneğin çok sayıda bölümün aldığı ortak dersler) önce planlanır; böylece daha az öncelikli dersler için kalan zaman dilimlerinde çakışma riski azalır. Her faz geçişi, yönetici onayıyla gerçekleşir ve geri alınamaz; bu sayede programlama süreci kontrollü ve denetlenebilir biçimde ilerler.

Projenin bir diğer öne çıkan bileşeni XAI (Açıklanabilir Yapay Zeka) modülüdür. Karar Ağacı tabanlı bu algoritma, bir ders için önerilen her zaman diliminin neden uygun ya da uygunsuz olduğunu adım adım açıklar. Yönetici yalnızca "Bu slota ata" demek yerine, "Bu slotta %92 uyumluluk skoru var çünkü hoca müsait, sınıf dolu değil ve aynı dönemden başka ders yok" gibi gerekçeli bir öneri görür. Bu şeffaflık, sisteme duyulan güveni artırır ve kötü atamalar yapılmadan önce müdahale imkânı tanır.

Sistem üç ana platformdan oluşmaktadır: Android mobil uygulama (öğretim görevlileri için birincil arayüz), web tabanlı yönetim paneli (admin ve süper admin için) ve RASA NLU tabanlı bir chatbot asistanı. Tüm bu bileşenler Ubuntu tabanlı bir sanal makine üzerinde çalışan FastAPI + PostgreSQL altyapısıyla iletişim kurar.

---

## 2. Proje Genel Bakış

### Amaç ve Kapsam

OptiClass'ın temel amacı, üniversite ders programlama sürecini merkezi, tutarlı ve kısmen akıllı bir platform üzerinden yönetmektir. Proje kapsamı şu işlevleri içermektedir:

- Öğretim görevlilerinin müsaitlik bilgilerini sisteme girmesi ve admin onayına sunması
- Admin'in ders atamalarını interaktif bir takvim grid'i üzerinden yapması
- XAI algoritmasının en uygun zaman dilimi ve derslik önerisini gerekçesiyle sunması
- Çok aşamalı (multi-phase) bir planlama süreci aracılığıyla öncelikli derslerin önce atanması
- Ortak dersler (COMMON) gibi özel durumların otomatik olarak bloke edilmesi
- RASA NLU chatbot ile kullanıcılara 7/24 yönlendirme desteği sağlanması

### Hedef Kullanıcı Kitlesi

| Rol | Platform | Yetki |
|---|---|---|
| `ADMIN` | Android + Web | Ders/Kullanıcı/Derslik yönetimi, takvim atama, XAI öneri |
| `INSTRUCTOR` | Android (yalnızca) | Müsaitlik girişi, kendi programını görme, admin ile mesajlaşma |
| `SUPER_ADMIN` | Web (yalnızca) | Admin yetkileri + Audit log görüntüleme, CSV export |

### Bitirme Ödevi Bağlamı

Proje; yazılım mühendisliği bitirme ödevi kapsamında, gerçek dünya kısıtlarına (DHCP'li VM, sınırlı kaynak) uyum sağlayarak üretilmiştir. Akademik özgünlük açısından en dikkat çekici yönler şunlardır: (1) XAI modülünün ders programlamaya özgü uyarlanmış karar ağacı tasarımı, (2) dinamik faz sistemi sayesinde n-aşamalı öncelik tabanlı planlama, (3) RASA NLU chatbot'un planlama iş akışına entegrasyonu.

---

## 3. Mimari Genel Bakış

### Sistem Katmanları

```
┌─────────────────────────────────────────────────────────┐
│              ANDROID MOBİL UYGULAMA (Kotlin)            │
│   LoginScreen ─ MainScaffold ─ Admin/Instructor Screens │
│   AppViewModel (AndroidViewModel)                        │
│   AppRepository (Global State, SnapshotState)           │
│   ApiService (Retrofit Interface)                        │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP/REST (JWT Bearer)
                         │
┌────────────────────────▼────────────────────────────────┐
│              WEB ADMIN PANELİ (Vanilla JS SPA)          │
│   index.html + app.js (~2700 satır) + api.js            │
│   nginx (Port 80 → 8000 reverse proxy)                  │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP/REST
                         │
┌────────────────────────▼────────────────────────────────┐
│              BACKEND (FastAPI / Python)                  │
│   main.py — REST API (~50 endpoint)                     │
│   JWT Auth (python-jose) ─ SHA-256 hash doğrulama       │
│   XAI: dt_evaluate_slot() — Decision Tree               │
│   Audit Log: audit_logs.csv — SSE stream                │
│   ChatBot Proxy: POST /chatbot → RASA                   │
└──────┬───────────────────────────┬──────────────────────┘
       │                           │
┌──────▼──────┐           ┌────────▼────────┐
│  PostgreSQL │           │   RASA NLU      │
│  (opticlass │           │  (Port 5005)    │
│  database)  │           │  23 intent      │
└─────────────┘           └─────────────────┘
```

### Android — MVVM + Repository Pattern

Android tarafı katı MVVM mimarisi ile organize edilmiştir:

- **`AppRepository`** (Singleton object): Tüm uygulama state'i burada tutulur. `mutableStateListOf` / `mutableStateMapOf` (Jetpack Compose SnapshotState) kullanılır; Compose UI, state değişikliklerini otomatik olarak algılar ve yeniden çizer. `AppRepository` doğrudan ağ çağrısı yapmaz.
- **`AppViewModel`** (AndroidViewModel): Tüm API çağrıları `viewModelScope.launch` ile Coroutine'de yapılır. Sonuç `AppRepository`'e yazılır; UI callback parametreleri ile bilgilendirilir.
- **`ApiService`** (Retrofit interface): Tüm HTTP endpoint'leri `suspend fun` olarak tanımlıdır.
- **UI (Compose Screens)**: State'i `AppRepository`'den okur; aksiyonları `AppViewModel` metodlarına callback olarak iletir.

### Web — SPA Mimarisi

Web paneli tek sayfalık uygulama (SPA) modeliyle çalışır. Sunucu tarafında hiçbir şablon motoru kullanılmaz; tüm sayfa geçişleri `handleRoute()` fonksiyonu ile `innerHTML` inject edilerek yapılır. `api.js` tüm fetch çağrılarını merkezi `apiFetch()` sarmalayıcısından geçirir.

---

## 4. Teknoloji Yığını

| Katman | Teknoloji | Versiyon / Açıklama | Seçilme Gerekçesi |
|--------|-----------|---------------------|-------------------|
| Android UI | Jetpack Compose + Material3 | API 24+ | Deklaratif UI, az kod, modern Android standardı |
| State Yönetimi | SnapshotState (`mutableStateListOf`) | Compose built-in | Compose ile doğal entegrasyon, LiveData gerekmez |
| HTTP Client | Retrofit + OkHttp + Gson | 2.11.0 / 4.12.0 | Kotlin suspend desteği, JSON dönüşümü kolay |
| Görsel | Coil | 2.6.0 | Compose native, base64 + HTTP URL desteği |
| Excel | Apache POI | 5.2.5 | `.xlsx` okuma için en yaygın Java kütüphanesi |
| Güvenli Depolama | EncryptedSharedPreferences (AES256-GCM) | security-crypto | Android Keystore ile donanım destekli şifreleme |
| Backend | FastAPI (Python) | — | Async, otomatik Swagger, hızlı prototipleme |
| Veritabanı | PostgreSQL 17 | — | JSONB desteği (schedule, slots), olgun ekosistem |
| Chatbot | RASA NLU | 3.x | Açık kaynak, on-premise NLU, custom intent eğitimi |
| Web Proxy | nginx | — | Statik dosya servisi + reverse proxy tek pakette |
| Web Excel | SheetJS (CDN) | — | Tarayıcı tarafında `.xlsx` ayrıştırma, kurulum gerektirmez |
| Kimlik Doğrulama | JWT (python-jose) + SHA-256 | — | Stateless auth, mobil ve web ortak kullanır |

---

## 5. Veri Modeli

### Tablo İlişkileri

```
users ──< availabilities        (1 user → 1 availability row)
users ──< schedules             (1 user → 1 schedule row, JSONB slots)
users ──< courses               (FK: lecturer_username → users.username)
courses >── classrooms          (FK: classroom_id → classrooms.id)
users ──< messages (sender)
users ──< messages (recipient)
users ──< notifications
users ──< schedule_history
app_settings                    (key-value; scheduling_phase, vb.)
```

### Tablo Tanımları ve Tasarım Kararları

**`users`**
```sql
CREATE TABLE users (
    username text PRIMARY KEY,
    password_hash text NOT NULL,   -- SHA-256
    role text NOT NULL,            -- ADMIN | INSTRUCTOR | SUPER_ADMIN
    full_name text NOT NULL,
    email text,
    avatar_url text,               -- data:image/jpeg;base64,... formatında
    department text DEFAULT '',
    must_change_password boolean DEFAULT false,
    created_at timestamptz DEFAULT now()
);
```

**`courses` — PK neden (code, department, email) üçlüsü?**

Aynı kod (örn. `EEE102`) birden fazla bölümde, hatta aynı bölümde farklı hocalarca verilebilir. Yalnızca `code` kullanılsaydı bu senaryolar çakışırdı. `email` alanı hocanın kimliğini taşır; `department` ise bölüm ayrımını sağlar. Bu üçlü birlikte bir dersin benzersiz kimliğini oluşturur.

```sql
CREATE TABLE courses (
    code text NOT NULL,
    name text NOT NULL,
    lecturer_username text,
    department text DEFAULT '' NOT NULL,
    email text DEFAULT '' NOT NULL,
    duration integer DEFAULT 1,
    classroom_id text,
    semester integer DEFAULT 1,
    student_count integer DEFAULT 0,
    priority integer DEFAULT 1,
    lecture_hours integer DEFAULT 0,
    lab_hours integer DEFAULT 0,
    lecture_assigned boolean DEFAULT false,
    lab_assigned boolean DEFAULT NULL,  -- NULL = lab yok, FALSE = atanmamış, TRUE = atanmış
    imported_at timestamptz DEFAULT now(),
    PRIMARY KEY (code, department, email)
);
```

**`schedules`** — Ders programı `JSONB` olarak saklanır. Anahtar format: `"Mon_08:00 AM"`. Bu yapı esnek slot genişletmeye ve sorgu kolaylığına imkân tanır.

**`app_settings`** — Sistem genelinde ayarları key-value olarak tutar. Şu an yalnızca `scheduling_phase` (varsayılan: `PHASE_1`) kullanılır. Faz geçişi bu tabloya `PUT` ile yazılır, `GET` ile okunur.

**`schedule_history`** — Her ders ataması/kaldırması olayı burada kaydedilir. `previous_course` ve `new_course` alanları `JSONB` olarak tutulur. Son 50 kayıt `LIMIT 50 ORDER BY id DESC` ile alınır.

---

## 6. API Katmanı

### Endpoint Grupları

| Grup | Endpoint Sayısı | Açıklama |
|------|----------------|----------|
| Auth | 1 | `POST /auth/login` |
| Users | 6 | CRUD + şifre değişikliği + avatar |
| Classrooms | 3 | Listeleme, ekleme, silme |
| Courses | 3 | Listeleme, bulk import, silme |
| Schedules | 3 | Tüm / tekil GET, PUT |
| Availabilities | 3 | Tüm / tekil GET, PUT |
| Messages | 3 | GET (filtreli), POST, PATCH (mark-read) |
| Notifications | 3 | GET, POST, PATCH |
| History | 2 | GET, POST |
| Settings | 3 | `GET/PUT /settings/scheduling_phase`, `GET /settings/phase_priorities` |
| XAI | 2 | `POST /schedule/suggest/{username}`, `POST /schedule/suggest-weekly` |
| ChatBot | 1 | `POST /chatbot` |
| Common Slots | 1 | `GET /common_course_slots` |
| Logs | 2 | `GET /logs`, `GET /logs/stream` (SSE) |

### JWT Auth Mekanizması

Giriş sırasında parola SHA-256 ile hash'lenerek sunucuya gönderilir. Parola düz metin hiçbir zaman iletilmez:

```kotlin
// Android — AppViewModel.kt
val passwordHash = sha256(password)
val response = RetrofitClient.instance.login(LoginRequest(username, passwordHash))
// Yanıtta gelen token "Bearer xxx" formatına çevrilir
authToken = "Bearer ${body.token}"
```

Token süresi `ACCESS_TOKEN_EXPIRE_MINUTES = 10080` (7 gün) olarak ayarlanmıştır. Tüm korumalı endpoint'ler `Authorization: Bearer <token>` başlığını zorunlu kılar. Web panelinde token `localStorage`'da, mobil uygulamada `EncryptedSharedPreferences`'ta saklanır.

### Kritik Endpoint Örneği — XAI Öneri

**İstek:**
```json
POST /schedule/suggest/john_doe
{
  "courseCode": "EEE201",
  "duration": 2,
  "lectureHours": 2,
  "labHours": 1,
  "suggestionType": "lecture",
  "classroomId": null
}
```

**Yanıt (özet):**
```json
{
  "courseCode": "EEE201",
  "courseName": "Circuit Theory",
  "suggestionType": "lecture",
  "algorithmNote": "Decision Tree evaluation with 9 nodes",
  "suggestions": [
    {
      "day": "Mon",
      "timeSlot": "09:00 AM",
      "classroomCode": "B201",
      "score": 0.91,
      "summary": "Excellent fit",
      "path": [
        {"node": "instructor_available", "result": "pass", "isHard": true},
        {"node": "slot_continuity", "result": "pass", "isHard": true},
        {"node": "classroom_capacity", "result": "pass", "isHard": true},
        {"node": "instructor_day_load", "result": "partial", "scoreContribution": -0.03}
      ]
    }
  ]
}
```

---

## 7. Dinamik Faz (Scheduling Phase) Sistemi

Faz sistemi, OptiClass'ın en özgün ve karmaşık bileşenlerinden biridir. Üniversite ortamında bazı dersler pek çok bölümü ilgilendirdiğinden çok daha dikkatli planlanmalıdır; bu dersler önce yerleştirilmezse sonraki bölümlerin dersleri için çok az uygun slot kalır.

### Faz Sayısının Otomatik Hesaplanması

Sistem, faz sayısını ve her fazın önceliğini sabit olarak kodlamaz. Bunun yerine, import edilen derslerdeki **benzersiz `priority` değerlerinin sayısı** kadar faz oluşturulur:

```
priority değerleri: {8, 2, 1} → 3 faz
PHASE_1 → priority 8 (en yüksek; ortak dersler)
PHASE_2 → priority 2
PHASE_3 → priority 1
```

Bu değerler `GET /settings/phase_priorities` endpoint'inden alınır ve `AppRepository.phasePriorities: List<Int>` olarak saklanır.

```kotlin
// AppRepository.kt
val currentPhaseIndex: Int get() {
    val num = schedulingPhase.removePrefix("PHASE_").toIntOrNull() ?: 1
    return num - 1
}
val currentPhasePriority: Int get() = phasePriorities.getOrElse(currentPhaseIndex) { -1 }
val previousPhasePriorities: Set<Int> get() = phasePriorities.take(currentPhaseIndex).toSet()
```

### Faz Geçiş Mekanizması

Geçiş `UpdateCalendarPage`'de banner üzerindeki "Phase N+1 →" butonuna basılarak başlatılır. Onay dialogu, hedef faz numarasını ve `priority` değerini gösterir. Geçiş `PUT /settings/scheduling_phase` ile DB'ye yazılır; bu işlem **tek yönlüdür** (geri dönüş UI'dan mümkün değildir).

Önemli not: `setSchedulingPhase` metodunda bir `catch` bloğu, API çağrısı başarısız olsa bile Android state'ini günceller. Bu bilinçli bir tasarım tercihidir; sunucu kapalıyken de kullanıcı deneyimi kesilmez, ancak sessiz desenkronizasyon riski doğar (Bkz. Bölüm 13).

### Faz Kısıtları — UpdateCalendarPage

**Hoca filtresi:** Aktif fazda yalnızca `course.priority == currentPhasePriority` olan hoçalar dropdown'da listelenir. Bu, yanlış fazda yanlış hocaya ders atanmasını önler.

**`semesterBlockedSlots`:** Bir ders seçildiğinde, önceki fazların derslerini taşıyan slot'lar kırmızıya boyanır ve tıklanamaz hale gelir:

```kotlin
// UpdateCalendarPage.kt — Hesaplama mantığı
val semesterBlockedSlots = remember(selectedCourseToAssign, ...) {
    // Diğer hocaların programlarını tara
    // slotCourse.priority in AppRepository.previousPhasePriorities &&
    // (slotCourse.semester == selectedCourse.semester) → bloke
}
```

**`semesterConflict`:** Atama dialogunda aynı dönem çakışması hard block olarak uygulanır; yönetici "Yine de ata" seçeneği göremez.

### Faz Kısıtları — MyAvailabilityPage

Bir hocanın müsaitlik formuna erişimi faz sistemiyle bağlantılıdır:

```
unlockedPriorities = phasePriorities.take(currentPhaseIndex + 1)
```

Eğer hocanın hiçbir dersi `unlockedPriorities` setinde değilse, form kilitlenir ve "Phase N (priority X) scheduling is in progress. Availability form will open when your priority group is reached." mesajı gösterilir.

---

## 8. XAI (Açıklanabilir Yapay Zeka) Modülü

### Genel Felsefe

Geleneksel ders programı optimizasyon araçları bir çözüm üretir ama neden o çözümü ürettiğini açıklamaz. OptiClass'taki XAI modülü, her slot önerisini "siyah kutu" olarak değil, adım adım gerekçelendirilmiş bir karar yolu olarak sunar. Bu yaklaşım, sisteme güveni artırır ve kötü atamaların fark edilmesini kolaylaştırır.

### Karar Ağacı — 9 Node

Algoritma, her (gün, zaman dilimi, derslik) kombinasyonunu 9 node'lu bir karar ağacından geçirir. Node'lar iki kategoriye ayrılır:

**Hard constraint node'lar (eleme yapar — başarısız olursa slot tamamen elenir):**

| # | Node | Kural |
|---|------|-------|
| 1 | `instructor_available` | Hoca o slotta müsait değilse → eleme |
| 2 | `no_semester_conflict` / `common_course_check` | Aynı dönemde çakışan ders → eleme |
| 3 | `no_classroom_conflict` | Aynı derslik aynı anda başka hoçada → eleme (ONLINE muaf) |
| 4 | `slot_continuity` | Multi-saatlik ders için ardışık tüm slotlar müsait değilse → eleme |
| 5 | `classroom_capacity` | Derslik kapasitesi < öğrenci sayısı → eleme |

**Soft penalty node'lar (skor düşürür — eleme yapmaz):**

| # | Node | Ceza |
|---|------|------|
| 6 | `same_dept_day_load` | Aynı dept+dönem aynı gün başka ders: **-4%** her ders için |
| 7 | `diff_dept_day_load` | Farklı dept aynı dönem aynı gün: **-3%** her ders için |
| 8 | `classroom_day_usage` | Aynı derslik aynı gün başka slotta kullanım: **-2%** |
| 9 | `instructor_day_load` | Hoca aynı gün başka ders: **-3%** her ders için |

Skor `1.0`'dan başlar ve soft cezalar eksiltilir. Maksimum skor **%100**'dür.

```python
# main.py — dt_evaluate_slot() temel mantığı
def dt_evaluate_slot(slot, classroom, course, username, all_schedules, availability):
    score = 1.0
    path = []
    
    # Hard node: instructor availability
    if slot not in availability.get(day, []):
        return fail_hard(path, "instructor_available")
    
    # Hard node: slot continuity (multi-hour)
    consecutive = [time_slots[i], time_slots[i+1], ...]
    if not all(s in availability[day] for s in consecutive):
        return fail_hard(path, "slot_continuity")
    
    # Soft node: instructor day load
    instr_same_day = count_instructor_lessons(username, day, all_schedules)
    score -= instr_same_day * 0.03
    path.append({"node": "instructor_day_load", "scoreContribution": -instr_same_day * 0.03})
    
    return {"score": score, "path": path, "eliminated": False}
```

### Skor Renk Eşikleri (Android)

- **≥ %85** → Yeşil (Excellent)
- **≥ %60** → Turuncu (Good/Moderate)
- **< %60** → Kırmızı (Poor)

### Accordion UI

Android'de öneri dialogu "accordion" tasarımıyla sunulur: her seçenek kart başlığında gün/saat/oda/skor görünür; tıklanınca genişler ve önce **"Apply Option N"** butonu, ardından skor bar ve Decision Path listesi açılır. "Apply" butonunun en üstte olması bilinçli bir UX kararıdır; derin scroll gerektiren dialog'larda alt kısımdaki butonlar görünmeyebilir.

### Haftalık XAI — suggest-weekly

`POST /schedule/suggest-weekly` endpoint'i, seçilen fazdaki tüm dersler için tam bir haftalık program önerisi üretir. Dört strateji çalışır (COMMON ders yoksa üç):

1. **By Student Count** — En fazla öğrencili dersler önce yerleştirilir
2. **By Lecture Duration** — En uzun dersler önce yerleştirilir
3. **COMMON First** — Ortak dersler en başa alınır
4. **COMMON Spread** *(yalnızca COMMON ders varsa)* — Ortak dersler dönemlere göre interleave edilerek farklı günlere yayılır

Her strateji greedy yaklaşımla çalışır; hoca müsaitliği + sınıf kapasitesi + dönem çakışması hard constraint olarak uygulanır. Yerleştirilemez derslere fallback denenir (dönem/COMMON kısıtları gevşetilir; yalnızca hoca müsaitliği + sınıf uygunluğu korunur).

---

## 9. RASA NLU ChatBot

### Mimari

```
Android/Web kullanıcısı
       ↓ POST /chatbot (message)
FastAPI (main.py) — proxy
       ↓ POST http://localhost:5005/webhooks/rest/webhook
RASA NLU Sunucusu (port 5005)
       ↓ intent sınıflandırma → utter_* yanıtı
FastAPI → Android/Web
```

FastAPI, doğrudan RASA API'sine bir proxy görevi üstlenir. Bu mimari iki avantaj sağlar: (1) RASA'nın adresi ve portu Android/Web'den gizlenir; (2) timeout ve fallback kontrolü merkezi olarak FastAPI'de yapılır. RASA yanıt vermezse (kapalı ya da timeout) backend "Assistant is currently unavailable" mesajını döner.

### 23 Intent — Kategorik Dağılım

| Kategori | Intent'ler |
|---|---|
| Genel Etkileşim (5) | `greet`, `goodbye`, `thanks`, `affirm`, `deny` |
| Navigasyon (6) | `ask_navigate_schedule`, `ask_navigate_availability`, `ask_navigate_lectures`, `ask_navigate_settings`, `ask_navigate_messages`, `ask_what_is_app` |
| Admin İşlevleri (5) | `ask_import_courses`, `ask_manage_classrooms`, `ask_manage_users`, `ask_assign_course`, `ask_send_notification` |
| Faz & Ders Sistemi (4) | `ask_phase_explanation`, `ask_phase_switch`, `ask_common_course`, `ask_availability_admin` |
| Teknik (2) | `ask_xai`, `ask_change_password` |
| Kapsam Dışı (1) | `out_of_scope` |

### DIETClassifier + FallbackClassifier Pipeline

```yaml
# config.yml
pipeline:
  - name: WhitespaceTokenizer
  - name: RegexFeaturizer
  - name: LexicalSyntacticFeaturizer
  - name: CountVectorsFeaturizer
  - name: DIETClassifier
    epochs: 100
  - name: FallbackClassifier
    threshold: 0.3
    ambiguity_threshold: 0.1
```

`DIETClassifier` (Dual Intent and Entity Transformer) Transformer tabanlı bir modeldir ve intent sınıflandırmasını aynı anda birden fazla görevle öğrenir. Sınıflandırma güveni 0.3'ün altında kalırsa `FallbackClassifier` devreye girer ve `utter_default` yanıtını tetikler.

Her intent için 10–40 örnek cümle `data/nlu.yml`'de tanımlıdır. `ask_navigate_schedule` için 46 farklı ifade ("where is my schedule", "how do I see my timetable", "I need to check my classes" gibi) eğitim verisini oluşturur.

---

## 10. Android Mobil Uygulama

### Ekran Envanteri

**Admin Ekranları:**

| Ekran | Ana İşlev |
|---|---|
| `AdminMainPage` | Inbox (tüm mesajlar), dashboard istatistikleri, 30sn'de bir polling |
| `UpdateCalendarPage` | Faz banner, hoca seçimi, course pill, ders atama grid'i, XAI öneri |
| `DataImportPage` | Excel import (Apache POI), önizleme, mevcut dersler listesi |
| `ClassroomsPage` | Derslik ekleme/silme/import |
| `UserTransactionsPage` | Kullanıcı CRUD, şifre sıfırlama, silme koruması |
| `InstructorAvailabilityAdminPage` | Tüm hocaların müsaitlik grid'i (salt okunur), refresh |
| `WeeklySchedulePage` | Mevcut program grid'i + XAI haftalık öneri (3–4 strateji) |

**Instructor Ekranları:**

| Ekran | Ana İşlev |
|---|---|
| `InstructorMainPage` | Dashboard, availability durumu kartı (tıklanabilir), 30sn polling |
| `MySchedulePage` | Kendi haftalık programı (ders kodu + derslik kodu, devam slotları dahil) |
| `MyLecturesPage` | Atanan dersler listesi |
| `MyAvailabilityPage` | Faz kilitli/açık kontrol, müsaitlik grid'i, "Save & Send to Admin" |

**Ortak Ekranlar:**

| Ekran | Ana İşlev |
|---|---|
| `SettingsPage` | Şifre değiştirme, avatar upload |
| `ChatBox` | Admin-Instructor ikili mesajlaşma, 5sn polling |
| `ChatBotPage` | RASA chatbot arayüzü (her iki rol görür) |

### Polling Mekanizmaları

Sistemde iki farklı polling döngüsü çalışır:

1. **30 saniyelik döngü (InstructorMainPage):** `LaunchedEffect` + `delay(30_000)` ile bildirim, program ve mesaj güncellenir.
2. **5 saniyelik döngü (ChatBox):** Yalnızca ChatBox açıkken aktif olur; yeni mesajları anlık getirir.

### Session Kalıcılığı

```kotlin
// AppViewModel.kt — EncryptedSharedPreferences kurulumu
val masterKey = MasterKey.Builder(application)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
val prefs = EncryptedSharedPreferences.create(
    application, "opticlass_session", masterKey,
    PrefKeyEncryptionScheme.AES256_SIV,
    PrefValueEncryptionScheme.AES256_GCM
)
```

Uygulama kapatılıp açılsa bile `authToken`, `username` ve `role` şifreli depodan okunur; `fetchInitialData` otomatik çağrılır. Kullanıcı 7 gün boyunca tekrar giriş yapmak zorunda kalmaz.

### Excel Import Akışı

`DataImportPage`'de kullanıcı `.xlsx` dosyası seçer. Apache POI ile her satır okunur; 10 sütun beklenir (Code, Name, Lecturer, Dept, Email, Semester, StudentCount, Priority, LecHours, LabHours). Önizleme listesinde her ders için "COMMON" badge, dönem, öğrenci sayısı ve ders saati gösterilir. Import sonrasında yeni hoçalar için kullanıcı adı+şifre çifti otomatik oluşturulur ve `CredentialsDialog` açılır.

### Avatar Yönetimi

Kullanıcı galeriden fotoğraf seçer → `ImageDecoder` ile bitmap alınır → max 256px'e scale edilir → JPEG %70 kaliteyle encode edilir → `data:image/jpeg;base64,...` string'ine çevrilir → `PUT /users/{username}/avatar` ile DB'ye yazılır. `UserAvatar` bileşeni URI tipine göre Coil (http://), Base64 decode veya ContentResolver kullanır.

---

## 11. Web Admin Paneli

### 8 Sayfa

| Sayfa | Erişim | İçerik |
|---|---|---|
| Dashboard | ADMIN+ | İstatistik kartları, faz progress bar, son schedule değişiklikleri, atanmamış ders uyarısı |
| Users | ADMIN+ | Arama/filtre tablosu, Add/Edit/Delete/ResetPassword modal |
| Classrooms | ADMIN+ | Tablo, manuel ekleme, Excel import |
| Courses | ADMIN+ | Arama+bölüm filtresi, SheetJS ile Excel import, sil |
| Calendar | ADMIN+ | Faz banner, instructor dropdown, course pill, 5×10 grid, XAI öneri accordion |
| Availability | ADMIN+ | Her instructor'ın salt-okunur 5×10 availability grid'i |
| Weekly Schedule | ADMIN+ | Faz seçici, mevcut program görünümü, XAI Generate (3–4 strateji accordion) |
| Logs | SUPER_ADMIN | Gerçek zamanlı audit log tablosu (SSE), action filtresi, CSV export |

### SUPER_ADMIN ve Audit Logs

`SUPER_ADMIN` rolü yalnızca web panelinde mevcuttur; mobil uygulamada görünmez. Varsayılan kimlik bilgileri: `super_admin` / `SuperAdmin1!` (ilk sunucu başlatmasında `create_tables()` tarafından otomatik oluşturulur).

Audit loglar veritabanında değil, `audit_logs.csv` dosyasında saklanır. Bu tasarım kararı veritabanı bağımlılığını azaltır ve CSV'nin herhangi bir araçla analiz edilmesini sağlar.

Loglanan aksiyonlar: `LOGIN`, `LOGIN_FAILED`, `LOGOUT`, `CREATE_USER`, `UPDATE_USER`, `DELETE_USER`, `RESET_PASSWORD`, `ADD_CLASSROOM`, `DELETE_CLASSROOM`, `IMPORT_COURSES`, `DELETE_COURSE`, `SAVE_SCHEDULE`, `SET_PHASE`, `SEND_NOTIFICATION`

### SSE ile Gerçek Zamanlı Log Akışı

```javascript
// app.js — SSE bağlantısı
const token = getToken();
logsEventSource = new EventSource(`/logs/stream?token=${token}`);
logsEventSource.onmessage = (e) => {
    const entry = JSON.parse(e.data);
    tbody.insertAdjacentHTML('afterbegin', renderLogRow(entry));
    // 1.5sn indigo highlight ile yeni giriş vurgulanır
};
```

`EventSource` HTTP başlığı desteklemediği için token query param olarak iletilir. Bağlantı 25 saniye boyunca event gelmezse keepalive gönderilir; sayfa değiştirilince bağlantı `close()` ile kapatılır.

### SheetJS ile Browser-Side Excel Import

Web panelinde Excel dosyaları sunucuya yüklenmez; tarayıcıda SheetJS kütüphanesiyle parse edilir. Bu yaklaşım sunucu yükünü azaltır ve anlık validasyon sağlar. Import öncesinde tüm satırlar hard validasyondan geçer: boş zorunlu alanlar, geçersiz email formatları, integer olmayan sayısal değerler gibi hatalar bulunduğunda **tüm dosya reddedilir** ve hiçbir satır eklenmez.

### nginx Reverse Proxy

```nginx
server {
    listen 80;
    location / {
        proxy_pass http://localhost:8000;
        proxy_set_header X-Real-IP $remote_addr;
        client_max_body_size 20M;
    }
}
```

nginx, web dosyalarını `/static` altında serve eder ve tüm API çağrılarını `localhost:8000`'e yönlendirir. `X-Real-IP` başlığı audit log'daki IP tespiti için gereklidir.

---

## 12. Güvenlik

### SHA-256 Şifre Hashleme

Parolalar hiçbir zaman düz metin olarak iletilmez. Hem Android hem web istemcisi SHA-256 hash uygular ve hash'i gönderir. Backend'de hash, veritabanındaki hash ile karşılaştırılır. Salt kullanılmamakta olup bu bilinen bir zayıflıktır (Bkz. Bölüm 13).

### JWT Token Yönetimi

Token süresi 7 gündür (`ACCESS_TOKEN_EXPIRE_MINUTES = 10080`). Yenileme (refresh token) mekanizması bulunmamaktadır; 7 gün sonra kullanıcı tekrar giriş yapmak zorundadır. Token, Android'de AES256-GCM şifreli depoda, web'de `localStorage`'da tutulur.

### EncryptedSharedPreferences (AES256-GCM)

Android Keystore entegrasyonuyla donanım destekli şifreleme sağlanır. Hem anahtar hem değer şifrelenir (`AES256_SIV` + `AES256_GCM`). Cihaz rootlanmış olsa bile depolanan token'a doğrudan erişim son derece güçtür.

### Rol Tabanlı Erişim Kontrolü (RBAC)

```python
# main.py — is_admin() helper
def is_admin(role: str) -> bool:
    return role in ("ADMIN", "SUPER_ADMIN")
```

Tüm admin endpoint'leri bu kontrolden geçer. `INSTRUCTOR` kullanıcısı admin sayfalarına web üzerinden giriş yapamaz: `"Instructor accounts use the mobile app only."` hatası döner.

Mobil uygulamada `AppDestinations` enum'unda her sayfanın `roleRestriction` alanı tanımlıdır; MainScaffold bu alana göre erişimi filtrelemez.

### Audit Log Sistemi

Kritik aksiyonlar `log_action()` çağrısıyla CSV'ye yazılır. Thread-safety için `threading.Lock()` kullanılır. Her giriş; aktör, rol, aksiyon, hedef, detay, IP adresi ve tarayıcı bilgisini içerir. Bu kayıtlar SUPER_ADMIN tarafından gerçek zamanlı olarak izlenebilir.

### Import Validasyonunun Güvenlik Boyutu

Web panelindeki Excel import validasyonu yalnızca kullanılabilirlik değil, güvenlik açısından da kritiktir. Sunucu tarafında Python kodu da validasyon uygular; ancak istemci tarafı validasyon, zararlı ya da hatalı biçimlendirilmiş verinin backend'e ulaşmasını önler.

---

## 13. Bilinen Limitasyonlar ve Teknik Borç

### Cross-Instructor Draft Classroom Conflict

İki admin aynı anda farklı hoçalar için ders atarken, kaydedilmemiş draft'lar arasındaki derslik çakışması tespit edilemez. Bu kontrol yalnızca veritabanına kaydedilmiş programlar üzerinde çalışır. Çözüm için global bir draft state veya optimistic locking mekanizması gerekir; geliştirme sürecinde ertelenmiştir.

### Token Yenileme Mekanizması Eksikliği

JWT token 7 gün sonra geçersiz olur ve yenileme mekanizması yoktur. Kullanıcı 7. günden sonra öngörüsüz biçimde sistemden düşer. Refresh token + silent renew akışı eklenmesi önerilir.

### setSchedulingPhase Silent Desync Riski

```kotlin
// AppViewModel.kt
} catch (e: Exception) {
    // Backend'e yazılamasa bile Android state güncellenir
    AppRepository.schedulingPhase = phase
    onResult(true)  // Kullanıcıya başarılı görünür
}
```

Sunucu kapalıyken faz geçişi Android'de "başarılı" görünür ama DB'ye yazılmaz. Uygulama kapatılıp açıldığında DB'den eski faz okunur; desenkronizasyon oluşur. Daha sağlıklı yaklaşım `onResult(false)` döndürmek ve kullanıcıyı bilgilendirmektir.

### Uvicorn systemd Service Olarak Kurulmamış

Sunucu her VM yeniden başlatılmasında `uvicorn main:app ...` komutu SSH üzerinden manuel çalıştırılmak zorundadır. `systemd` servisi oluşturulursa VM yeniden başlatmalarında API otomatik olarak ayağa kalkar.

### Şifre Hashleme — Salt Eksikliği

SHA-256 tek başına, rainbow table saldırılarına karşı savunmasızdır. Bcrypt veya Argon2 gibi adaptif hash algoritmaları önerilir; bunlar otomatik salt ekler ve brute-force saldırılarını yavaşlatır.

### AddUserDialog Şifre Validasyonu Tutarsızlığı

`AddUserDialog`'da parola yalnızca `password.length < 6` ile kontrol edilirken `ForceChangePasswordScreen` ve `ChangePasswordDialog` tam `validatePassword()` fonksiyonunu kullanır (min 8 karakter, büyük/küçük harf, rakam, özel karakter). Bu tutarsızlık, admin tarafından yaratılan hesaplarda zayıf parola riskine yol açar.

---

## 14. Geliştirme Süreci ve Kararlar

### Kritik 5 Mimari Karar

**1. State Yönetimi: AppRepository Singleton**

Başlangıçta state her ViewModel'de ayrı tutulmayı planlanmıştı. Birden fazla ekran aynı veriyi göstermesi gerekince merkezi bir singleton'a geçildi. `GlobalState.kt` dosyası boş kaldı; tüm state `AppRepository`'e taşındı. Bu, callback zincirini (`OptiClassApp → MainScaffold → Screens`) gerektirdi ama state tutarlılığını garanti etti.

**2. Ders Tekil Kimliği: (code, department, email) Üçlüsü**

Başlangıçta `code` tek başına kimlik olarak kullanıldı. Aynı kod farklı bölümlerde veya farklı hoçalarda görününce çakışmalar yaşandı. Üçlü anahtar yapısı hem DB PK'sında hem Android `syncCourses` dedup mantığında tutarlı uygulanmaktadır.

**3. Şifre API Doğrulaması: Yerel Karşılaştırma Yok**

Kullanıcıların parolaları API sync'ten sonra `""` olarak tutulur. Yerel SHA-256 karşılaştırması yapılmaz; her doğrulama API çağrısıyla gerçekleşir. Bu, offline senaryolarda login imkânını ortadan kaldırır ama güvenlik açısından doğru bir tercihdir.

**4. Faz Sistemi: Dinamik, Sabit Sayılı Değil**

Başlangıçta sadece PHASE_1 ve PHASE_2 tasarlandı. Projenin ilerleyen aşamalarında N-fazlı bir yapıya geçildi; `PHASE_N` regex formatı kabul eden endpoint yazıldı ve `phasePriorities` listesi dinamik hale getirildi. Bu değişiklik önemli bir refactor gerektirdi ama sistemi çok daha esnek kıldı.

**5. XAI Scoring: Binary'den Penalty Tabanlıya**

İlk XAI tasarımında classroom_fits (+%45) ve day_variety (+%20) gibi pozitif katkılar kullanıldı. Bu yaklaşım bazı kötü slot'ların yüksek puan almasına yol açtı. "Skor 1.0'dan başlar, soft cezalar eksiltilir" modeline geçilince hem skor yorumu kolaylaştı hem de "mükemmel slot" kavramı netleşti.

### Refactor Edilen / Kaldırılan Özellikler

- **`encodeUsername` / `decodeUsername`:** Kullanıcı adları bir dönem Base64 encode ediliyordu. Bu gereksiz kompleksite eklediğinden kaldırıldı; plain text kullanılmaktadır.
- **`labSuggestionPending: Boolean`:** Lab öneri akışını yönetmek için kullanılan bu flag, `assignmentMode: String?` ("lecture"/"lab"/null) state'iyle değiştirildi. Daha temiz ve genişletilebilir bir yapı sağladı.
- **`selectedDuration` (duration chip):** Atama dialogunda kullanıcının süreyi seçmesi gereken chip kaldırıldı. `appliedDuration` artık `assignmentMode`'a göre otomatik hesaplanır.
- **`SchedulingGridEnhanced`'daki X butonu:** Hücredeki ayrı bir "Temizle" butonu kaldırıldı. Dolu bir hücreye tıklanınca direkt silme yapılır; bu UX akışı daha doğal bulundu.

---

## 15. Deployment ve Operasyon

### Altyapı

```
Windows Host
└── VirtualBox
    └── Ubuntu VM (Bridge mode, DHCP)
        ├── PostgreSQL 17
        ├── uvicorn (main.py, port 8000)
        ├── nginx (port 80 → 8000)
        └── RASA NLU (port 5005, ~/rasa-env)
```

### Sunucu Başlatma Adımları

```bash
# 1. SSH bağlantısı (IP değişmiş olabilir)
ssh vboxuser@<IP>

# 2. FastAPI API başlatma
cd ~/opticlass-api
source venv/bin/activate
uvicorn main:app --host 0.0.0.0 --port 8000

# 3. RASA başlatma (yeni terminal)
source ~/rasa-env/bin/activate
cd ~/opticlass-chatbot
rasa run --enable-api --cors "*" --port 5005

# 4. Doğrulama
# http://<IP>:8000 → {"message": "OptiClass API Online!"}
```

### Güncelleme Süreci

```bash
# Kaynak dosyaları VM'e kopyalama
scp main.py vboxuser@<IP>:/home/vboxuser/opticlass-api/main.py
scp -r web/ vboxuser@<IP>:/home/vboxuser/opticlass-api/

# SSH'da uvicorn yeniden başlatma
# Ctrl+C → yukarıdaki uvicorn komutunu tekrar çalıştır
```

### Sıfırdan DB Kurulumu

```bash
# Windows'tan opticlass.sql gönderme
scp opticlass.sql vboxuser@<IP>:/home/vboxuser/

# VM'de veritabanını yeniden oluşturma
psql -U yusuf -c "DROP DATABASE IF EXISTS opticlass;"
psql -U yusuf -c "CREATE DATABASE opticlass;"
psql -U yusuf opticlass < opticlass.sql

# Not: super_admin kullanıcısı create_tables() ile otomatik oluşur
# (SQL dump'ta yer almaz; ilk API başlatmasında eklenir)
```

### DHCP ve IP Yönetimi

VM Bridge modda çalıştığından IP her oturumda değişebilir. IP değişince:
1. VM'de `ip a` komutuyla yeni IP'yi öğren
2. `network/RetrofitClient.kt` dosyasındaki `BASE_URL`'i güncelle
3. Android uygulamasını yeniden derle

Bu DHCP bağımlılığı production ortamı için ciddi bir operasyonel risk oluşturur.

---

## 16. Sonuç ve Gelecek Çalışmalar

### Mevcut Durum Değerlendirmesi

OptiClass, tanımladığı problemleri işlevsel düzeyde çözmektedir. Faz tabanlı planlama mekanizması, XAI slot önerisi ve RASA chatbot entegrasyonu akademik bağlamda özgün katkılar sunmaktadır. Sistem, gerçek bir üniversite ortamında pilot olarak kullanılabilecek olgunluktadır; ancak üretim ortamına taşınmadan önce aşağıdaki eksikler giderilmelidir.

### Üretim İçin Yapılması Gerekenler

1. **Token Yenileme:** Refresh token mekanizması eklenmeli; 7 günlük sabit süre kaldırılmalıdır.
2. **systemd Service:** Uvicorn ve RASA, sistem başlangıcında otomatik başlamalı; manuel SSH prosedürü kaldırılmalıdır.
3. **Statik IP / DNS:** DHCP yerine statik IP veya iç DNS kaydı kullanılmalıdır.
4. **Şifre Hashleme:** SHA-256 yerine Bcrypt/Argon2 kullanılmalıdır.
5. **SSL/TLS:** Tüm iletişim HTTPS üzerinden yapılmalıdır.
6. **Cross-Instructor Conflict:** Global draft state veya gerçek zamanlı koordinasyon mekanizması eklenmeli.

### Önerilen Geliştirmeler

- **Docker Compose:** Tüm servisler (FastAPI, PostgreSQL, RASA, nginx) container'a alınarak deployment tekrarlanabilir hale getirilmelidir.
- **CI/CD Pipeline:** GitHub Actions ile otomatik test ve deploy akışı kurulmalıdır.
- **Mobil Bildirim (FCM):** Polling mekanizması Firebase Cloud Messaging ile değiştirilirse pil tüketimi azalır ve anlık bildirimler sağlanır.
- **AddUserDialog Validasyon Tutarsızlığı:** `validatePassword()` tüm parola giriş noktalarında tutarlı olarak kullanılmalıdır.
- **RASA Model Güncellemesi:** Yeni intent'ler eklendikçe `rasa train` çalıştırılmalı; model sürüm takibi yapılmalıdır.
- **Çoklu Dil Desteği:** Chatbot şu an yalnızca İngilizce çalışmakta; Türkçe intent desteği eklenebilir.

### Akademik Katkı ve Özgün Yönler

OptiClass'ın akademik özgünlüğü üç temel alanda yoğunlaşmaktadır:

1. **Dinamik Faz Sistemi:** N-fazlı, priority değerlerinden otomatik türetilen, geri alınamaz faz geçişleriyle kontrollü bir planlama modeli. Bu yaklaşım literatürdeki sabit-fazlı sistemlerden farklıdır.

2. **XAI Entegrasyonu:** Ders programlamaya özgü uyarlanmış 9-node'lu Karar Ağacı; hard/soft constraint ayrımı, ceza tabanlı skorlama ve adım adım açıklama mekanizmasıyla şeffaf bir öneri sistemi sunar.

3. **Bütünleşik Platform:** Android (öğretim görevlileri), Web (admin/super_admin) ve RASA chatbot'un tek bir FastAPI backend'ine entegrasyonu; gerçek dünya çok kullanıcılı senaryolarını destekleyen eksiksiz bir sistem oluşturur.

---

*Bu rapor OptiClass projesinin kaynak kodu (CLAUDE.md, AppViewModel.kt, AppRepository.kt, ApiService.kt, web/app.js, web/api.js, rasa_chatbot/domain.yml, rasa_chatbot/data/nlu.yml, opticlass.sql) incelenerek hazırlanmıştır.*
