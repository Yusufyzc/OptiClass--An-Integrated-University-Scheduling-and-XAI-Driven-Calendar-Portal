package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network

object TokenStore {
    private var _token: String? = null
    var token: String?
        get() = _token
        set(value) { _token = value }
}