package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LishPDF", appName)
    val openWith = context.getString(R.string.open_with_pdf_reader)
    assertEquals("Buka dengan LishPDF", openWith)
  }

  @Test
  fun `intent filter resolves ACTION_VIEW for pdf mimeType`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
      setDataAndType(android.net.Uri.parse("content://com.android.providers.media.documents/document/123"), "application/pdf")
      addCategory(android.content.Intent.CATEGORY_DEFAULT)
    }
    val activities = context.packageManager.queryIntentActivities(intent, 0)
    val matched = activities.any { it.activityInfo.packageName == context.packageName }
    org.junit.Assert.assertTrue("LishPDF MainActivity should resolve application/pdf VIEW intents", matched)
  }

  @Test
  fun `intent filter resolves ACTION_SEND for pdf mimeType`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
      type = "application/pdf"
      addCategory(android.content.Intent.CATEGORY_DEFAULT)
    }
    val activities = context.packageManager.queryIntentActivities(intent, 0)
    val matched = activities.any { it.activityInfo.packageName == context.packageName }
    org.junit.Assert.assertTrue("LishPDF MainActivity should resolve application/pdf SEND intents", matched)
  }
}
