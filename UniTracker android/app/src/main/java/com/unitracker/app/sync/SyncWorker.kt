package com.unitracker.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unitracker.app.UniTrackerApp
import com.unitracker.app.data.remote.ApiException
import org.json.JSONException
import java.io.IOException

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as UniTrackerApp).container.repository
        return try {
            repository.sync()
            Result.success()
        } catch (e: ApiException) {
            when {
                // La sesión guardada en la Lambda caducó: reintentar no sirve.
                e.message?.contains("SESION_INVALIDA") == true -> Result.failure()
                // Error de configuración local o clave incorrecta: tampoco.
                e.httpCode == -1 || e.httpCode == 401 || e.httpCode == 403 -> Result.failure()
                runAttemptCount < 3 -> Result.retry()
                else -> Result.failure()
            }
        } catch (e: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: JSONException) {
            // La respuesta no tenía el formato esperado: reintentar no lo arregla.
            Result.failure()
        }
    }
}
