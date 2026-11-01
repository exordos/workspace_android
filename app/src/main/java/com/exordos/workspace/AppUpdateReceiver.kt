package com.exordos.workspace

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Removes obsolete task roots when an update changes the application's activities. */
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        context.getSystemService(ActivityManager::class.java).appTasks.forEach { task ->
            try {
                val root = task.taskInfo?.baseIntent ?: return@forEach
                if (root.component?.packageName == context.packageName &&
                    context.packageManager.resolveActivity(root, 0) == null) {
                    task.finishAndRemoveTask()
                }
            } catch (_: IllegalArgumentException) {
                // Android may remove the task between the snapshot and this cleanup.
            }
        }
    }
}
