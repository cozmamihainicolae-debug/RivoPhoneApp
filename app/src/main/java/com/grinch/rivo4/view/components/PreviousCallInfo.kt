package com.grinch.rivo4.view.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telecom.Call
import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PreviousCall(
    val date: Long,
    val duration: Long
)

private fun normalizeRomanianNumber(number: String): String {
    val normalized = PhoneNumberUtils.normalizeNumber(number)

    if (normalized.isEmpty()) return ""

    return when {
        normalized.startsWith("0040") ->
            normalized.removePrefix("0040")

        normalized.startsWith("+40") ->
            normalized.removePrefix("+40")

        normalized.startsWith("40") && normalized.length >= 11 ->
            normalized.removePrefix("40")

        normalized.startsWith("0") ->
            normalized.removePrefix("0")

        else ->
            normalized.takeLast(9)
    }.takeLast(9)
}

private suspend fun findPreviousCall(
    context: Context,
    phoneNumber: String,
    currentCallStartTime: Long
): PreviousCall? = withContext(Dispatchers.IO) {

    if (
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return@withContext null
    }

    val targetNumber = normalizeRomanianNumber(phoneNumber)

    if (targetNumber.isEmpty()) {
        return@withContext null
    }

    val projection = arrayOf(
        CallLog.Calls.NUMBER,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION
    )

    try {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->

            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

            if (numberIndex < 0 || dateIndex < 0 || durationIndex < 0) {
                return@withContext null
            }

            while (cursor.moveToNext()) {

                val number = cursor.getString(numberIndex) ?: continue
                val date = cursor.getLong(dateIndex)
                val duration = cursor.getLong(durationIndex)

                val normalizedNumber = normalizeRomanianNumber(number)

                if (normalizedNumber != targetNumber) {
                    continue
                }

                /*
                 * Do not return the call that is currently ringing.
                 * The current incoming call may already exist in CallLog
                 * on some Android/Oppo versions.
                 */
                if (currentCallStartTime > 0L && date >= currentCallStartTime) {
                    continue
                }

                return@withContext PreviousCall(
                    date = date,
                    duration = duration
                )
            }
        }
    } catch (_: SecurityException) {
        return@withContext null
    } catch (_: Exception) {
        return@withContext null
    }

    null
}

private fun formatPreviousCallDate(timestamp: Long): String {
    return SimpleDateFormat(
        "dd.MM.yyyy, HH:mm",
        Locale.getDefault()
    ).format(Date(timestamp))
}

private fun formatPreviousCallDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60

    return if (hours > 0) {
        String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            remainingSeconds
        )
    } else {
        String.format(
            Locale.getDefault(),
            "%02d:%02d",
            minutes,
            remainingSeconds
        )
    }
}

@Composable
fun PreviousCallInfo(
    call: Call,
    phoneNumber: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val currentCallStartTime = call.details?.creationTimeMillis ?: 0L

    val previousCall by produceState<PreviousCall?>(
        initialValue = null,
        key1 = phoneNumber,
        key2 = currentCallStartTime
    ) {
        value = findPreviousCall(
            context = context,
            phoneNumber = phoneNumber,
            currentCallStartTime = currentCallStartTime
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )
        ) {
            if (previousCall == null) {
                Text(
                    text = "Niciun apel anterior",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Ultimul apel: ${
                        formatPreviousCallDate(previousCall!!.date)
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Durată: ${
                        formatPreviousCallDuration(previousCall!!.duration)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}