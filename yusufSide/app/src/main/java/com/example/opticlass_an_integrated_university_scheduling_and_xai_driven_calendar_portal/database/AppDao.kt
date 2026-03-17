package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Department
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDepartment(department: Department)

    @Query("SELECT * FROM department")
    fun getAllDepartments(): Flow<List<Department>>

    // Semester
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemester(semester: Semester)

    @Query("SELECT * FROM semester")
    fun getAllSemesters(): Flow<List<Semester>>

    // Lecturer
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecturer(lecturer: Lecturer)

    @Query("SELECT * FROM lecturer")
    fun getAllLecturers(): Flow<List<Lecturer>>

    // User
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM user WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM user")
    fun getAllUsers(): Flow<List<User>>

    // Classrooms
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassroom(classroom: Classrooms)

    @Query("SELECT * FROM classrooms")
    fun getAllClassrooms(): Flow<List<Classrooms>>

    // Courses
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Courses)

    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<Courses>>

    // CourseDepartment
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourseDepartment(courseDepartment: CourseDepartment)

    // AvailabilityStatus
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAvailabilityStatus(availabilityStatus: AvailabilityStatus)

    @Query("SELECT * FROM availability_status WHERE lecturer_id = :lecturerId")
    fun getAvailabilityByLecturer(lecturerId: String): Flow<List<AvailabilityStatus>>
}
