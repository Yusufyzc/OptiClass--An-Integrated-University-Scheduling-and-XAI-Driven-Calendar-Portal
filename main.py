from fastapi import FastAPI, HTTPException, Depends, status, Request, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import OAuth2PasswordBearer
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse, StreamingResponse
from pydantic import BaseModel
from typing import List, Optional, Dict
from datetime import datetime, timedelta, timezone
from jose import JWTError, jwt
import psycopg2
import psycopg2.extras
import json
import base64
import hashlib
import os
import csv
import threading
import asyncio
import requests as http_requests

# --- CONFIGURATION AND DATABASE CONNECTION ---
DB_URL = "postgresql://yusuf:123456@localhost/opticlass"
SECRET_KEY = "opticlass_cok_gizli_anahtar"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 10080  # 7 days

# --- AUDIT LOG (CSV) ---
_LOG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "audit_logs.csv")
_LOG_FIELDS = ["id","timestamp","actor_username","actor_role","action","target","details","ip_address","user_agent"]
_log_lock = threading.Lock()
_log_id_counter = 0
_log_listeners: set = set()
_event_loop: asyncio.AbstractEventLoop = None

def _init_log_file():
    global _log_id_counter
    if not os.path.exists(_LOG_FILE):
        with open(_LOG_FILE, "w", newline="", encoding="utf-8") as f:
            csv.DictWriter(f, fieldnames=_LOG_FIELDS).writeheader()
        _log_id_counter = 0
    else:
        with open(_LOG_FILE, "r", encoding="utf-8") as f:
            _log_id_counter = sum(1 for _ in csv.DictReader(f))

app = FastAPI(title="OptiClass API", version="1.0.0")

_WEB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web")
if os.path.isdir(_WEB_DIR):
    app.mount("/static", StaticFiles(directory=_WEB_DIR), name="web_static")

@app.on_event("startup")
def create_tables():
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("""CREATE TABLE IF NOT EXISTS schedule_history (
        id SERIAL PRIMARY KEY,
        changed_by VARCHAR NOT NULL,
        instructor_username VARCHAR NOT NULL,
        instructor_full_name VARCHAR NOT NULL,
        day VARCHAR NOT NULL,
        time_slot VARCHAR NOT NULL,
        previous_course JSONB,
        new_course JSONB,
        changed_at TIMESTAMP DEFAULT NOW()
    )""")
    cur.execute("""CREATE TABLE IF NOT EXISTS app_settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
    )""")
    cur.execute("""INSERT INTO app_settings (key, value) VALUES ('scheduling_phase', 'PHASE_1')
                   ON CONFLICT (key) DO NOTHING""")
    cur.execute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS lecture_assigned BOOLEAN DEFAULT FALSE")
    cur.execute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS lab_assigned BOOLEAN DEFAULT NULL")
    conn.commit()
    cur.close()
    conn.close()
    _init_log_file()

@app.on_event("startup")
async def capture_event_loop():
    global _event_loop
    _event_loop = asyncio.get_running_loop()

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

def is_admin(token_data: dict) -> bool:
    return token_data.get("role") in ("ADMIN", "SUPER_ADMIN")

def get_client_ip(request: Request) -> str:
    return (
        request.headers.get("X-Real-IP") or
        request.headers.get("X-Forwarded-For", "").split(",")[0].strip() or
        (request.client.host if request.client else "")
    )[:50]

def get_user_agent(request: Request) -> str:
    return request.headers.get("User-Agent", "")[:500]

def log_action(actor_username: str, actor_role: str, action: str,
               target: str = "", details: str = "", ip: str = "", user_agent: str = ""):
    global _log_id_counter
    try:
        now = datetime.now(timezone.utc)
        with _log_lock:
            _log_id_counter += 1
            row = {
                "id": _log_id_counter,
                "timestamp": now.isoformat(),
                "actor_username": (actor_username or "")[:100],
                "actor_role": (actor_role or "")[:50],
                "action": (action or "")[:100],
                "target": (target or "")[:200],
                "details": (details or "")[:500],
                "ip_address": (ip or "")[:50],
                "user_agent": (user_agent or "")[:500],
            }
            with open(_LOG_FILE, "a", newline="", encoding="utf-8") as f:
                csv.DictWriter(f, fieldnames=_LOG_FIELDS).writerow(row)
        # Push to SSE listeners (thread-safe)
        if _event_loop and _log_listeners:
            data = json.dumps(row)
            for q in list(_log_listeners):
                try:
                    _event_loop.call_soon_threadsafe(q.put_nowait, data)
                except Exception:
                    pass
    except Exception:
        pass  # Never let logging break the API

def smart_decode(value: str) -> str:
    if not value: return value
    try:
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
    token = raw.strip()
    while token.lower().startswith("bearer "):
        token = token[7:].strip()
    return token

def verify_token(authorization: str = Depends(oauth2_scheme)):
    if not authorization:
        raise HTTPException(status_code=401, detail="Token not found")
    token = clean_token(authorization)
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username: str = payload.get("sub")
        if username is None:
            raise HTTPException(status_code=401, detail="Invalid token")
        return payload
    except JWTError as e:
        raise HTTPException(status_code=401, detail=f"Unauthorized: {str(e)}")

# --- PYDANTIC MODELS ---
class LoginRequest(BaseModel):
    username: str
    passwordHash: str

class LoginResponse(BaseModel):
    token: str
    role: str
    username: str
    mustChangePassword: bool
    department: str = ""

class UserDto(BaseModel):
    username: str
    role: str
    fullName: str
    email: Optional[str] = None
    department: Optional[str] = ""
    avatarUrl: Optional[str] = None

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

class AdminResetPasswordRequest(BaseModel):
    newPasswordHash: str

class AvatarUpdateDto(BaseModel):
    avatarUrl: str

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
    semester: int = 1
    studentCount: int = 0
    priority: int = 1
    lectureHours: int = 0
    labHours: int = 0
    lecture_assigned: bool = False
    lab_assigned: Optional[bool] = None

class SchedulingPhaseDto(BaseModel):
    phase: str

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
    changedAt: Optional[int] = None

class AuditLogDto(BaseModel):
    id: int
    timestamp: Optional[str] = None
    actor_username: str = ""
    actor_role: str = ""
    action: str = ""
    target: str = ""
    details: str = ""
    ip_address: str = ""
    user_agent: str = ""

# --- AUTH ENDPOINTS ---

@app.post("/auth/login", response_model=LoginResponse, tags=["Auth"])
def login(request: LoginRequest, req: Request = None):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT username, password_hash, role, must_change_password, department FROM users WHERE username = %s", (request.username,))
    user = cur.fetchone()
    cur.close()
    conn.close()
    ip = get_client_ip(req) if req else ""
    ua = get_user_agent(req) if req else ""
    if not user or user["password_hash"] != request.passwordHash:
        log_action(request.username, "UNKNOWN", "LOGIN_FAILED", request.username,
                   "Invalid credentials", ip, ua)
        raise HTTPException(status_code=401, detail="Invalid credentials")
    token = create_access_token(data={"sub": user["username"], "role": user["role"], "dept": user.get("department", "")})
    log_action(user["username"], user["role"], "LOGIN", user["username"], "", ip, ua)
    return LoginResponse(
        token=token, role=user["role"], username=user["username"],
        mustChangePassword=user["must_change_password"],
        department=user.get("department") or ""
    )

# --- USER CRUD OPERATIONS ---

@app.get("/users", response_model=List[UserDto], tags=["Users"])
def get_users(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT username, role, full_name, email, department, avatar_url FROM users")
    users = cur.fetchall()
    cur.close()
    conn.close()
    return [UserDto(username=u["username"], role=u["role"], fullName=u["full_name"],
                    email=u["email"], department=u["department"], avatarUrl=u["avatar_url"]) for u in users]

@app.post("/users", response_model=UserDto, tags=["Users"])
def create_user(user: UserCreateDto, req: Request, token_data: dict = Depends(verify_token)):
    if not is_admin(token_data):
        raise HTTPException(status_code=403, detail="Unauthorized")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("""INSERT INTO users (username, password_hash, role, full_name, email, department, must_change_password)
                       VALUES (%s,%s,%s,%s,%s,%s,%s)""",
                    (user.username, user.passwordHash, user.role, user.fullName,
                     user.email, user.department, True))
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()
    log_action(token_data["sub"], token_data["role"], "CREATE_USER", user.username,
               f"role={user.role} dept={user.department}", get_client_ip(req), get_user_agent(req))
    return UserDto(username=user.username, role=user.role, fullName=user.fullName,
                   email=user.email, department=user.department)

@app.put("/users/{username}", response_model=UserDto, tags=["Users"])
def update_user(username: str, user: UserUpdateDto, req: Request, token_data: dict = Depends(verify_token)):
    if not is_admin(token_data):
        raise HTTPException(status_code=403, detail="Only admins can update users")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("""UPDATE users SET role=%s, full_name=%s, email=%s, department=%s
                       WHERE username=%s""",
                    (user.role, user.fullName, user.email or "", user.department or "", username))
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="User not found")
        conn.commit()
    except HTTPException:
        raise
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()
    log_action(token_data["sub"], token_data["role"], "UPDATE_USER", username,
               f"role={user.role} dept={user.department}", get_client_ip(req), get_user_agent(req))
    return UserDto(username=username, role=user.role, fullName=user.fullName,
                   email=user.email, department=user.department)

