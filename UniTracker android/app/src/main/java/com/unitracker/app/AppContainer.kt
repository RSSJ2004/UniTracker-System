package com.unitracker.app

import android.content.Context
import com.unitracker.app.alarm.AlarmScheduler
import com.unitracker.app.data.TaskRepository
import com.unitracker.app.data.local.AppDatabase
import com.unitracker.app.data.remote.TaskApi

/**
 * Contenedor de dependencias manual (sin Hilt): para esta app son solo
 * unos pocos objetos y evita sumar otro plugin que pueda chocar con KSP.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.build(appContext)
    private val api = TaskApi(BuildConfig.LAMBDA_URL, BuildConfig.LAMBDA_API_KEY)

    val alarmScheduler = AlarmScheduler(appContext, database.reminderDao())
    val repository = TaskRepository(database, api, alarmScheduler)
}
