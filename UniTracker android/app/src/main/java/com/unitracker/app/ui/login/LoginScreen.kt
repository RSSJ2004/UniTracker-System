package com.unitracker.app.ui.login

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun CampusLoginWebView(
    onCookiesCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("unitracker_settings", Context.MODE_PRIVATE) }

    val userEmail = prefs.getString("default_email", "") ?: ""
    var showGuide by remember { mutableStateOf(!prefs.getBoolean("hide_login_guide", false)) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val loginUrl = "https://campusvirtual.intec.edu.do/login/index.php"

    Column(modifier = Modifier.fillMaxSize()) {
        if (showGuide) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡 Guía de Inicio de Sesión y MFA", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { showConfirmDialog = true }) {
                            Text("Saltar guía")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("1. Tu correo se autocompletará automáticamente si lo configuraste.", style = MaterialTheme.typography.bodySmall)
                    Text("2. Introduce tu contraseña institucional de forma segura.", style = MaterialTheme.typography.bodySmall)
                    Text("3. Aprueba la notificación de tu Microsoft Authenticator.", style = MaterialTheme.typography.bodySmall)
                    Text("4. Marca 'Sí' en '¿Mantener la sesión iniciada?' para cookies de larga duración.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Diálogo de confirmación para saltar la guía
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("¿Saltar guía?") },
                text = { Text("¿Estás seguro de que deseas ocultar esta guía? No volverá a aparecer al renovar sesión.") },
                confirmButton = {
                    Button(onClick = {
                        prefs.edit().putBoolean("hide_login_guide", true).apply()
                        showGuide = false
                        showConfirmDialog = false
                    }) {
                        Text("Sí, saltar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // WebView que ocupa el resto de la pantalla
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val currentUrl = url ?: ""

                            if (currentUrl.contains("login.microsoftonline.com") && userEmail.isNotBlank()) {
                                val autofillScript = """
                                    (function() {
                                        let emailInput = document.querySelector('input[type="email"]');
                                        if (emailInput && !emailInput.value) {
                                            emailInput.value = '$userEmail';
                                            let nextBtn = document.querySelector('input[type="submit"]') || document.querySelector('button[type="submit"]');
                                            if (nextBtn) nextBtn.click();
                                        }
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(autofillScript, null)
                            }

                            if (currentUrl.contains("campusvirtual.intec.edu.do")) {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.flush()

                                val rawCookies = cookieManager.getCookie(currentUrl)
                                    ?: cookieManager.getCookie("https://campusvirtual.intec.edu.do")

                                if (!rawCookies.isNullOrEmpty() && rawCookies.contains("MoodleSession")) {
                                    val cookieEntries = rawCookies.split(";")
                                    val jsonCookies = cookieEntries.mapNotNull { entry ->
                                        val parts = entry.trim().split("=", limit = 2)
                                        if (parts.size == 2) {
                                            val name = parts[0].trim()
                                            val value = parts[1].trim()
                                            val domain = if (name.contains("ESTS", ignoreCase = true) || name.contains("esctx", ignoreCase = true)) {
                                                ".login.microsoftonline.com"
                                            } else {
                                                "campusvirtual.intec.edu.do"
                                            }
                                            """
                                            {
                                              "name": "$name",
                                              "value": "$value",
                                              "domain": "$domain",
                                              "path": "/",
                                              "expires": -1,
                                              "httpOnly": false,
                                              "secure": true,
                                              "sameSite": "None"
                                            }
                                            """.trimIndent()
                                        } else null
                                    }.joinToString(",")

                                    val jsonContent = """
                                    {
                                      "cookies": [ $jsonCookies ],
                                      "origins": []
                                    }
                                    """.trimIndent()

                                    val file = File(ctx.filesDir, "storageState.json")
                                    file.writeText(jsonContent)

                                    Toast.makeText(ctx, "¡Sesión guardada con éxito!", Toast.LENGTH_SHORT).show()
                                    onCookiesCaptured(rawCookies)
                                }
                            }
                        }
                    }
                    loadUrl(loginUrl)
                }
            }
        )
    }
}