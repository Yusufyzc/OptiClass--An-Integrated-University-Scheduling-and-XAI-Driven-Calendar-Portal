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

# --- CONFIGURATION AND DATABASE CONNECTION ---
DB_URL = "postgresql://yusuf:123456@localhost/opticlass"
SECRET_KEY = "opticlass_cok_gizli_anahtar"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 10080  # 7 days

app = FastAPI(title="OptiClass API", version="1.0.0")

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
    conn.commit()
    cur.close()
    conn.close()

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
        raise HTTPException(status_code=401, detail="Invalid credentials")
    token = create_access_token(data={"sub": user["username"], "role": user["role"]})
    return LoginResponse(
        token=token, role=user["role"], username=user["username"],
        mustChangePassword=user["must_change_password"]
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
def create_user(user: UserCreateDto, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
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
    return UserDto(username=user.username, role=user.role, fullName=user.fullName,
                   email=user.email, department=user.department)

@app.put("/users/{username}", response_model=UserDto, tags=["Users"])
def update_user(username: str, user: UserUpdateDto, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
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
                         token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
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
def delete_user(username: str, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
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
def add_classroom(classroom: ClassroomDto, token_data: dict = Depends(verify_token)):
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
    return classroom

@app.delete("/classrooms/{id}", tags=["Classrooms"])
def delete_classroom(id: str, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
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
    return {"message": "Classroom deleted"}

@app.get("/courses", response_model=List[CourseDto], tags=["Courses"])
def get_courses(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT code, name, lecturer_username, department, email, duration, classroom_id, semester, student_count, priority FROM courses")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [CourseDto(code=r["code"], name=r["name"], lecturerUsername=r["lecturer_username"],
                     department=r["department"], email=r["email"], duration=r["duration"],
                     classroomId=r["classroom_id"], semester=r["semester"] or 1,
                     studentCount=r["student_count"] or 0, priority=r["priority"] or 1) for r in rows]

@app.post("/courses/bulk", tags=["Courses"])
def import_courses(courses: List[CourseDto], token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        for c in courses:
            cur.execute("""INSERT INTO courses (code, name, lecturer_username, department,
                           email, duration, classroom_id, semester, student_count, priority)
                           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                           ON CONFLICT (code) DO UPDATE SET
                           name=EXCLUDED.name,
                           lecturer_username=EXCLUDED.lecturer_username,
                           email=EXCLUDED.email,
                           semester=EXCLUDED.semester,
                           student_count=EXCLUDED.student_count,
                           priority=EXCLUDED.priority""",
                        (c.code, c.name, c.lecturerUsername, c.department,
                         c.email, c.duration, c.classroomId, c.semester, c.studentCount, c.priority))
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        cur.close()
        conn.close()
    return {"status": "ok"}

@app.delete("/courses/{code}", tags=["Courses"])
def delete_course(code: str, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
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
        conn.commit()
    finally:
        cur.close()
        conn.close()
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
        cur.execute("INSERT INTO notifications (recipient_username, text) VALUES (%s,%s)",
                    (notif.recipientUsername, notif.text))
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

@app.put("/settings/scheduling_phase", tags=["Settings"])
def set_scheduling_phase(body: SchedulingPhaseDto, token_data: dict = Depends(verify_token)):
    if token_data.get("role") != "ADMIN":
        raise HTTPException(status_code=403, detail="Only admins can change scheduling phase")
    if body.phase not in ("PHASE_1", "PHASE_2"):
        raise HTTPException(status_code=400, detail="Invalid phase value")
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
    return {"phase": body.phase}

@app.get("/common_course_slots", tags=["Schedules"])
def get_common_course_slots(token_data: dict = Depends(verify_token)):
    conn = get_db_connection()
    cur = conn.cursor()
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
            if course.get("department") == "COMMON":
                underscore_idx = key.index("_")
                day = key[:underscore_idx]
                slot = key[underscore_idx + 1:]
                if day not in result:
                    result[day] = []
                if slot not in result[day]:
                    result[day].append(slot)
    return result

@app.get("/", tags=["Health"])
def root():
    return {"message": "OptiClass API Online!"}
