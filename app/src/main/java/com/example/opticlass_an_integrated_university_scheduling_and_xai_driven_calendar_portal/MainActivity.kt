package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.ui.theme.OptiClassAn_Integrated_University_Scheduling_and_XAIDriven_Calendar_PortalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel: AppViewModel by viewModels()
        setContent {
            OptiClassAn_Integrated_University_Scheduling_and_XAIDriven_Calendar_PortalTheme {
                OptiClassApp(viewModel)
            }
        }
    }
}
