package com.unitracker.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Una tarea tal cual viene en el JSON de la Lambda. */
data class RemoteTask(
    val id: String,
    val type: String,
    val phase: String,
    val title: String,
    val course: String?,
    val dueAt: String?,
    val dueAtRaw: String?,
    val url: String?
)

/** httpCode = -1 cuando el error es de configuración local (sin llamada de red). */
class ApiException(val httpCode: Int, message: String) : Exception(message)

class TaskApi(private val lambdaUrl: String, private val apiKey: String) {

    suspend fun fetchTasks(): List<RemoteTask> = withContext(Dispatchers.IO) {
        if (lambdaUrl.isBlank() || apiKey.isBlank()) {
            throw ApiException(-1, "Falta lambda.url o lambda.apiKey en local.properties")
        }
        val conn = URL(lambdaUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("accept", "application/json")
            conn.connectTimeout = 15_000
            // La Lambda abre un navegador: el primer arranque en frío puede tardar.
            conn.readTimeout = 90_000

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw ApiException(code, "HTTP $code: ${body.take(300)}")
            }
            parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<RemoteTask> {
        val root = JSONObject(body)
        if (!root.optBoolean("ok", false)) {
            throw ApiException(500, root.optString("error", "Respuesta sin ok=true").take(300))
        }
        val arr: JSONArray = root.optJSONArray("tasks") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RemoteTask(
                id = o.getString("id"),
                type = o.optString("type", "task"),
                phase = o.optString("phase", "due"),
                title = o.getString("title"),
                course = o.optStringOrNull("course"),
                dueAt = o.optStringOrNull("dueAt"),
                dueAtRaw = o.optStringOrNull("dueAtRaw"),
                url = o.optStringOrNull("url")
            )
        }
    }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
