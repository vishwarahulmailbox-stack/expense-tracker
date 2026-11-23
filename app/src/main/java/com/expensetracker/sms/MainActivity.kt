package com.expensetracker.sms

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.expensetracker.sms.ui.App
import com.expensetracker.sms.ui.AppTheme

/** What a notification asked us to open. */
data class LaunchRequest(val openBills: Boolean, val payBillId: Long?)

class MainActivity : FragmentActivity() {
    private val launch = mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BillReminders.schedule(this)
        handle(intent)
        setContent {
            AppTheme { App(launch = launch.value, onLaunchHandled = { launch.value = null }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i == null) return
        val pay = i.getLongExtra(Notifier.EXTRA_PAY_BILL, -1L).takeIf { it > 0 }
        val open = i.getBooleanExtra(Notifier.EXTRA_OPEN_BILLS, false)
        if (pay != null || open) launch.value = LaunchRequest(openBills = true, payBillId = pay)
    }
}
