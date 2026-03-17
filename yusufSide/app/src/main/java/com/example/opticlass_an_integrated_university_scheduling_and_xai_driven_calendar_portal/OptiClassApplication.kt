package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.app.Application
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.database.AppDatabase

class OptiClassApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
