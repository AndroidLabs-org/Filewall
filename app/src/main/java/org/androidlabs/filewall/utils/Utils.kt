package org.androidlabs.filewall.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.text.Html
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

class Utils {
    companion object {
        fun getAppName(context: Context, packageName: String): String {
            return try {
                val packageManager = context.packageManager
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                val appName = packageManager.getApplicationLabel(applicationInfo)
                appName.toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }

        fun getAppIcon(context: Context, packageName: String): Drawable? {
            return try {
                val packageManager = context.packageManager
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                val icon = packageManager.getApplicationIcon(applicationInfo)
                icon
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

        fun startActivity(context: Context?,
                          activityClass: Class<out Any>,
                          configureExtras: (Intent.() -> Unit)? = null
        ) {
            val intent = Intent(context, activityClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                configureExtras?.invoke(this)
            }
            context?.startActivity(intent)
        }

        fun sendBroadcast( context: Context?,
                           receiverClass: Class<out Any>,
                           configureExtras: (Intent.() -> Unit)? = null) {
            val intent = Intent(context, receiverClass).apply {
                configureExtras?.invoke(this)
            }
            context?.sendBroadcast(intent)
        }

        fun startActivityOrSendBroadcast(
            context: Context?,
            activityClass: Class<out Any>,
            receiverClass: Class<out Any>,
            configureExtras: (Intent.() -> Unit)? = null
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                startActivity(context,activityClass,configureExtras)
            } else {
                sendBroadcast(context,receiverClass,configureExtras)
            }
        }
    }
}

fun htmlToAnnotatedString(html: String): AnnotatedString {
    val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    return buildAnnotatedString {
        val text = spanned.toString()
        append(text)
        val spans = spanned.getSpans(0, spanned.length, Any::class.java)
        for (span in spans) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            when (span) {
                is StyleSpan -> when (span.style) {
                    android.graphics.Typeface.BOLD ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    android.graphics.Typeface.ITALIC ->
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                }
                is UnderlineSpan ->
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            }
        }
    }
}