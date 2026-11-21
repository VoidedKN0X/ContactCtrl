package com.vknxworks.contactctrl.ui.contacts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.data.contacts.ContactDetail
import com.vknxworks.contactctrl.data.contacts.QrVCard
import com.vknxworks.contactctrl.data.qr.QrCode
import com.vknxworks.contactctrl.ui.components.CtrlAlertDialog
import com.vknxworks.contactctrl.ui.theme.CtrlColors

/**
 * The contact as a QR code another phone's camera can scan to add it. Made on the phone, nothing leaves it.
 * Always black on white with a quiet zone, whatever the theme, so every scanner reads it.
 */
@Composable
fun ContactQrDialog(d: ContactDetail, onDismiss: () -> Unit) {
    // With the note if it fits, else without it; null when even that is too much for a QR code.
    val result = remember(d) {
        val full = QrVCard.build(d, withNote = true)
        QrCode.encode(full.toByteArray(Charsets.UTF_8))?.let { it to false }
            ?: if (d.note.isNotBlank()) QrCode.encode(QrVCard.build(d, withNote = false).toByteArray(Charsets.UTF_8))?.let { it to true } else null
    }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (result == null) {
                    Text(stringResource(R.string.qr_too_big), style = MaterialTheme.typography.bodyMedium)
                } else {
                    val (qr, noteLeftOut) = result
                    QrImage(qr, stringResource(R.string.qr_description, d.displayName))
                    Text(
                        stringResource(if (noteLeftOut) R.string.qr_hint_no_note else R.string.qr_hint),
                        style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun QrImage(qr: QrCode, description: String) {
    val quiet = 4
    Box(
        Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(2.dp)) {
            val cells = qr.size + quiet * 2
            val cell = size.minDimension / cells
            for (y in 0 until qr.size) for (x in 0 until qr.size) {
                if (qr.dark(x, y)) {
                    // A hair larger than the cell, so no seams show between neighbouring modules.
                    drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}