@app.put("/users/{username}/password", tags=["Users"])
def change_password(username: str, req: ChangePasswordRequest,
                   token_data: dict = Depends(verify_token)):
    if token_data.get("sub") != username:
        raise HTTPException(status_code=403, detail="Forbidden")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("SELECT password_hash FROM users WHERE username = %s", (username,))
        row = cur.fetchone()
        if not row or row["password_hash"] != req.currentPasswordHash:
            raise HTTPException(status_code=400, detail="Incorrect password")
        cur.execute("""UPDATE users SET password_hash = %s, must_change_password = FALSE
                       WHERE username = %s""", (req.newPasswordHash, username))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"message": "Success"}

@app.put("/users/{username}/reset-password", tags=["Users"])
def admin_reset_password(username: str, req: AdminResetPasswordRequest,
                         http_req: Request = None,
                         token_data: dict = Depends(verify_token)):
    if not is_admin(token_data):
        raise HTTPException(status_code=403, detail="Only admins can reset passwords")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("""UPDATE users SET password_hash=%s, must_change_password=TRUE
                       WHERE username=%s""", (req.newPasswordHash, username))
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="User not found")
        conn.commit()
    finally:
        cur.close()
        conn.close()
    log_action(token_data["sub"], token_data["role"], "RESET_PASSWORD", username, "",
               get_client_ip(http_req) if http_req else "", get_user_agent(http_req) if http_req else "")
    return {"message": "Password reset"}

@app.put("/users/{username}/avatar", tags=["Users"])
def update_avatar(username: str, body: AvatarUpdateDto, token_data: dict = Depends(verify_token)):
    if token_data.get("sub") != username and token_data.get("role") != "ADMIN":
        raise HTTPException(status_code=403, detail="Forbidden")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("UPDATE users SET avatar_url=%s WHERE username=%s", (body.avatarUrl, username))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"status": "ok"}

@app.delete("/users/{username}", tags=["Users"])
def delete_user(username: str, req: Request, token_data: dict = Depends(verify_token)):
    if not is_admin(token_data):
        raise HTTPException(status_code=403, detail="Only admins can delete")
    if username.lower() == "admin":
        raise HTTPException(status_code=400, detail="Admin cannot be deleted")
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
        raise HTTPException(status_code=404, detail="User not found")
    log_action(token_data["sub"], token_data["role"], "DELETE_USER", username, "",
               get_client_ip(req), get_user_agent(req))
    return {"message": "User deleted"}

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
def add_classroom(classroom: ClassroomDto, req: Request, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("INSERT INTO classrooms (id, room_code, capacity) VALUES (%s,%s,%s)",
                    (classroom.id, classroom.roomCode, classroom.capacity))
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()
    log_action(token_data["sub"], token_data["role"], "ADD_CLASSROOM", classroom.roomCode,
               f"capacity={classroom.capacity}", get_client_ip(req), get_user_agent(req))
    return classroom

@app.delete("/classrooms/{id}", tags=["Classrooms"])
def delete_classroom(id: str, req: Request, token_data: dict = Depends(verify_token)):
    if not is_admin(token_data):
        raise HTTPException(status_code=403, detail="Only admins can delete")
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
        raise HTTPException(status_code=404, detail="Classroom not found")
    log_action(token_data["sub"], token_data["role"], "DELETE_CLASSROOM", id, "",
               get_client_ip(req), get_user_agent(req))
    return {"message": "Classroom deleted"}

@app.get("/courses", response_model=List[CourseDto], tags=["Courses"])
def get_courses(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT code, name, lecturer_username, department, email, duration, classroom_id, semester, student_count, priority, lecture_hours, lab_hours, lecture_assigned, lab_assigned FROM courses")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [CourseDto(code=r["code"], name=r["name"], lecturerUsername=r["lecturer_username"],
                     department=r["department"], email=r["email"], duration=r["duration"],
                     classroomId=r["classroom_id"], semester=r["semester"] or 1,
                     studentCount=r["student_count"] or 0, priority=r["priority"] or 1,
                     lectureHours=r["lecture_hours"] or 0, labHours=r["lab_hours"] or 0,
                     lecture_assigned=r["lecture_assigned"] or False,
                     lab_assigned=r["lab_assigned"]) for r in rows]

