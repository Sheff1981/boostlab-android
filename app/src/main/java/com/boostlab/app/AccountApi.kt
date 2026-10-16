package com.boostlab.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object AccountApi {
    const val BASE_URL = "https://212.193.15.139"
    fun authenticated(path: String, token: String, method: String = "GET"): JSONObject {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Authorization", "Bearer " + token)
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Сессия истекла или сервер недоступен (HTTP ${connection.responseCode})")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    fun linkDevice(sessionToken: String, deviceId: String, linkToken: String): JSONObject {
        val connection = URL(BASE_URL + "/v1/accounts/devices/link").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer " + sessionToken)
        connection.setRequestProperty("Content-Type", "application/json")
        try {
            connection.outputStream.use {
                it.write(JSONObject().put("device_id", deviceId).put("link_token", linkToken)
                    .toString().toByteArray(Charsets.UTF_8))
            }
            if (connection.responseCode != 200) throw IllegalStateException("Привязка устройства не выполнена (HTTP ${connection.responseCode})")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    fun request(path: String, email: String, password: String): JSONObject {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        try {
            connection.outputStream.use {
                it.write(JSONObject().put("email", email).put("password", password)
                    .toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = when (status) {
                    400 -> "Проверь email и пароль: от 8 символов, заглавная и строчная буква, цифра"
                    401 -> "Неверный email или пароль"
                    409 -> "Такой email уже зарегистрирован"
                    429 -> "Слишком много попыток. Попробуй позже"
                    else -> "Ошибка сервера: HTTP $status"
                }
                throw IllegalStateException(message)
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
