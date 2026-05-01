from fastapi import FastAPI, HTTPException, Depends, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import OAuth2PasswordBearer
from pydantic import BaseModel
from typing import List, Optional, Dict
from datetime import datetime, timedelta
from jose import JWTError, jwt
import psycopg2
import psycopg2.extras
import json

# --- AYARLAR VE VERİTABANI BAĞLANTISI ---
DB_URL = "postgresql://yusuf:123456@localhost/opticlass"
SECRET_KEY = "opticlass_cok_gizli_anahtar"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 10080  # 7 gün

app = FastAPI(title="OptiClass API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="auth/login", auto_error=False)

def get_db_connection():
    return psycopg2.connect(DB_URL, cursor_factory=psycopg2.extras.RealDictCursor)

def create_access_token(data: dict):
    to_encode = data.copy()
    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

def clean_token(raw: str) -> str:
    """Bearer Bearer Bearer... ne kadar gelirse gelsin temizle"""
    token = raw.strip()
    while token.lower().startswith("bearer "):
        token = token[7:].strip()
    return token

def verify_token(authorization: str = Depends(oauth2_scheme)):
    if not authorization:
        raise HTTPException(status_code=401, detail="Token bulunamadı")

    token = clean_token(authorization)

    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username: str = payload.get("sub")
        if username is None:
            raise HTTPException(status_code=401, detail="Geçersiz token")
        return payload
    except JWTError as e:
        raise HTTPException(status_code=401, detail=f"Yetkisiz erişim: {str(e)}")

# --- PYDANTIC MODELLERİ ---
class LoginRequest(BaseModel):
    username: str
    passwordHash: str

class LoginResponse(BaseModel):
    token: str
    role: str
    username: str
    mustChangePassword: bool

class UserDto(BaseModel):
    username: str
    role: str
    fullName: str
    email: Optional[str] = None
    department: Optional[str] = ""

class UserCreateDto(BaseModel):
    username: str
    passwordHash: str
    role: str
    fullName: str
    email: Optional[str] = None
    department: Optional[str] = ""

class UserUpdateDto(BaseModel):
    role: str
    fullName: str
    email: Optional[str] = None
    department: Optional[str] = ""

class ChangePasswordRequest(BaseModel):
    currentPasswordHash: str
    newPasswordHash: str

class ClassroomDto(BaseModel):
    id: str
    roomCode: str
    capacity: int

class CourseDto(BaseModel):
    code: str
    name: str
    lecturerUsername: Optional[str] = None
    department: str = ""
    email: str = ""
    duration: int = 1
    classroomId: Optional[str] = None

class AvailabilityDto(BaseModel):
    instructorUsername: str
    slots: Dict[str, List[str]]

class ScheduleDto(BaseModel):
    instructorUsername: str
    slots: Dict[str, Optional[CourseDto]]

class MessageDto(BaseModel):
    id: Optional[int] = None
    senderUsername: str
    recipientUsername: str
    content: str
    isRead: bool = False
    createdAt: Optional[int] = None

class NotificationDto(BaseModel):
    id: Optional[str] = None
    recipientUsername: str
    text: str
    isRead: bool = False

class ScheduleHistoryDto(BaseModel):
    changedBy: str
    instructorUsername: str
    instructorFullName: str
    day: str
    timeSlot: str
    previousCourseCode: Optional[str] = None
    newCourseCode: Optional[str] = None

# --- AUTH ENDPOINTS ---

@app.post("/auth/login", response_model=LoginResponse, tags=["Auth"])
def login(request: LoginRequest):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT username, password_hash, role, must_change_password FROM users WHERE username = %s", (request.username,))
    user = cur.fetchone()
    cur.close()
    conn.close()

    if not user or user["password_hash"] != request.passwordHash:
        raise HTTPException(status_code=401, detail="Kullanıcı adı veya şifre hatalı")

    token = create_access_token(data={"sub": user["username"], "role": user["role"]})
    return LoginResponse(
        token=token, role=user["role"], username=user["username"],
        mustChangePassword=user["must_change_password"]
    )

# --- USERS CRUD ---

@app.get("/users", response_model=List[UserDto], tags=["Users"])
def get_users(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT username, role, full_name, email, department FROM users")
    users = cur.fetchall()
    cur.close()
    conn.close()
    return [UserDto(username=u["username"], role=u["role"], fullName=u["full_name"], email=u["email"], department=u["department"]) for u in users]

@app.post("/users", response_model=UserDto, tags=["Users"])
def create_user(user: UserCreateDto, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN": raise HTTPException(status_code=403, detail="Yetkisiz")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("INSERT INTO users (username, password_hash, role, full_name, email, department) VALUES (%s,%s,%s,%s,%s,%s)",
                    (user.username, user.passwordHash, user.role, user.fullName, user.email, user.department))
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()
    return user

@app.put("/users/{username}/password", tags=["Users"])
def change_password(username: str, req: ChangePasswordRequest, token_data: dict = Depends(verify_token)):
    if token_data.get("sub") != username: raise HTTPException(status_code=403, detail="Yasak")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("SELECT password_hash FROM users WHERE username = %s", (username,))
        row = cur.fetchone()
        if not row or row["password_hash"] != req.currentPasswordHash:
            raise HTTPException(status_code=400, detail="Mevcut şifre hatalı")
        cur.execute("UPDATE users SET password_hash = %s, must_change_password = FALSE WHERE username = %s", (req.newPasswordHash, username))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"message": "Başarılı"}

# --- CLASSROOMS & COURSES ---

@app.get("/classrooms", response_model=List[ClassroomDto], tags=["Classrooms"])
def get_classrooms(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT id, room_code, capacity FROM classrooms")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [ClassroomDto(id=r["id"], roomCode=r["room_code"], capacity=r["capacity"]) for r in rows]

@app.post("/classrooms", response_model=ClassroomDto, tags=["Classrooms"])
def add_classroom(classroom: ClassroomDto, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("INSERT INTO classrooms (id, room_code, capacity) VALUES (%s,%s,%s)", (classroom.id, classroom.roomCode, classroom.capacity))
    conn.commit()
    cur.close()
    conn.close()
    return classroom

@app.get("/courses", response_model=List[CourseDto], tags=["Courses"])
def get_courses(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT code, name, lecturer_username, department, email, duration, classroom_id FROM courses")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [CourseDto(code=r["code"], name=r["name"], lecturerUsername=r["lecturer_username"], department=r["department"], email=r["email"], duration=r["duration"], classroomId=r["classroom_id"]) for r in rows]

@app.post("/courses/bulk", tags=["Courses"])
def import_courses(courses: List[CourseDto], token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    for c in courses:
        cur.execute("INSERT INTO courses (code, name, lecturer_username, department, email, duration, classroom_id) VALUES (%s,%s,%s,%s,%s,%s,%s) ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name, lecturer_username=EXCLUDED.lecturer_username, email=EXCLUDED.email",
                    (c.code, c.name, c.lecturerUsername, c.department, c.email, c.duration, c.classroomId))
    conn.commit()
    cur.close()
    conn.close()
    return {"status": "ok"}

# --- SCHEDULES & AVAILABILITY ---

@app.get("/schedules/{username}", response_model=ScheduleDto, tags=["Schedules"])
def get_schedule(username: str, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT slots FROM schedules WHERE instructor_username = %s", (username,))
    row = cur.fetchone()
    cur.close()
    conn.close()
    return ScheduleDto(instructorUsername=username, slots=row["slots"] if row else {})

@app.put("/schedules/{username}", tags=["Schedules"])
def update_schedule(username: str, schedule: ScheduleDto, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("INSERT INTO schedules (instructor_username, slots) VALUES (%s,%s) ON CONFLICT (instructor_username) DO UPDATE SET slots=EXCLUDED.slots",
                    (username, json.dumps({k: v.dict() if v else None for k, v in schedule.slots.items()})))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"status": "ok"}

@app.get("/availabilities/{username}", response_model=AvailabilityDto, tags=["Availabilities"])
def get_avail(username: str, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT slots FROM availabilities WHERE instructor_username = %s", (username,))
    row = cur.fetchone()
    cur.close()
    conn.close()
    return AvailabilityDto(instructorUsername=username, slots=row["slots"] if row else {})

@app.put("/availabilities/{username}", tags=["Availabilities"])
def update_avail(username: str, avail: AvailabilityDto, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("INSERT INTO availabilities (instructor_username, slots) VALUES (%s,%s) ON CONFLICT (instructor_username) DO UPDATE SET slots=EXCLUDED.slots",
                (username, json.dumps(avail.slots)))
    conn.commit()
    cur.close()
    conn.close()
    return {"status": "ok"}

# --- MESSAGES & NOTIFICATIONS ---

@app.get("/messages", response_model=List[MessageDto], tags=["Messages"])
def get_msgs(with_user: str, token_data: dict = Depends(verify_token)):
    current_user = token_data.get("sub")
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("""SELECT id, sender_username, recipient_username, content, is_read, EXTRACT(EPOCH FROM created_at)*1000 as ts
                   FROM messages WHERE (sender_username=%s AND recipient_username=%s) OR (sender_username=%s AND recipient_username=%s) ORDER BY created_at ASC""",
                (current_user, with_user, with_user, current_user))
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [MessageDto(id=r["id"], senderUsername=r["sender_username"], recipientUsername=r["recipient_username"], content=r["content"], isRead=r["is_read"], createdAt=int(r["ts"])) for r in rows]

@app.post("/messages", response_model=MessageDto, tags=["Messages"])
def send_msg(msg: MessageDto, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("INSERT INTO messages (sender_username, recipient_username, content) VALUES (%s,%s,%s) RETURNING id, EXTRACT(EPOCH FROM created_at)*1000 as ts",
                (token_data.get("sub"), msg.recipientUsername, msg.content))
    row = cur.fetchone()
    conn.commit()
    cur.close()
    conn.close()
    msg.id = row["id"]
    msg.createdAt = int(row["ts"])
    return msg

@app.get("/notifications", response_model=List[NotificationDto], tags=["Notifications"])
def get_notifs(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT id, text, is_read FROM notifications WHERE recipient_username = %s", (token_data.get("sub"),))
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [NotificationDto(id=str(r["id"]), recipientUsername=token_data.get("sub"), text=r["text"], isRead=r["is_read"]) for r in rows]

@app.post("/notifications", tags=["Notifications"])
def send_notif(notif: NotificationDto, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("INSERT INTO notifications (recipient_username, text) VALUES (%s,%s)", (notif.recipientUsername, notif.text))
    conn.commit()
    cur.close()
    conn.close()
    return {"status": "ok"}

# --- HISTORY ---

@app.get("/history", response_model=List[ScheduleHistoryDto], tags=["History"])
def get_history(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT changed_by, instructor_username, instructor_full_name, day, time_slot, previous_course->>'code' as p_code, new_course->>'code' as n_code FROM schedule_history ORDER BY changed_at DESC LIMIT 50")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [ScheduleHistoryDto(changedBy=r["changed_by"], instructorUsername=r["instructor_username"], instructorFullName=r["instructor_full_name"], day=r["day"], timeSlot=r["time_slot"], previousCourseCode=r["p_code"], newCourseCode=r["n_code"]) for r in rows]

@app.get("/", tags=["Health"])
def root():
    return {"message": "OptiClass API Online!"}

























from fastapi import FastAPI, HTTPException, Depends, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import OAuth2PasswordBearer
from pydantic import BaseModel
from typing import List, Optional, Dict
from datetime import datetime, timedelta
from jose import JWTError, jwt
import psycopg2
import psycopg2.extras
import json
import base64

# --- AYARLAR VE VERİTABANI BAĞLANTISI ---
DB_URL = "postgresql://yusuf:123456@localhost/opticlass"
SECRET_KEY = "opticlass_cok_gizli_anahtar"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 10080  # 7 gün

app = FastAPI(title="OptiClass API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="auth/login", auto_error=False)

def get_db_connection():
    return psycopg2.connect(DB_URL, cursor_factory=psycopg2.extras.RealDictCursor)

def smart_decode(value: str) -> str:
    """Base64 kodlu gelen kullanıcı adlarını (YWRtaW4= gibi) çözer."""
    if not value: return value
    try:
        # Değerde '=' varsa veya Base64 karakterleri içeriyorsa çözmeyi dene
        if any(c in value for c in ['=', '+', '/']):
            decoded_bytes = base64.b64decode(value)
            return decoded_bytes.decode('utf-8')
    except:
        pass
    return value

def create_access_token(data: dict):
    to_encode = data.copy()
    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

def clean_token(raw: str) -> str:
    """Bearer Bearer Bearer... ne kadar gelirse gelsin temizle"""
    token = raw.strip()
    while token.lower().startswith("bearer "):
        token = token[7:].strip()
    return token

def verify_token(authorization: str = Depends(oauth2_scheme)):
    if not authorization:
        raise HTTPException(status_code=401, detail="Token bulunamadı")

    token = clean_token(authorization)

    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username: str = payload.get("sub")
        if username is None:
            raise HTTPException(status_code=401, detail="Geçersiz token")
        return payload
    except JWTError as e:
        raise HTTPException(status_code=401, detail=f"Yetkisiz erişim: {str(e)}")

# --- PYDANTIC MODELLERİ ---
class LoginRequest(BaseModel):
    username: str
    passwordHash: str

class LoginResponse(BaseModel):
    token: str
    role: str
    username: str
    mustChangePassword: bool

class UserDto(BaseModel):
    username: str
    role: str
    fullName: str
    email: Optional[str] = None
    department: Optional[str] = ""

class UserCreateDto(BaseModel):
    username: str
    passwordHash: str
    role: str
    fullName: str
    email: Optional[str] = None
    department: Optional[str] = ""

class UserUpdateDto(BaseModel):
    role: str
    fullName: str
    email: Optional[str] = None
    department: Optional[str] = ""

class ChangePasswordRequest(BaseModel):
    currentPasswordHash: str
    newPasswordHash: str

class ClassroomDto(BaseModel):
    id: str
    roomCode: str
    capacity: int

class CourseDto(BaseModel):
    code: str
    name: str
    lecturerUsername: Optional[str] = None
    department: str = ""
    email: str = ""
    duration: int = 1
    classroomId: Optional[str] = None

class AvailabilityDto(BaseModel):
    instructorUsername: str
    slots: Dict[str, List[str]]

class ScheduleDto(BaseModel):
    instructorUsername: str
    slots: Dict[str, Optional[CourseDto]]

class MessageDto(BaseModel):
    id: Optional[int] = None
    senderUsername: str
    recipientUsername: str
    content: str
    isRead: bool = False
    createdAt: Optional[int] = None

class NotificationDto(BaseModel):
    id: Optional[str] = None
    recipientUsername: str
    text: str
    isRead: bool = False

class ScheduleHistoryDto(BaseModel):
    changedBy: str
    instructorUsername: str
    instructorFullName: str
    day: str
    timeSlot: str
    previousCourseCode: Optional[str] = None
    newCourseCode: Optional[str] = None

# --- AUTH ENDPOINTS ---

@app.post("/auth/login", response_model=LoginResponse, tags=["Auth"])
def login(request: LoginRequest):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT username, password_hash, role, must_change_password FROM users WHERE username = %s", (request.username,))
    user = cur.fetchone()
    cur.close()
    conn.close()

    if not user or user["password_hash"] != request.passwordHash:
        raise HTTPException(status_code=401, detail="Kullanıcı adı veya şifre hatalı")

    token = create_access_token(data={"sub": user["username"], "role": user["role"]})
    return LoginResponse(
        token=token, role=user["role"], username=user["username"],
        mustChangePassword=user["must_change_password"]
    )

# --- USERS CRUD ---

@app.get("/users", response_model=List[UserDto], tags=["Users"])
def get_users(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT username, role, full_name, email, department FROM users")
    users = cur.fetchall()
    cur.close()
    conn.close()
    return [UserDto(username=u["username"], role=u["role"], fullName=u["full_name"], email=u["email"], department=u["department"]) for u in users]

@app.post("/users", response_model=UserDto, tags=["Users"])
def create_user(user: UserCreateDto, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN": raise HTTPException(status_code=403, detail="Yetkisiz")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("INSERT INTO users (username, password_hash, role, full_name, email, department) VALUES (%s,%s,%s,%s,%s,%s)",
                    (user.username, user.passwordHash, user.role, user.fullName, user.email, user.department))
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()
    return user

@app.put("/users/{username}/password", tags=["Users"])
def change_password(username: str, req: ChangePasswordRequest, token_data: dict = Depends(verify_token)):
    if token_data.get("sub") != username: raise HTTPException(status_code=403, detail="Yasak")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("SELECT password_hash FROM users WHERE username = %s", (username,))
        row = cur.fetchone()
        if not row or row["password_hash"] != req.currentPasswordHash:
            raise HTTPException(status_code=400, detail="Mevcut şifre hatalı")
        cur.execute("UPDATE users SET password_hash = %s, must_change_password = FALSE WHERE username = %s", (req.newPasswordHash, username))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"message": "Başarılı"}

@app.delete("/users/{username}", tags=["Users"])
def delete_user(username: str, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
        raise HTTPException(status_code=403, detail="Sadece admin silebilir")

    if username.lower() == "admin":
        raise HTTPException(status_code=400, detail="Admin silinemez")

    conn = get_db_connection()
    cur = conn.cursor()

    try:
        cur.execute("DELETE FROM users WHERE username = %s", (username,))
        deleted = cur.rowcount
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()

    if deleted == 0:
        raise HTTPException(status_code=404, detail="Kullanıcı bulunamadı")

    return {"message": "Kullanıcı silindi"}

# --- CLASSROOMS & COURSES ---

@app.get("/classrooms", response_model=List[ClassroomDto], tags=["Classrooms"])
def get_classrooms(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT id, room_code, capacity FROM classrooms")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [ClassroomDto(id=r["id"], roomCode=r["room_code"], capacity=r["capacity"]) for r in rows]

@app.post("/classrooms", response_model=ClassroomDto, tags=["Classrooms"])
def add_classroom(classroom: ClassroomDto, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("INSERT INTO classrooms (id, room_code, capacity) VALUES (%s,%s,%s)", (classroom.id, classroom.roomCode, classroom.capacity))
    conn.commit()
    cur.close()
    conn.close()
    return classroom

@app.get("/courses", response_model=List[CourseDto], tags=["Courses"])
def get_courses(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT code, name, lecturer_username, department, email, duration, classroom_id FROM courses")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [CourseDto(code=r["code"], name=r["name"], lecturerUsername=r["lecturer_username"], department=r["department"], email=r["email"], duration=r["duration"], classroomId=r["classroom_id"]) for r in rows]

@app.post("/courses/bulk", tags=["Courses"])
def import_courses(courses: List[CourseDto], token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    for c in courses:
        cur.execute("INSERT INTO courses (code, name, lecturer_username, department, email, duration, classroom_id) VALUES (%s,%s,%s,%s,%s,%s,%s) ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name, lecturer_username=EXCLUDED.lecturer_username, email=EXCLUDED.email",
                    (c.code, c.name, c.lecturerUsername, c.department, c.email, c.duration, c.classroomId))
    conn.commit()
    cur.close()
    conn.close()
    return {"status": "ok"}

@app.delete("/classrooms/{id}", tags=["Classrooms"])
def delete_classroom(id: str, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
        raise HTTPException(status_code=403, detail="Sadece admin silebilir")

    conn = get_db_connection()
    cur = conn.cursor()

    try:
        cur.execute("DELETE FROM classrooms WHERE id = %s", (id,))
        deleted = cur.rowcount
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()

    if deleted == 0:
        raise HTTPException(status_code=404, detail="Derslik bulunamadı")

    return {"message": "Derslik silindi"}

@app.delete("/courses/{code}", tags=["Courses"])
def delete_course(code: str, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
        raise HTTPException(status_code=403, detail="Sadece admin silebilir")

    conn = get_db_connection()
    cur = conn.cursor()

    try:
        cur.execute("DELETE FROM courses WHERE code = %s", (code,))
        deleted = cur.rowcount
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()

    if deleted == 0:
        raise HTTPException(status_code=404, detail="Ders bulunamadı")

    return {"message": "Ders silindi"}

# --- SCHEDULES & AVAILABILITY ---

@app.get("/schedules/{username}", response_model=ScheduleDto, tags=["Schedules"])
def get_schedule(username: str, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT slots FROM schedules WHERE instructor_username = %s", (username,))
    row = cur.fetchone()
    cur.close()
    conn.close()
    return ScheduleDto(instructorUsername=username, slots=row["slots"] if row else {})

@app.put("/schedules/{username}", tags=["Schedules"])
def update_schedule(username: str, schedule: ScheduleDto, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("INSERT INTO schedules (instructor_username, slots) VALUES (%s,%s) ON CONFLICT (instructor_username) DO UPDATE SET slots=EXCLUDED.slots",
                    (username, json.dumps({k: v.dict() if v else None for k, v in schedule.slots.items()})))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"status": "ok"}

@app.get("/availabilities/{username}", response_model=AvailabilityDto, tags=["Availabilities"])
def get_avail(username: str, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT slots FROM availabilities WHERE instructor_username = %s", (username,))
    row = cur.fetchone()
    cur.close()
    conn.close()
    return AvailabilityDto(instructorUsername=username, slots=row["slots"] if row else {})

@app.put("/availabilities/{username}", tags=["Availabilities"])
def update_avail(username: str, avail: AvailabilityDto, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("INSERT INTO availabilities (instructor_username, slots) VALUES (%s,%s) ON CONFLICT (instructor_username) DO UPDATE SET slots=EXCLUDED.slots",
                (username, json.dumps(avail.slots)))
    conn.commit()
    cur.close()
    conn.close()
    return {"status": "ok"}

@app.delete("/schedules/{username}", tags=["Schedules"])
def delete_schedule(username: str, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN" and token_data.get("sub") != username:
        raise HTTPException(status_code=403, detail="Yetkisiz")

    conn = get_db_connection()
    cur = conn.cursor()

    try:
        cur.execute("DELETE FROM schedules WHERE instructor_username = %s", (username,))
        conn.commit()
    finally:
        cur.close()
        conn.close()

    return {"message": "Program silindi"}

@app.delete("/availabilities/{username}", tags=["Availabilities"])
def delete_availability(username: str, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN" and token_data.get("sub") != username:
        raise HTTPException(status_code=403, detail="Yetkisiz")

    conn = get_db_connection()
    cur = conn.cursor()

    try:
        cur.execute("DELETE FROM availabilities WHERE instructor_username = %s", (username,))
        conn.commit()
    finally:
        cur.close()
        conn.close()

    return {"message": "Müsaitlik silindi"}

# --- MESSAGES & NOTIFICATIONS ---

@app.get("/messages", response_model=List[MessageDto], tags=["Messages"])
def get_msgs(with_user: str, token_data: dict = Depends(verify_token)):
    current_user = token_data.get("sub")
    
    # Android'den şifreli gelmiş olabileceği için çözüyoruz
    target_user = smart_decode(with_user)
    
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("""SELECT id, sender_username, recipient_username, content, is_read, EXTRACT(EPOCH FROM created_at)*1000 as ts
                   FROM messages 
                   WHERE (sender_username=%s AND recipient_username=%s) 
                      OR (sender_username=%s AND recipient_username=%s) 
                   ORDER BY created_at ASC""",
                (current_user, target_user, target_user, current_user))
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [MessageDto(id=r["id"], senderUsername=r["sender_username"], recipientUsername=r["recipient_username"], 
                       content=r["content"], isRead=r["is_read"], createdAt=int(r["ts"])) for r in rows]

@app.post("/messages", response_model=MessageDto, tags=["Messages"])
def send_msg(msg: MessageDto, token_data: dict = Depends(verify_token)):
    sender = token_data.get("sub")
    
    # ALICI adını çözüyoruz: YWRtaW4= -> admin
    recipient = smart_decode(msg.recipientUsername)
    
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("""INSERT INTO messages (sender_username, recipient_username, content) 
                       VALUES (%s,%s,%s) 
                       RETURNING id, EXTRACT(EPOCH FROM created_at)*1000 as ts""",
                    (sender, recipient, msg.content))
        row = cur.fetchone()
        conn.commit()
        
        msg.id = row["id"]
        msg.createdAt = int(row["ts"])
        msg.recipientUsername = recipient # DB ile uyumlu haliyle geri döndür
        return msg
    except psycopg2.errors.ForeignKeyViolation:
        conn.rollback()
        raise HTTPException(status_code=404, detail=f"Alıcı kullanıcı ({recipient}) veritabanında yok!")
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()

@app.get("/notifications", response_model=List[NotificationDto], tags=["Notifications"])
def get_notifs(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT id, text, is_read FROM notifications WHERE recipient_username = %s", (token_data.get("sub"),))
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [NotificationDto(id=str(r["id"]), recipientUsername=token_data.get("sub"), text=r["text"], isRead=r["is_read"]) for r in rows]

@app.post("/notifications", tags=["Notifications"])
def send_notif(notif: NotificationDto, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("INSERT INTO notifications (recipient_username, text) VALUES (%s,%s)", (notif.recipientUsername, notif.text))
    conn.commit()
    cur.close()
    conn.close()
    return {"status": "ok"}

# --- HISTORY ---

@app.get("/history", response_model=List[ScheduleHistoryDto], tags=["History"])
def get_history(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT changed_by, instructor_username, instructor_full_name, day, time_slot, previous_course->>'code' as p_code, new_course->>'code' as n_code FROM schedule_history ORDER BY changed_at DESC LIMIT 50")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [ScheduleHistoryDto(changedBy=r["changed_by"], instructorUsername=r["instructor_username"], instructorFullName=r["instructor_full_name"], day=r["day"], timeSlot=r["time_slot"], previousCourseCode=r["p_code"], newCourseCode=r["n_code"]) for r in rows]

@app.get("/", tags=["Health"])
def root():
    return {"message": "OptiClass API Online!"}