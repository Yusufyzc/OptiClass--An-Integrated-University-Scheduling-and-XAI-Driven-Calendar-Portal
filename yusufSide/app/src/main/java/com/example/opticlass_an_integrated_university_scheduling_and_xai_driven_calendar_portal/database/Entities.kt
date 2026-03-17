package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "department")
data class Department(
    @PrimaryKey
    @ColumnInfo(name = "department_id")
    val departmentId: String,
    @ColumnInfo(name = "department_name")
    val departmentName: String
)

@Entity(tableName = "semester")
data class Semester(
    @PrimaryKey
    @ColumnInfo(name = "semester_id")
    val semesterId: String,
    @ColumnInfo(name = "semester_name")
    val semesterName: String,
    @ColumnInfo(name = "start_date")
    val startDate: String, // 'YYYY-MM-DD'
    @ColumnInfo(name = "finish_date")
    val finishDate: String, // 'YYYY-MM-DD'
    @ColumnInfo(name = "status")
    val status: Int // 0 or 1
)

@Entity(
    tableName = "lecturer",
    foreignKeys = [
        ForeignKey(
            entity = Department::class,
            parentColumns = ["department_id"],
            childColumns = ["department_id"]
        )
    ]
)
data class Lecturer(
    @PrimaryKey
    @ColumnInfo(name = "lecturer_id")
    val lecturerId: String,
    @ColumnInfo(name = "department_id")
    val departmentId: String
)

@Entity(
    tableName = "user",
    foreignKeys = [
        ForeignKey(
            entity = Lecturer::class,
            parentColumns = ["lecturer_id"],
            childColumns = ["lecturer_id"]
        )
    ],
    indices = [Index(value = ["email"], unique = true)]
)
data class User(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "name_surname")
    val nameSurname: String,
    @ColumnInfo(name = "email")
    val email: String,
    @ColumnInfo(name = "password")
    val password: String,
    @ColumnInfo(name = "lecturer_id")
    val lecturerId: String?, // NULL if admin
    @ColumnInfo(name = "role")
    val role: String // 'admin' or 'lecturer'
)

@Entity(tableName = "classrooms")
data class Classrooms(
    @PrimaryKey
    @ColumnInfo(name = "classroom_id")
    val classroomId: String,
    @ColumnInfo(name = "classroom_name")
    val classroomName: String,
    @ColumnInfo(name = "capacity")
    val capacity: Int,
    @ColumnInfo(name = "lab_existence")
    val labExistence: Int, // 0 or 1
    @ColumnInfo(name = "building")
    val building: String?
)

@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = Lecturer::class,
            parentColumns = ["lecturer_id"],
            childColumns = ["lecturer_id"]
        ),
        ForeignKey(
            entity = Lecturer::class,
            parentColumns = ["lecturer_id"],
            childColumns = ["course_assistance_id"]
        ),
        ForeignKey(
            entity = Semester::class,
            parentColumns = ["semester_id"],
            childColumns = ["semester_id"]
        )
    ],
    indices = [
        Index(value = ["lecturer_id"]),
        Index(value = ["semester_id"])
    ]
)
data class Courses(
    @PrimaryKey
    @ColumnInfo(name = "course_id")
    val courseId: String,
    @ColumnInfo(name = "course_code")
    val courseCode: String,
    @ColumnInfo(name = "course_name")
    val courseName: String,
    @ColumnInfo(name = "course_hours")
    val courseHours: Int,
    @ColumnInfo(name = "lab_existence")
    val labExistence: Int, // 0 or 1
    @ColumnInfo(name = "lab_hours")
    val labHours: Int?, // NULL if no lab
    @ColumnInfo(name = "lecturer_id")
    val lecturerId: String,
    @ColumnInfo(name = "course_assistance_id")
    val courseAssistanceId: String?,
    @ColumnInfo(name = "course_capacity")
    val courseCapacity: Int,
    @ColumnInfo(name = "semester_id")
    val semesterId: String
)

@Entity(
    tableName = "course_department",
    foreignKeys = [
        ForeignKey(
            entity = Courses::class,
            parentColumns = ["course_id"],
            childColumns = ["course_id"]
        ),
        ForeignKey(
            entity = Department::class,
            parentColumns = ["department_id"],
            childColumns = ["department_id"]
        )
    ],
    indices = [
        Index(value = ["course_id", "department_id"], unique = true),
        Index(value = ["course_id"]),
        Index(value = ["department_id"])
    ]
)
data class CourseDepartment(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "course_id")
    val courseId: String,
    @ColumnInfo(name = "department_id")
    val departmentId: String
)

@Entity(
    tableName = "availability_status",
    foreignKeys = [
        ForeignKey(
            entity = Lecturer::class,
            parentColumns = ["lecturer_id"],
            childColumns = ["lecturer_id"]
        ),
        ForeignKey(
            entity = Semester::class,
            parentColumns = ["semester_id"],
            childColumns = ["semester_id"]
        )
    ],
    indices = [
        Index(value = ["lecturer_id"]),
        Index(value = ["semester_id"])
    ]
)
data class AvailabilityStatus(
    @PrimaryKey
    @ColumnInfo(name = "availability_id")
    val availabilityId: String,
    @ColumnInfo(name = "lecturer_id")
    val lecturerId: String,
    @ColumnInfo(name = "day")
    val day: String,
    @ColumnInfo(name = "start_time")
    val startTime: String, // 'HH:MM'
    @ColumnInfo(name = "finish_time")
    val finishTime: String, // 'HH:MM'
    @ColumnInfo(name = "semester_id")
    val semesterId: String
)
