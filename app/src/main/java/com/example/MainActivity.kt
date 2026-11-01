package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.BiometricHelper
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

  private val readerViewModel: ReaderViewModel by lazy {
    ViewModelProvider(this)[ReaderViewModel::class.java]
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    handleIncomingIntent(intent)

    setContent {
      MyApplicationTheme {
        BookLishApp(
          activity = this,
          viewModel = readerViewModel
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  override fun onPause() {
    super.onPause()
    readerViewModel.saveCurrentReadingProgress()
  }

  override fun onStop() {
    super.onStop()
    readerViewModel.saveCurrentReadingProgress()
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return
    val action = intent.action
    if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) {
      val uri: Uri? = when (action) {
        Intent.ACTION_SEND -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
          } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
          }
        }
        else -> intent.data ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
      }

      uri?.let {
        readerViewModel.openFromExternalUri(it)
      }
    }
  }
}

@Composable
fun BookLishApp(
  activity: FragmentActivity,
  viewModel: ReaderViewModel = viewModel()
) {
  val books by viewModel.allBooks.collectAsState()
  val vaultBooks by viewModel.vaultBooks.collectAsState()
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  var showEmulatorFallbackDialog by remember { mutableStateOf(false) }

  val triggerBiometricAuthentication = {
    if (BiometricHelper.canAuthenticate(activity)) {
      BiometricHelper.authenticate(
        activity = activity,
        title = "Verifikasi Sidik Jari",
        subtitle = "Pindai sidik jari Anda untuk membuka tab tersembunyi",
        onSuccess = {
          viewModel.unlockVault()
        },
        onError = { errorMsg ->
          coroutineScope.launch {
            snackbarHostState.showSnackbar(errorMsg)
          }
        }
      )
    } else {
      // Hardware biometric or lock screen is not enrolled (e.g. streaming emulator)
      // Provide clean fallback so the user can still test & unlock
      showEmulatorFallbackDialog = true
    }
  }

  LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let { error ->
      snackbarHostState.showSnackbar(error)
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(snackbarHostState) }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      AnimatedContent(
        targetState = when {
          uiState.currentBook != null -> "READER"
          uiState.isVaultUnlocked -> "VAULT"
          else -> "HOME"
        },
        transitionSpec = {
          if (targetState == "READER") {
            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
          } else if (targetState == "VAULT") {
            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
          } else {
            slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
          }
        },
        label = "book_navigation_transition"
      ) { screenState ->
        when (screenState) {
          "READER" -> {
            BackHandler {
              viewModel.closeReader()
            }

            ReaderScreen(
              viewModel = viewModel,
              uiState = uiState,
              onBackToLibrary = { viewModel.closeReader() }
            )
          }
          "VAULT" -> {
            VaultScreen(
              vaultBooks = vaultBooks,
              onOpenBook = { book -> viewModel.openBook(book) },
              onImportUri = { uri -> viewModel.importBookFromUri(uri, isVault = true) },
              onDeleteBook = { book -> viewModel.deleteBook(book) },
              onQuickExit = { viewModel.quickExitVault() }
            )
          }
          else -> {
            HomeScreen(
              books = books,
              onOpenBook = { book -> viewModel.openBook(book) },
              onImportUri = { uri -> viewModel.importBookFromUri(uri, isVault = false) },
              onDeleteBook = { book -> viewModel.deleteBook(book) },
              onTriggerVaultUnlock = { triggerBiometricAuthentication() }
            )
          }
        }
      }

      if (uiState.isBookLoading) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }
      }
    }
  }

  // Fallback dialog when running on devices/emulators without enrolled biometric hardware
  if (showEmulatorFallbackDialog) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showEmulatorFallbackDialog = false },
      title = {
        androidx.compose.material3.Text(
          text = "Verifikasi Sidik Jari",
          fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
      },
      text = {
        androidx.compose.material3.Text(
          text = "Perangkat/emulator ini belum mendaftarkan sidik jari fisik. Konfirmasi untuk membuka Tab Tersembunyi:"
        )
      },
      confirmButton = {
        androidx.compose.material3.Button(
          onClick = {
            showEmulatorFallbackDialog = false
            viewModel.unlockVault()
          }
        ) {
          androidx.compose.material3.Text("Buka Tab Tersembunyi")
        }
      },
      dismissButton = {
        androidx.compose.material3.TextButton(
          onClick = { showEmulatorFallbackDialog = false }
        ) {
          androidx.compose.material3.Text("Batal")
        }
      }
    )
  }
}