@app.post("/courses/bulk", tags=["Courses"])
def import_courses(courses: List[CourseDto], req: Request, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        for c in courses:
            initial_lab_assigned = None if (c.labHours == 0) else False
            cur.execute("""INSERT INTO courses (code, name, lecturer_username, department,
                           email, duration, classroom_id, semester, student_count, priority,
                           lecture_hours, lab_hours, lecture_assigned, lab_assigned)
                           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                           ON CONFLICT (code, department, email) DO UPDATE SET
                           name=EXCLUDED.name,
                           lecturer_username=EXCLUDED.lecturer_username,
                           email=EXCLUDED.email,
                           semester=EXCLUDED.semester,
                           student_count=EXCLUDED.student_count,
                           priority=EXCLUDED.priority,
                           lecture_hours=EXCLUDED.lecture_hours,
                           lab_hours=EXCLUDED.lab_hours""",
                        (c.code, c.name, c.lecturerUsername, c.department,
                         c.email, c.duration, c.classroomId, c.semester, c.studentCount, c.priority,
                         c.lectureHours, c.labHours, False, initial_lab_assigned))
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()
    log_action(token_data["sub"], token_data["role"], "IMPORT_COURSES", "",
               f"{len(courses)} courses imported", get_client_ip(req), get_user_agent(req))
    return {"status": "ok"}

@app.delete("/courses/{code}", tags=["Courses"])
def delete_course(code: str, req: Request, token_data: dict = Depends(verify_token)):
    if not is_admin(token_data):
        raise HTTPException(status_code=403, detail="Only admins can delete")
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
        raise HTTPException(status_code=404, detail="Course not found")
    log_action(token_data["sub"], token_data["role"], "DELETE_COURSE", code, "",
               get_client_ip(req), get_user_agent(req))
    return {"message": "Course deleted"}

# --- SCHEDULES & AVAILABILITY ---

@app.get("/schedules", response_model=List[ScheduleDto], tags=["Schedules"])
def get_all_schedules(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT instructor_username, slots FROM schedules")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [ScheduleDto(instructorUsername=r["instructor_username"], slots=r["slots"] or {}) for r in rows]

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
def update_schedule(username: str, schedule: ScheduleDto,
                   req: Request = None,
                   token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("""INSERT INTO schedules (instructor_username, slots)
                       VALUES (%s,%s)
                       ON CONFLICT (instructor_username)
                       DO UPDATE SET slots=EXCLUDED.slots""",
                    (username, json.dumps({k: v.dict() if v else None
                                          for k, v in schedule.slots.items()})))

        # Reset all assignment flags for this instructor's courses
        cur.execute("""UPDATE courses SET
                       lecture_assigned = FALSE,
                       lab_assigned = CASE WHEN lab_hours > 0 THEN FALSE ELSE NULL END
                       WHERE lecturer_username = %s""", (username,))

        # Count non-continuation appearances per course in new schedule
        course_counts = {}
        for slot_data in schedule.slots.values():
            if slot_data and slot_data.duration != -1:
                key = (slot_data.code, slot_data.lecturerUsername or username)
                course_counts[key] = course_counts.get(key, 0) + 1

        # Update flags based on schedule
        for (code, lecturer_un), count in course_counts.items():
            cur.execute("SELECT lab_hours FROM courses WHERE code = %s AND lecturer_username = %s",
                        (code, lecturer_un))
            row = cur.fetchone()
            if row:
                lab_h = row["lab_hours"] or 0
                lecture_assigned = count >= 1
                lab_assigned = None if lab_h == 0 else (count >= 2)
                cur.execute("""UPDATE courses SET lecture_assigned = %s, lab_assigned = %s
                               WHERE code = %s AND lecturer_username = %s""",
                            (lecture_assigned, lab_assigned, code, lecturer_un))

        conn.commit()
    finally:
        cur.close()
        conn.close()
    log_action(token_data["sub"], token_data["role"], "SAVE_SCHEDULE", username,
               f"{len(schedule.slots)} slots saved",
               get_client_ip(req) if req else "", get_user_agent(req) if req else "")
    return {"status": "ok"}

@app.delete("/schedules/{username}", tags=["Schedules"])
def delete_schedule(username: str, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN" and token_data.get("sub") != username:
        raise HTTPException(status_code=403, detail="Unauthorized")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("DELETE FROM schedules WHERE instructor_username = %s", (username,))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"message": "Schedule deleted"}

@app.get("/availabilities", response_model=List[AvailabilityDto], tags=["Availabilities"])
def get_all_availabilities(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT instructor_username, slots FROM availabilities")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [AvailabilityDto(instructorUsername=r["instructor_username"], slots=r["slots"] or {}) for r in rows]

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
def update_avail(username: str, avail: AvailabilityDto,
                token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("""INSERT INTO availabilities (instructor_username, slots)
                       VALUES (%s,%s)
                       ON CONFLICT (instructor_username)
                       DO UPDATE SET slots=EXCLUDED.slots""",
                    (username, json.dumps(avail.slots)))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"status": "ok"}

@app.delete("/availabilities/{username}", tags=["Availabilities"])
def delete_availability(username: str, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN" and token_data.get("sub") != username:
        raise HTTPException(status_code=403, detail="Unauthorized")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("DELETE FROM availabilities WHERE instructor_username = %s", (username,))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"message": "Availability deleted"}

# --- MESSAGES & NOTIFICATIONS ---

@app.get("/messages", response_model=List[MessageDto], tags=["Messages"])
def get_msgs(token_data: dict = Depends(verify_token), with_user: Optional[str] = None):
    current_user = token_data.get("sub")
    conn = get_db_connection()
    cur = conn.cursor()
    if with_user:
        target_user = smart_decode(with_user)
        cur.execute("""SELECT id, sender_username, recipient_username, content, is_read,
                       EXTRACT(EPOCH FROM created_at)*1000 as ts
                       FROM messages
                       WHERE (sender_username=%s AND recipient_username=%s)
                          OR (sender_username=%s AND recipient_username=%s)
                       ORDER BY created_at ASC""",
                    (current_user, target_user, target_user, current_user))
    else:
        cur.execute("""SELECT id, sender_username, recipient_username, content, is_read,
                       EXTRACT(EPOCH FROM created_at)*1000 as ts
                       FROM messages
                       WHERE sender_username=%s OR recipient_username=%s
                       ORDER BY created_at ASC""",
                    (current_user, current_user))
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [MessageDto(id=r["id"], senderUsername=r["sender_username"],
                      recipientUsername=r["recipient_username"],
                      content=r["content"], isRead=r["is_read"],
                      createdAt=int(r["ts"])) for r in rows]

@app.post("/messages", response_model=MessageDto, tags=["Messages"])
def send_msg(msg: MessageDto, token_data: dict = Depends(verify_token)):
    sender = token_data.get("sub")
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
        msg.recipientUsername = recipient
        return msg
    except psycopg2.errors.ForeignKeyViolation:
        conn.rollback()
        raise HTTPException(status_code=404, detail=f"Recipient user ({recipient}) not found!")
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()

@app.patch("/messages/mark-read", tags=["Messages"])
def mark_messages_read(sender: str, token_data: dict = Depends(verify_token)):
    current_user = token_data.get("sub")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute(
            "UPDATE messages SET is_read = TRUE WHERE recipient_username = %s AND sender_username = %s",
            (current_user, sender)
        )
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"status": "ok"}

@app.get("/notifications", response_model=List[NotificationDto], tags=["Notifications"])
def get_notifs(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT id, text, is_read FROM notifications WHERE recipient_username = %s",
                (token_data.get("sub"),))
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [NotificationDto(id=str(r["id"]), recipientUsername=token_data.get("sub"),
                           text=r["text"], isRead=r["is_read"]) for r in rows]

@app.post("/notifications", tags=["Notifications"])
def send_notif(notif: NotificationDto, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        # Get instructor's department for admin matching
        cur.execute("SELECT department FROM users WHERE username = %s", (notif.recipientUsername,))
        row = cur.fetchone()
        instructor_dept = (row["department"] or "") if row else ""

        recipients = {notif.recipientUsername}

        # All SUPER_ADMIN users receive every notification
        cur.execute("SELECT username FROM users WHERE role = 'SUPER_ADMIN' AND username != %s",
                    (notif.recipientUsername,))
        for r in cur.fetchall():
            recipients.add(r["username"])

        # ADMIN users receive notification if their department matches (or they have no department)
        cur.execute("SELECT username, department FROM users WHERE role = 'ADMIN' AND username != %s",
                    (notif.recipientUsername,))
        for r in cur.fetchall():
            admin_dept = r["department"] or ""
            if not admin_dept or admin_dept == instructor_dept:
                recipients.add(r["username"])

        for recipient in recipients:
            cur.execute("INSERT INTO notifications (recipient_username, text) VALUES (%s,%s)",
                        (recipient, notif.text))

        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"status": "ok"}

@app.patch("/notifications/{notification_id}/read", tags=["Notifications"])
def mark_notification_read(notification_id: int, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("UPDATE notifications SET is_read = TRUE WHERE id = %s AND recipient_username = %s",
                    (notification_id, token_data.get("sub")))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    return {"status": "ok"}

# --- HISTORY ---

@app.get("/history", response_model=List[ScheduleHistoryDto], tags=["History"])
def get_history(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("""SELECT changed_by, instructor_username, instructor_full_name, day,
                   time_slot, previous_course->>'code' as p_code,
                   new_course->>'code' as n_code,
                   EXTRACT(EPOCH FROM changed_at)*1000 as changed_at_ms
                   FROM schedule_history
                   ORDER BY changed_at DESC LIMIT 50""")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [ScheduleHistoryDto(changedBy=r["changed_by"],
                              instructorUsername=r["instructor_username"],
                              instructorFullName=r["instructor_full_name"],
                              day=r["day"], timeSlot=r["time_slot"],
                              previousCourseCode=r["p_code"],
                              newCourseCode=r["n_code"],
                              changedAt=int(r["changed_at_ms"]) if r["changed_at_ms"] else None) for r in rows]

@app.post("/history", tags=["History"])
def add_history(entry: ScheduleHistoryDto, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
        raise HTTPException(status_code=403, detail="Only admins can add history")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        prev_json = json.dumps({"code": entry.previousCourseCode}) if entry.previousCourseCode else None
        new_json = json.dumps({"code": entry.newCourseCode}) if entry.newCourseCode else None
        cur.execute("""INSERT INTO schedule_history
                       (changed_by, instructor_username, instructor_full_name, day, time_slot, previous_course, new_course)
                       VALUES (%s,%s,%s,%s,%s,%s::jsonb,%s::jsonb)""",
                    (entry.changedBy, entry.instructorUsername, entry.instructorFullName,
                     entry.day, entry.timeSlot, prev_json, new_json))
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()
    return {"status": "ok"}

# --- SETTINGS ---

@app.get("/settings/scheduling_phase", tags=["Settings"])
def get_scheduling_phase(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT value FROM app_settings WHERE key = 'scheduling_phase'")
    row = cur.fetchone()
    cur.close()
    conn.close()
    return {"phase": row["value"] if row else "PHASE_1"}

@app.get("/settings/phase_priorities", tags=["Settings"])
def get_phase_priorities(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT DISTINCT priority FROM courses ORDER BY priority DESC")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return {"phasePriorities": [r["priority"] for r in rows]}

@app.put("/settings/scheduling_phase", tags=["Settings"])
def set_scheduling_phase(body: SchedulingPhaseDto, req: Request,
                         token_data: dict = Depends(verify_token)):
    if not is_admin(token_data):
        raise HTTPException(status_code=403, detail="Only admins can change scheduling phase")
    import re
    if not re.match(r'^PHASE_\d+$', body.phase):
        raise HTTPException(status_code=400, detail="Invalid phase format")
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("""INSERT INTO app_settings (key, value) VALUES ('scheduling_phase', %s)
                       ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value""",
                    (body.phase,))
        conn.commit()
    finally:
        cur.close()
        conn.close()
    log_action(token_data["sub"], token_data["role"], "SET_PHASE", body.phase, "",
               get_client_ip(req), get_user_agent(req))
    return {"phase": body.phase}

@app.get("/common_course_slots", tags=["Schedules"])
def get_common_course_slots(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()

    cur.execute("SELECT value FROM app_settings WHERE key = 'scheduling_phase'")
    phase_row = cur.fetchone()
    phase_str = phase_row["value"] if phase_row else "PHASE_1"
    try:
        phase_num = int(phase_str.replace("PHASE_", ""))
    except Exception:
        phase_num = 1

    cur.execute("SELECT DISTINCT priority FROM courses ORDER BY priority DESC")
    priority_rows = cur.fetchall()
    all_priorities = [r["priority"] for r in priority_rows]
    prev_priorities = set(all_priorities[:phase_num - 1])

    if not prev_priorities:
        cur.close()
        conn.close()
        return {}

    cur.execute("SELECT slots FROM schedules")
    rows = cur.fetchall()
    cur.close()
    conn.close()

    result: dict = {}
    for row in rows:
        slots = row["slots"] or {}
        for key, course in slots.items():
            if not course:
                continue
            if course.get("priority") in prev_priorities:
                underscore_idx = key.index("_")
                day = key[:underscore_idx]
                slot = key[underscore_idx + 1:]
                if day not in result:
                    result[day] = []
                if slot not in result[day]:
                    result[day].append(slot)
    return result

# --- CHATBOT ---

RASA_URL = "http://localhost:5005"

class ChatBotRequest(BaseModel):
    message: str

@app.post("/chatbot", tags=["ChatBot"])
def chatbot(body: ChatBotRequest, token_data: dict = Depends(verify_token)):
    sender = token_data.get("sub", "user")
    try:
        resp = http_requests.post(
            f"{RASA_URL}/webhooks/rest/webhook",
            json={"sender": sender, "message": body.message},
            timeout=8
        )
        messages = resp.json()
        if messages and len(messages) > 0:
            return {"response": messages[0].get("text", "I didn't understand that.")}
        return {"response": "I didn't understand that. Could you rephrase?"}
    except Exception:
        return {"response": "The assistant is currently unavailable. Please try again later."}

# --- XAI SCHEDULE SUGGESTION (Decision Tree) ---

XAI_SCHEDULE_DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri"]
XAI_SCHEDULE_SLOTS = ["08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM",
                      "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM"]

class XAISuggestionRequest(BaseModel):
    courseCode: str
    duration: int = 1
    lectureHours: int = 0
    labHours: int = 0
    suggestionType: str = "lecture"
    classroomId: Optional[str] = None

class DTNodeResult(BaseModel):
    node: str
    label: str
    result: str   # "pass" | "fail_hard" | "partial" | "info" | "fail"
    description: str
    isHard: bool
    weight: float = 0.0
    scoreContribution: float = 0.0

class XAISlotSuggestion(BaseModel):
    day: str
    timeSlot: str
    classroomId: Optional[str] = None
    classroomCode: Optional[str] = None
    score: float
    path: List[DTNodeResult]
    summary: str

class XAISuggestionResponse(BaseModel):
    suggestions: List[XAISlotSuggestion]
    courseCode: str
    courseName: str
    algorithmNote: str
    suggestionType: str = "lecture"


def dt_evaluate_slot(day, main_slot, consec, classroom,
                     available_keys, conflict_map, classroom_booked,
                     course_dept, course_semester, student_count,
                     effective_duration, day_load):
    path = []
    consec_keys = {f"{day}_{s}" for s in consec}
    main_key = f"{day}_{main_slot}"
    room_id = classroom["id"]
    room_code = classroom["room_code"]
    is_online = room_code.upper() == "ONLINE"
    is_common = course_dept == "COMMON"
    score = 1.0

    # Node 1: Instructor Availability (HARD)
    avail = main_key in available_keys
    path.append(DTNodeResult(
        node="instructor_available",
        label="Instructor Availability",
        result="pass" if avail else "fail_hard",
        description=f"Instructor {'is available' if avail else 'is not available'} on {day} at {main_slot}",
        isHard=True
    ))
    if not avail:
        return 0.0, path, True

    # Node 2: Semester / Common Course Conflict at same slot (HARD)
    conflict = any(
        c["semester"] == course_semester and (
            is_common or c["dept"] == "COMMON" or c["dept"] == course_dept
        )
        for s in consec for c in conflict_map.get(f"{day}_{s}", [])
    )
    if is_common:
        path.append(DTNodeResult(
            node="common_course_check",
            label="Common Course — All Departments",
            result="fail_hard" if conflict else "pass",
            description=(
                f"Conflict: a semester {course_semester} course is already scheduled at this slot"
                if conflict else
                f"No semester {course_semester} conflicts across any department at this slot"
            ),
            isHard=True
        ))
    else:
        path.append(DTNodeResult(
            node="no_semester_conflict",
            label="Semester Conflict Check",
            result="fail_hard" if conflict else "pass",
            description=(
                f"Another {course_dept} dept semester-{course_semester} course at this exact slot"
                if conflict else
                f"No {course_dept} dept semester-{course_semester} conflicts at this slot"
            ),
            isHard=True
        ))
    if conflict:
        return 0.0, path, True

    # Node 3: Classroom Availability at same slot (HARD for non-ONLINE)
    room_free = all(room_id not in classroom_booked.get(f"{day}_{s}", set()) for s in consec)
    if is_online:
        path.append(DTNodeResult(
            node="no_classroom_conflict",
            label="Classroom Availability",
            result="info",
            description="Online room — multiple instructors can use it simultaneously",
            isHard=False
        ))
    else:
        path.append(DTNodeResult(
            node="no_classroom_conflict",
            label="Classroom Availability",
            result="pass" if room_free else "fail_hard",
            description=(
                f"{room_code} is free for all {effective_duration} hour(s)"
                if room_free else
                f"{room_code} is already booked at this time"
            ),
            isHard=True
        ))
        if not room_free:
            return 0.0, path, True

    # Node 4: ALL consecutive hours in availability (HARD)
    all_consec_avail = all(f"{day}_{s}" in available_keys for s in consec)
    path.append(DTNodeResult(
        node="slot_continuity",
        label="All Hours in Availability",
        result="pass" if all_consec_avail else "fail_hard",
        description=(
            f"All {effective_duration} consecutive hour(s) are within instructor availability"
            if all_consec_avail else
            f"Not all {effective_duration} hour(s) are within instructor availability — hard block"
        ),
        isHard=True
    ))
    if not all_consec_avail:
        return 0.0, path, True

    # --- SOFT PENALTY NODES (score starts at 1.0, penalties deducted) ---

    # Node 5: Classroom Capacity (HARD — eliminate if too small)
    fits = student_count == 0 or classroom["capacity"] >= student_count
    path.append(DTNodeResult(
        node="classroom_capacity",
        label="Classroom Capacity",
        result="pass" if fits else "fail_hard",
        description=(
            f"{room_code} (cap: {classroom['capacity']}) fits {student_count} students"
            if fits else
            f"{room_code} (cap: {classroom['capacity']}) too small for {student_count} — eliminated"
        ),
        isHard=True
    ))
    if not fits:
        return 0.0, path, True

    # Node 6: Same dept + same semester courses on same day other slots — penalty -4% each
    if not is_common:
        same_dept_count = sum(
            1 for slot_key, courses in conflict_map.items()
            if slot_key.startswith(f"{day}_") and slot_key not in consec_keys
            for c in courses if c["semester"] == course_semester and c["dept"] == course_dept
        )
        same_dept_penalty = round(same_dept_count * 0.04, 4)
        score -= same_dept_penalty
        path.append(DTNodeResult(
            node="same_dept_day_load",
            label="Same Dept. Day Load",
            result="pass" if same_dept_count == 0 else "partial",
            description=(
                f"No other {course_dept} semester-{course_semester} courses on {day}"
                if same_dept_count == 0 else
                f"{same_dept_count} other {course_dept} sem-{course_semester} course(s) on {day} — penalty -{same_dept_count * 4}%"
            ),
            isHard=False,
            weight=0.04,
            scoreContribution=round(-same_dept_penalty, 4)
        ))

    # Node 7: Different dept + same semester courses on same day — penalty -3% each
    if not is_common:
        diff_dept_count = sum(
            1 for slot_key, courses in conflict_map.items()
            if slot_key.startswith(f"{day}_") and slot_key not in consec_keys
            for c in courses
            if c["semester"] == course_semester and c["dept"] != course_dept and c["dept"] != ""
        )
        diff_dept_penalty = round(diff_dept_count * 0.03, 4)
        score -= diff_dept_penalty
        path.append(DTNodeResult(
            node="diff_dept_day_load",
            label="Other Dept. Day Load",
            result="pass" if diff_dept_count == 0 else "partial",
            description=(
                f"No other dept semester-{course_semester} courses on {day}"
                if diff_dept_count == 0 else
                f"{diff_dept_count} other dept sem-{course_semester} course(s) on {day} — penalty -{diff_dept_count * 3}%"
            ),
            isHard=False,
            weight=0.03,
            scoreContribution=round(-diff_dept_penalty, 4)
        ))

    # Node 8: Classroom usage on same day (other slots) — penalty -2% per use
    if not is_online:
        cls_day_uses = sum(
            1 for slot_key, rooms in classroom_booked.items()
            if slot_key.startswith(f"{day}_") and slot_key not in consec_keys and room_id in rooms
        )
        cls_penalty = round(cls_day_uses * 0.02, 4)
        score -= cls_penalty
        path.append(DTNodeResult(
            node="classroom_day_usage",
            label="Classroom Day Usage",
            result="pass" if cls_day_uses == 0 else "partial",
            description=(
                f"{room_code} is unused on {day} outside this slot"
                if cls_day_uses == 0 else
                f"{room_code} used {cls_day_uses} other time(s) on {day} — penalty -{cls_day_uses * 2}%"
            ),
            isHard=False,
            weight=0.02,
            scoreContribution=round(-cls_penalty, 4)
        ))

    # Node 9: Instructor day load — penalty -3% per existing course on same day
    instr_today = day_load.get(day, 0)
    instr_penalty = round(instr_today * 0.03, 4)
    score -= instr_penalty
    path.append(DTNodeResult(
        node="instructor_day_load",
        label="Instructor Day Load",
        result="pass" if instr_today == 0 else "partial",
        description=(
            f"No other courses for this instructor on {day}"
            if instr_today == 0 else
            f"Instructor has {instr_today} other course(s) on {day} — penalty -{instr_today * 3}%"
        ),
        isHard=False,
        weight=0.03,
        scoreContribution=round(-instr_penalty, 4)
    ))

    return max(0.0, round(score, 4)), path, False


def dt_summary(path, score):
    penalties = [n for n in path if n.scoreContribution < 0]
    penalty_total = abs(sum(n.scoreContribution for n in penalties))
    if score >= 0.85:
        return f"Excellent match ({score:.0%}): all hard constraints passed, minimal penalties."
    elif score >= 0.65:
        penalty_desc = f" Soft penalties: {', '.join(n.label for n in penalties[:2])}." if penalties else ""
        return f"Good match ({score:.0%}): all hard constraints passed.{penalty_desc}"
    elif score >= 0.40:
        return f"Moderate match ({score:.0%}). Total soft penalty: {penalty_total:.0%}."
    else:
        return f"Low match ({score:.0%}). High penalties: {penalty_total:.0%} total deduction."

@app.post("/schedule/suggest/{username}", tags=["XAI"])
def suggest_schedule(username: str, body: XAISuggestionRequest,
                     token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()

    cur.execute("""SELECT code, name, department, semester, student_count
                   FROM courses WHERE code = %s AND lecturer_username = %s""",
                (body.courseCode, username))
    course_row = cur.fetchone()
    if not course_row:
        cur.execute("SELECT code, name, department, semester, student_count FROM courses WHERE code = %s",
                    (body.courseCode,))
        course_row = cur.fetchone()
    if not course_row:
        cur.close(); conn.close()
        raise HTTPException(status_code=404, detail="Course not found")

    course_semester = course_row["semester"] or 1
    course_dept = course_row["department"] or ""
    student_count = course_row["student_count"] or 0

    cur.execute("SELECT slots FROM availabilities WHERE instructor_username = %s", (username,))
    avail_row = cur.fetchone()
    available_keys = set()
    if avail_row and avail_row["slots"]:
        for d, slots in avail_row["slots"].items():
            for s in slots:
                available_keys.add(f"{d}_{s}")

    cur.execute("SELECT slots FROM schedules WHERE instructor_username = %s", (username,))
    own_sched_row = cur.fetchone()
    own_schedule = own_sched_row["slots"] if own_sched_row and own_sched_row["slots"] else {}

    cur.execute("SELECT slots FROM schedules WHERE instructor_username != %s", (username,))
    other_schedules = cur.fetchall()

    cur.execute("SELECT id, room_code, capacity FROM classrooms")
    all_classrooms = cur.fetchall()
    if body.classroomId:
        eligible = [c for c in all_classrooms if c["id"] == body.classroomId]
        if not eligible:
            eligible = all_classrooms
    else:
        eligible = [c for c in all_classrooms if student_count == 0 or c["capacity"] >= student_count]
        if not eligible:
            eligible = all_classrooms

    cur.close(); conn.close()

    if body.suggestionType == "lab" and body.labHours > 0:
        effective_duration = body.labHours
    elif body.lectureHours > 0:
        effective_duration = body.lectureHours
    else:
        effective_duration = body.duration

    conflict_map = {}
    classroom_booked = {}
    for other in other_schedules:
        for key, sc in (other["slots"] or {}).items():
            if not sc:
                continue
            conflict_map.setdefault(key, []).append({
                "dept": sc.get("department", ""),
                "semester": sc.get("semester", 1)
            })
            if sc.get("classroomId"):
                classroom_booked.setdefault(key, set()).add(sc["classroomId"])

    day_load = {d: sum(1 for k, sc in own_schedule.items()
                       if k.startswith(d + "_") and sc and sc.get("duration", 1) != -1)
               for d in XAI_SCHEDULE_DAYS}

    suggestions = []
    for day in XAI_SCHEDULE_DAYS:
        for si in range(len(XAI_SCHEDULE_SLOTS)):
            if si + effective_duration > len(XAI_SCHEDULE_SLOTS):
                continue
            main_slot = XAI_SCHEDULE_SLOTS[si]
            consec = [XAI_SCHEDULE_SLOTS[si + k] for k in range(effective_duration)]
            if own_schedule.get(f"{day}_{main_slot}"):
                continue

            for classroom in eligible:
                score, path, eliminated = dt_evaluate_slot(
                    day, main_slot, consec, classroom,
                    available_keys, conflict_map, classroom_booked,
                    course_dept, course_semester, student_count,
                    effective_duration, day_load
                )
                if eliminated:
                    continue
                suggestions.append(XAISlotSuggestion(
                    day=day, timeSlot=main_slot,
                    classroomId=classroom["id"],
                    classroomCode=classroom["room_code"],
                    score=score,
                    path=path,
                    summary=dt_summary(path, score)
                ))

    suggestions.sort(key=lambda x: x.score, reverse=True)
    return XAISuggestionResponse(
        suggestions=suggestions,
        courseCode=body.courseCode,
        courseName=course_row["name"],
        algorithmNote="Score starts at 100%. Hard constraints eliminate slots: instructor availability, all consecutive hours in availability, same-slot semester/dept conflicts, classroom booking. Soft penalties: classroom too small -30%, same dept+semester same day -4% each, other dept+semester same day -3% each, classroom reused same day -2% each, instructor day load -3% each course.",
        suggestionType=body.suggestionType
    )

# --- XAI WEEKLY SCHEDULE SUGGESTION ---

class WeeklySlotDto(BaseModel):
    day: str
    timeSlot: str
    courseCode: str
    courseName: str
    lecturerUsername: str
    lecturerFullName: str
    classroomCode: str
    department: str
    semester: int
    priority: int
    duration: int = 1
    isLab: bool = False

class WeeklySuggestionDto(BaseModel):
    title: str
    description: str
    assignments: List[WeeklySlotDto]
    unassignedCourses: List[str]

class WeeklyScheduleRequest(BaseModel):
    phase: str

class WeeklyScheduleResponseDto(BaseModel):
    suggestions: List[WeeklySuggestionDto]
    phase: str
    algorithmNote: str

@app.post("/schedule/suggest-weekly", tags=["XAI"])
def suggest_weekly_schedule(body: WeeklyScheduleRequest, token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()

    # Distinct priorities descending
    cur.execute("SELECT DISTINCT priority FROM courses ORDER BY priority DESC")
    priorities = [row["priority"] for row in cur.fetchall()]

    try:
        phase_index = int(body.phase.split("_")[1]) - 1
    except Exception:
        phase_index = 0

    if not priorities or phase_index >= len(priorities):
        cur.close(); conn.close()
        return WeeklyScheduleResponseDto(suggestions=[], phase=body.phase,
                                         algorithmNote="No courses found for this phase.")

    target_priority = priorities[phase_index]

    # Courses for this phase
    cur.execute("""
        SELECT c.code, c.name, c.lecturer_username, c.department, c.semester,
               c.student_count, c.priority, c.lecture_hours, c.lab_hours,
               u.full_name AS lecturer_full_name
        FROM courses c
        LEFT JOIN users u ON c.lecturer_username = u.username
        WHERE c.priority = %s
    """, (target_priority,))
    phase_courses = [dict(r) for r in cur.fetchall()]

    if not phase_courses:
        cur.close(); conn.close()
        return WeeklyScheduleResponseDto(suggestions=[], phase=body.phase,
                                         algorithmNote="No courses found for this phase.")

    # Instructor availabilities
    cur.execute("SELECT instructor_username, slots FROM availabilities")
    avail_map = {}
    for row in cur.fetchall():
        keys = set()
        for day, slots in (row["slots"] or {}).items():
            for s in slots:
                keys.add(f"{day}_{s}")
        avail_map[row["instructor_username"]] = keys

    # All schedules — build conflict map from OTHER phases
    cur.execute("SELECT slots FROM schedules")
    global_conflict_map = {}   # {day_slot: [{dept, semester}]}
    global_classroom_map = {}  # {day_slot: set(room_ids)}
    for row in cur.fetchall():
        for key, sc in (row["slots"] or {}).items():
            if not sc:
                continue
            sc_priority = sc.get("priority", 1)
            if sc_priority != target_priority:
                global_conflict_map.setdefault(key, []).append({
                    "dept": sc.get("department", ""),
                    "semester": sc.get("semester", 1)
                })
                if sc.get("classroomId"):
                    global_classroom_map.setdefault(key, set()).add(sc["classroomId"])

    cur.execute("SELECT id, room_code, capacity FROM classrooms")
    all_classrooms = [dict(r) for r in cur.fetchall()]
    cur.close(); conn.close()

    def sort_by_student_count(courses):
        return sorted(courses, key=lambda x: (-(x["student_count"] or 0), -(x["lecture_hours"] or 1)))

    def sort_by_lecture_hours(courses):
        return sorted(courses, key=lambda x: (-(x["lecture_hours"] or 1), -(x["student_count"] or 0)))

    def sort_common_first(courses):
        return sorted(courses, key=lambda x: (
            0 if (x["department"] or "") == "COMMON" else 1,
            (x["department"] or ""),
            -(x["student_count"] or 0)
        ))

    def sort_common_spread(courses):
        """COMMON courses interleaved by semester for max day-spread, then non-COMMON by student count."""
        from collections import defaultdict
        common = [c for c in courses if (c["department"] or "") == "COMMON"]
        non_common = sorted(
            [c for c in courses if (c["department"] or "") != "COMMON"],
            key=lambda x: (-(x["student_count"] or 0),)
        )
        sem_groups = defaultdict(list)
        for c in sorted(common, key=lambda x: -(x["student_count"] or 0)):
            sem_groups[c["semester"] or 1].append(c)
        sems = sorted(sem_groups.keys())
        interleaved = []
        max_len = max((len(v) for v in sem_groups.values()), default=0)
        for i in range(max_len):
            for s in sems:
                if i < len(sem_groups[s]):
                    interleaved.append(sem_groups[s][i])
        return interleaved + non_common

    strategies = [
        ("Option 1: By Student Count",
         "Largest classes scheduled first to secure better time slots",
         sort_by_student_count),
        ("Option 2: By Lecture Duration",
         "Longer courses scheduled first to avoid slot fragmentation",
         sort_by_lecture_hours),
        ("Option 3: COMMON First",
         "COMMON/shared courses scheduled first, then alphabetically by department",
         sort_common_first),
    ]
    has_common = any((c["department"] or "") == "COMMON" for c in phase_courses)
    if has_common:
        strategies.append((
            "Option 4: COMMON Spread",
            "COMMON courses interleaved by semester to maximise day spread; all courses are force-placed if needed",
            sort_common_spread
        ))

    result_suggestions = []

    for title, description, sort_fn in strategies:
        ordered = sort_fn(list(phase_courses))

        instructor_occupied = {}  # {username: set(day_slot)}
        classroom_occupied = {}   # {day_slot: set(room_id)}
        # Semester/dept conflict tracking within this suggestion
        # (None, sem, key) = COMMON blocks all same-semester in that slot
        # (dept, sem, key) = dept-specific block
        assigned_slots = set()
        common_day_count = {d: 0 for d in XAI_SCHEDULE_DAYS}  # COMMON spread tracking

        assignments = []
        unassigned = []

        for course in ordered:
            code = course["code"]
            name = course["name"]
            lecturer = course["lecturer_username"] or ""
            dept = course["department"] or ""
            semester = course["semester"] or 1
            student_count = course["student_count"] or 0
            lec_hrs = course["lecture_hours"] or 0
            lecturer_name = course["lecturer_full_name"] or lecturer

            effective_duration = lec_hrs if lec_hrs > 0 else 1
            is_common = dept == "COMMON"

            instr_avail = avail_map.get(lecturer, set())
            instr_occ = instructor_occupied.get(lecturer, set())

            best = None
            # Prefer days with fewer slots taken; COMMON courses also prefer days with fewer COMMON placements
            instr_slots_set = instructor_occupied.get(lecturer, set())
            instr_load_fn = lambda d: sum(1 for k in instr_slots_set if k.startswith(f"{d}_"))
            if is_common:
                spread_days = sorted(
                    XAI_SCHEDULE_DAYS,
                    key=lambda d: (common_day_count.get(d, 0), instr_load_fn(d))
                )
            else:
                spread_days = sorted(XAI_SCHEDULE_DAYS, key=instr_load_fn)
            for day in spread_days:
                for si in range(len(XAI_SCHEDULE_SLOTS)):
                    if si + effective_duration > len(XAI_SCHEDULE_SLOTS):
                        continue
                    main_slot = XAI_SCHEDULE_SLOTS[si]
                    consec = [XAI_SCHEDULE_SLOTS[si + k] for k in range(effective_duration)]
                    main_key = f"{day}_{main_slot}"

                    if not all(f"{day}_{s}" in instr_avail for s in consec):
                        continue
                    if any(f"{day}_{s}" in instr_occ for s in consec):
                        continue

                    # Global semester conflict (from other phases)
                    sem_conflict = False
                    for s in consec:
                        k = f"{day}_{s}"
                        for ex in global_conflict_map.get(k, []):
                            if is_common and ex["dept"] == "COMMON":
                                # Two COMMON courses never share a slot
                                sem_conflict = True
                                break
                            if ex["semester"] == semester:
                                if is_common or ex["dept"] == "COMMON" or ex["dept"] == dept:
                                    sem_conflict = True
                                    break
                        if sem_conflict:
                            break

                    if sem_conflict:
                        continue

                    # Within-suggestion semester conflict
                    inner_conflict = False
                    for s in consec:
                        k = f"{day}_{s}"
                        if is_common:
                            # COMMON courses never overlap any same-semester course
                            # and never overlap another COMMON course (any semester)
                            if any(e[2] == k and (e[1] == semester or e[0] is None) for e in assigned_slots):
                                inner_conflict = True
                                break
                        else:
                            # Non-COMMON: conflict with COMMON same-semester OR same-dept same-semester
                            if (None, semester, k) in assigned_slots:
                                inner_conflict = True
                                break
                            if (dept, semester, k) in assigned_slots:
                                inner_conflict = True
                                break
                    if inner_conflict:
                        continue

                    # Find classroom
                    eligible = [c for c in all_classrooms
                                if student_count == 0 or c["capacity"] >= student_count]
                    if not eligible:
                        eligible = all_classrooms

                    chosen_room = None
                    for room in eligible:
                        room_id = room["id"]
                        is_online = room["room_code"].upper() == "ONLINE"
                        if is_online:
                            chosen_room = room
                            break
                        room_free = all(
                            room_id not in global_classroom_map.get(f"{day}_{s}", set()) and
                            room_id not in classroom_occupied.get(f"{day}_{s}", set())
                            for s in consec
                        )
                        if room_free:
                            chosen_room = room
                            break

                    if chosen_room is None:
                        continue

                    best = (day, main_slot, consec, chosen_room)
                    break
                if best:
                    break

            # Fallback: relax only classroom/spread optimizations
            # Hard constraints kept: instructor availability + no instructor double-booking + semester conflict
            if best is None:
                instr_slots_fb = instructor_occupied.get(lecturer, set())
                if is_common:
                    fb_days = sorted(XAI_SCHEDULE_DAYS,
                                     key=lambda d: (common_day_count.get(d, 0),
                                                    sum(1 for k in instr_slots_fb if k.startswith(f"{d}_"))))
                else:
                    fb_days = sorted(XAI_SCHEDULE_DAYS,
                                     key=lambda d: sum(1 for k in instr_slots_fb if k.startswith(f"{d}_")))
                for day in fb_days:
                    for si in range(len(XAI_SCHEDULE_SLOTS)):
                        if si + effective_duration > len(XAI_SCHEDULE_SLOTS):
                            continue
                        main_slot = XAI_SCHEDULE_SLOTS[si]
                        consec = [XAI_SCHEDULE_SLOTS[si + k] for k in range(effective_duration)]
                        if not all(f"{day}_{s}" in instr_avail for s in consec):
                            continue
                        if any(f"{day}_{s}" in instructor_occupied.get(lecturer, set()) for s in consec):
                            continue
                        # Semester conflict — hard, never relax (correctly scoped)
                        fb_sem_conflict = False
                        for s in consec:
                            slot_key = f"{day}_{s}"
                            for ex in global_conflict_map.get(slot_key, []):
                                if is_common and ex["dept"] == "COMMON":
                                    fb_sem_conflict = True; break
                                if ex["semester"] == semester:
                                    if is_common or ex["dept"] == "COMMON" or ex["dept"] == dept:
                                        fb_sem_conflict = True; break
                            if fb_sem_conflict:
                                break
                            if is_common:
                                if any(e[2] == slot_key and (e[1] == semester or e[0] is None) for e in assigned_slots):
                                    fb_sem_conflict = True; break
                            else:
                                if (None, semester, slot_key) in assigned_slots or (dept, semester, slot_key) in assigned_slots:
                                    fb_sem_conflict = True; break
                        if fb_sem_conflict:
                            continue
                        eligible = [c for c in all_classrooms
                                    if student_count == 0 or c["capacity"] >= student_count]
                        if not eligible:
                            eligible = all_classrooms
                        chosen_room = None
                        for room in eligible:
                            if room["room_code"].upper() == "ONLINE":
                                chosen_room = room
                                break
                            if all(
                                room["id"] not in global_classroom_map.get(f"{day}_{s}", set()) and
                                room["id"] not in classroom_occupied.get(f"{day}_{s}", set())
                                for s in consec
                            ):
                                chosen_room = room
                                break
                        if chosen_room is None:
                            continue
                        best = (day, main_slot, consec, chosen_room)
                        break
                    if best:
                        break

            # Last resort: relax classroom booking, keep only instructor avail + semester + capacity
            if best is None:
                instr_slots_lr = instructor_occupied.get(lecturer, set())
                lr_days = sorted(XAI_SCHEDULE_DAYS,
                                 key=lambda d: sum(1 for k in instr_slots_lr if k.startswith(f"{d}_")))
                for day in lr_days:
                    for si in range(len(XAI_SCHEDULE_SLOTS)):
                        if si + effective_duration > len(XAI_SCHEDULE_SLOTS):
                            continue
                        main_slot = XAI_SCHEDULE_SLOTS[si]
                        consec = [XAI_SCHEDULE_SLOTS[si + k] for k in range(effective_duration)]
                        if not all(f"{day}_{s}" in instr_avail for s in consec):
                            continue
                        if any(f"{day}_{s}" in instructor_occupied.get(lecturer, set()) for s in consec):
                            continue
                        lr_sem_conflict = False
                        for s in consec:
                            slot_key = f"{day}_{s}"
                            for ex in global_conflict_map.get(slot_key, []):
                                if is_common and ex["dept"] == "COMMON":
                                    lr_sem_conflict = True; break
                                if ex["semester"] == semester:
                                    if is_common or ex["dept"] == "COMMON" or ex["dept"] == dept:
                                        lr_sem_conflict = True; break
                            if lr_sem_conflict:
                                break
                            if is_common:
                                if any(e[2] == slot_key and (e[1] == semester or e[0] is None) for e in assigned_slots):
                                    lr_sem_conflict = True; break
                            else:
                                if (None, semester, slot_key) in assigned_slots or (dept, semester, slot_key) in assigned_slots:
                                    lr_sem_conflict = True; break
                        if lr_sem_conflict:
                            continue
                        eligible = [c for c in all_classrooms
                                    if student_count == 0 or c["capacity"] >= student_count]
                        if not eligible:
                            eligible = all_classrooms
                        chosen_room = eligible[0] if eligible else (all_classrooms[0] if all_classrooms else None)
                        if chosen_room is None:
                            continue
                        best = (day, main_slot, consec, chosen_room)
                        break
                    if best:
                        break

            if best:
                b_day, b_slot, b_consec, b_room = best
                if lecturer not in instructor_occupied:
                    instructor_occupied[lecturer] = set()
                for s in b_consec:
                    instructor_occupied[lecturer].add(f"{b_day}_{s}")

                if b_room["room_code"].upper() != "ONLINE":
                    for s in b_consec:
                        k = f"{b_day}_{s}"
                        classroom_occupied.setdefault(k, set()).add(b_room["id"])

                eff_dept = None if is_common else dept
                for s in b_consec:
                    k = f"{b_day}_{s}"
                    assigned_slots.add((eff_dept, semester, k))

                if is_common:
                    common_day_count[b_day] = common_day_count.get(b_day, 0) + 1

                assignments.append(WeeklySlotDto(
                    day=b_day, timeSlot=b_slot,
                    courseCode=code, courseName=name,
                    lecturerUsername=lecturer, lecturerFullName=lecturer_name,
                    classroomCode=b_room["room_code"],
                    department=dept, semester=semester, priority=target_priority,
                    duration=effective_duration, isLab=False
                ))

                # Lab scheduling (if lab_hrs > 0)
                lab_hrs = course["lab_hours"] or 0
                if lab_hrs > 0:
                    lab_best = None
                    # Prefer days with least instructor load; always try days other than lecture day first
                    updated_instr_slots = instructor_occupied.get(lecturer, set())
                    other_days = sorted(
                        [d for d in XAI_SCHEDULE_DAYS if d != b_day],
                        key=lambda d: sum(1 for k in updated_instr_slots if k.startswith(f"{d}_"))
                    )
                    for search_day in (other_days + [b_day]):
                        for si in range(len(XAI_SCHEDULE_SLOTS)):
                            if si + lab_hrs > len(XAI_SCHEDULE_SLOTS):
                                continue
                            l_slot = XAI_SCHEDULE_SLOTS[si]
                            l_consec = [XAI_SCHEDULE_SLOTS[si + k] for k in range(lab_hrs)]
                            if not all(f"{search_day}_{s}" in instr_avail for s in l_consec):
                                continue
                            if any(f"{search_day}_{s}" in instructor_occupied.get(lecturer, set()) for s in l_consec):
                                continue
                            # Semester conflict for lab (hard, correctly scoped)
                            lab_sem_conflict = False
                            for s in l_consec:
                                slot_key = f"{search_day}_{s}"
                                for ex in global_conflict_map.get(slot_key, []):
                                    if is_common and ex["dept"] == "COMMON":
                                        lab_sem_conflict = True; break
                                    if ex["semester"] == semester:
                                        if is_common or ex["dept"] == "COMMON" or ex["dept"] == dept:
                                            lab_sem_conflict = True; break
                                if lab_sem_conflict:
                                    break
                                if is_common:
                                    if any(e[2] == slot_key and (e[1] == semester or e[0] is None) for e in assigned_slots):
                                        lab_sem_conflict = True; break
                                else:
                                    if (None, semester, slot_key) in assigned_slots or (dept, semester, slot_key) in assigned_slots:
                                        lab_sem_conflict = True; break
                            if lab_sem_conflict:
                                continue
                            eligible_lab = [c for c in all_classrooms
                                            if student_count == 0 or c["capacity"] >= student_count]
                            if not eligible_lab:
                                eligible_lab = all_classrooms
                            chosen_lab_room = None
                            for room in eligible_lab:
                                is_online = room["room_code"].upper() == "ONLINE"
                                if is_online:
                                    chosen_lab_room = room
                                    break
                                if all(
                                    room["id"] not in global_classroom_map.get(f"{search_day}_{s}", set()) and
                                    room["id"] not in classroom_occupied.get(f"{search_day}_{s}", set())
                                    for s in l_consec
                                ):
                                    chosen_lab_room = room
                                    break
                            if chosen_lab_room:
                                lab_best = (search_day, l_slot, l_consec, chosen_lab_room)
                                break
                        if lab_best:
                            break
                    # Lab fallback: relax only classroom constraints, keep availability + semester conflict
                    if lab_best is None:
                        all_days_lab = sorted(
                            XAI_SCHEDULE_DAYS,
                            key=lambda d: sum(1 for k in instructor_occupied.get(lecturer, set()) if k.startswith(f"{d}_"))
                        )
                        for search_day in all_days_lab:
                            for si in range(len(XAI_SCHEDULE_SLOTS)):
                                if si + lab_hrs > len(XAI_SCHEDULE_SLOTS):
                                    continue
                                l_slot = XAI_SCHEDULE_SLOTS[si]
                                l_consec = [XAI_SCHEDULE_SLOTS[si + k] for k in range(lab_hrs)]
                                if not all(f"{search_day}_{s}" in instr_avail for s in l_consec):
                                    continue
                                if any(f"{search_day}_{s}" in instructor_occupied.get(lecturer, set()) for s in l_consec):
                                    continue
                                # Semester conflict — hard, correctly scoped
                                lab_fb_sem_conflict = False
                                for s in l_consec:
                                    slot_key = f"{search_day}_{s}"
                                    for ex in global_conflict_map.get(slot_key, []):
                                        if is_common and ex["dept"] == "COMMON":
                                            lab_fb_sem_conflict = True; break
                                        if ex["semester"] == semester:
                                            if is_common or ex["dept"] == "COMMON" or ex["dept"] == dept:
                                                lab_fb_sem_conflict = True; break
                                    if lab_fb_sem_conflict:
                                        break
                                    if is_common:
                                        if any(e[2] == slot_key and (e[1] == semester or e[0] is None) for e in assigned_slots):
                                            lab_fb_sem_conflict = True; break
                                    else:
                                        if (None, semester, slot_key) in assigned_slots or (dept, semester, slot_key) in assigned_slots:
                                            lab_fb_sem_conflict = True; break
                                if lab_fb_sem_conflict:
                                    continue
                                eligible_lab_fb = [c for c in all_classrooms
                                                   if student_count == 0 or c["capacity"] >= student_count]
                                if not eligible_lab_fb:
                                    eligible_lab_fb = all_classrooms
                                chosen_lab_room = None
                                for room in eligible_lab_fb:
                                    if room["room_code"].upper() == "ONLINE":
                                        chosen_lab_room = room
                                        break
                                    if all(
                                        room["id"] not in global_classroom_map.get(f"{search_day}_{s}", set()) and
                                        room["id"] not in classroom_occupied.get(f"{search_day}_{s}", set())
                                        for s in l_consec
                                    ):
                                        chosen_lab_room = room
                                        break
                                if chosen_lab_room:
                                    lab_best = (search_day, l_slot, l_consec, chosen_lab_room)
                                    break
                            if lab_best:
                                break

                    # Lab last resort: relax classroom booking, keep only avail + semester + capacity
                    if lab_best is None:
                        lr_lab_days = sorted(
                            XAI_SCHEDULE_DAYS,
                            key=lambda d: sum(1 for k in instructor_occupied.get(lecturer, set()) if k.startswith(f"{d}_"))
                        )
                        for search_day in lr_lab_days:
                            for si in range(len(XAI_SCHEDULE_SLOTS)):
                                if si + lab_hrs > len(XAI_SCHEDULE_SLOTS):
                                    continue
                                l_slot = XAI_SCHEDULE_SLOTS[si]
                                l_consec = [XAI_SCHEDULE_SLOTS[si + k] for k in range(lab_hrs)]
                                if not all(f"{search_day}_{s}" in instr_avail for s in l_consec):
                                    continue
                                if any(f"{search_day}_{s}" in instructor_occupied.get(lecturer, set()) for s in l_consec):
                                    continue
                                lab_lr_sem_conflict = False
                                for s in l_consec:
                                    slot_key = f"{search_day}_{s}"
                                    for ex in global_conflict_map.get(slot_key, []):
                                        if is_common and ex["dept"] == "COMMON":
                                            lab_lr_sem_conflict = True; break
                                        if ex["semester"] == semester:
                                            if is_common or ex["dept"] == "COMMON" or ex["dept"] == dept:
                                                lab_lr_sem_conflict = True; break
                                    if lab_lr_sem_conflict:
                                        break
                                    if is_common:
                                        if any(e[2] == slot_key and (e[1] == semester or e[0] is None) for e in assigned_slots):
                                            lab_lr_sem_conflict = True; break
                                    else:
                                        if (None, semester, slot_key) in assigned_slots or (dept, semester, slot_key) in assigned_slots:
                                            lab_lr_sem_conflict = True; break
                                if lab_lr_sem_conflict:
                                    continue
                                eligible_lr = [c for c in all_classrooms
                                               if student_count == 0 or c["capacity"] >= student_count]
                                if not eligible_lr:
                                    eligible_lr = all_classrooms
                                chosen_lab_room = eligible_lr[0] if eligible_lr else (all_classrooms[0] if all_classrooms else None)
                                if chosen_lab_room:
                                    lab_best = (search_day, l_slot, l_consec, chosen_lab_room)
                                    break
                            if lab_best:
                                break

                    if lab_best:
                        ld, ls, lc, lr = lab_best
                        instructor_occupied.setdefault(lecturer, set()).update(f"{ld}_{s}" for s in lc)
                        if lr["room_code"].upper() != "ONLINE":
                            for s in lc:
                                classroom_occupied.setdefault(f"{ld}_{s}", set()).add(lr["id"])
                        assignments.append(WeeklySlotDto(
                            day=ld, timeSlot=ls,
                            courseCode=code, courseName=name,
                            lecturerUsername=lecturer, lecturerFullName=lecturer_name,
                            classroomCode=lr["room_code"],
                            department=dept, semester=semester, priority=target_priority,
                            duration=lab_hrs, isLab=True
                        ))
                    else:
                        unassigned.append(f"{code} ({lecturer_name}) — lab")
            else:
                unassigned.append(f"{code} ({lecturer_name})")

        result_suggestions.append(WeeklySuggestionDto(
            title=title, description=description,
            assignments=assignments, unassignedCourses=unassigned
        ))

    return WeeklyScheduleResponseDto(
        suggestions=result_suggestions,
        phase=body.phase,
        algorithmNote=(
            f"Phase {phase_index + 1} (Priority {target_priority}): "
            f"{len(phase_courses)} courses. "
            "Each option uses a different scheduling strategy. "
            "Hard constraints: instructor availability, semester conflicts, classroom availability. "
            "Courses without submitted availability cannot be assigned."
        )
    )

# --- AUDIT LOGS (CSV-backed) ---

@app.get("/logs", response_model=List[AuditLogDto], tags=["Admin"])
def get_logs(token_data: dict = Depends(verify_token),
             limit: int = 200, offset: int = 0,
             action_filter: Optional[str] = None):
    if token_data.get("role") != "SUPER_ADMIN":
        raise HTTPException(status_code=403, detail="Only super admins can view logs")
    with _log_lock:
        if not os.path.exists(_LOG_FILE):
            return []
        with open(_LOG_FILE, "r", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
    rows.reverse()  # newest first
    if action_filter:
        af = action_filter.lower()
        rows = [r for r in rows if af in r.get("action", "").lower()]
    rows = rows[offset: offset + min(limit, 500)]
    return [AuditLogDto(
        id=int(r.get("id") or 0),
        timestamp=r.get("timestamp", ""),
        actor_username=r.get("actor_username", ""),
        actor_role=r.get("actor_role", ""),
        action=r.get("action", ""),
        target=r.get("target", ""),
        details=r.get("details", ""),
        ip_address=r.get("ip_address", ""),
        user_agent=r.get("user_agent", "")
    ) for r in rows]

@app.get("/logs/stream", tags=["Admin"])
async def stream_logs(request: Request, token: str = Query(...)):
    try:
        payload = jwt.decode(clean_token(token), SECRET_KEY, algorithms=[ALGORITHM])
        if payload.get("role") != "SUPER_ADMIN":
            raise HTTPException(status_code=403)
    except JWTError:
        raise HTTPException(status_code=401)

    queue: asyncio.Queue = asyncio.Queue()
    _log_listeners.add(queue)

    async def generate():
        try:
            while True:
                if await request.is_disconnected():
                    break
                try:
                    data = await asyncio.wait_for(queue.get(), timeout=25)
                    yield f"data: {data}\n\n"
                except asyncio.TimeoutError:
                    yield ": keepalive\n\n"
        finally:
            _log_listeners.discard(queue)

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
    )

# --- STATIC WEB FILES & ROOT ---

@app.get("/", tags=["Health"])
def root():
    index_path = os.path.join(_WEB_DIR, "index.html")
    if os.path.isfile(index_path):
        return FileResponse(index_path)
    return {"message": "OptiClass API Online!"}

@app.get("/health", tags=["Health"])
def health():
    return {"message": "OptiClass API Online!", "status": "ok"}
