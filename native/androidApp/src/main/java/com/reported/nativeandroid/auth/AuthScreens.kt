package com.reported.nativeandroid.auth

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.reported.nativeandroid.R
import com.reported.nativeandroid.auth.findActivity
import com.reported.nativeandroid.auth.launchAppleSignIn
import com.reported.nativeandroid.auth.signInWithGoogle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.reported.nativeandroid.app.LoginViewModel
import com.reported.nativeandroid.app.LoginAction
import com.reported.nativeandroid.app.RegisterAction
import com.reported.nativeandroid.app.RegisterViewModel
import com.reported.nativeandroid.screens.MessageCard
import com.reported.nativeandroid.screens.PrimaryButton
import com.reported.nativeandroid.screens.ReportedField
import com.reported.nativeandroid.screens.ReportedPasswordField
import com.reported.nativeandroid.screens.SecondaryButton
import com.reported.nativeandroid.screens.TertiaryButton
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onSkip: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SplashVideoBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PrimaryButton(text = "Login", onClick = onLogin)
                SecondaryButton(text = "Register", onClick = onRegister)
                TertiaryButton(text = "Skip", onClick = onSkip, textColor = Color.White)
            }
        }
    }
}

@Composable
private fun SplashVideoBackground() {
    val context = LocalContext.current
    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("asset:///splash.mp4")))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        update = { view ->
            view.player = player
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onSuccess: () -> Unit,
    onRegister: () -> Unit,
    onBack: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modal: Boolean = false,
    vm: LoginViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val backAction = onBack ?: onDismiss
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    state.passwordResetMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.onAction(LoginAction.PasswordResetMessageDismissed) },
            title = { Text("Check your email") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.onAction(LoginAction.PasswordResetMessageDismissed) }) {
                    Text("OK")
                }
            }
        )
    }
    fun startGoogleSignIn() {
        val activity = context.findActivity()
        if (activity == null) {
            vm.onAction(LoginAction.SocialSignInFailed("Google", "Google sign-in needs an active screen."))
            return
        }
        scope.launch {
            runCatching { signInWithGoogle(activity) }
                .onSuccess { profile -> vm.onAction(LoginAction.SocialSignInCompleted(profile, onSuccess)) }
                .onFailure { error ->
                    if (error.isSocialAuthCancellation()) {
                        vm.onAction(LoginAction.SocialSignInCancelled)
                    } else {
                        vm.onAction(LoginAction.SocialSignInFailed("Google", error.message))
                    }
                }
        }
    }
    fun startAppleSignIn() {
        runCatching { launchAppleSignIn(context) }
            .onFailure { error -> vm.onAction(LoginAction.SocialSignInFailed("Apple", error.message)) }
    }

    AuthScreenScaffold(
        title = "Login",
        onBack = backAction,
        modal = modal
    ) {
            state.error?.let { MessageCard(it) }
            SignInWithGoogleButton(onClick = ::startGoogleSignIn, enabled = !state.loading)
            SignInWithAppleButton(onClick = ::startAppleSignIn, enabled = !state.loading)
            AuthDivider()
            ReportedField("Email", state.email, onValueChange = { vm.onAction(LoginAction.EmailChanged(it)) })
            ReportedPasswordField("Password", state.password, onValueChange = { vm.onAction(LoginAction.PasswordChanged(it)) })
            PrimaryButton("Login", onClick = { vm.onAction(LoginAction.LoginPressed(onSuccess)) }, enabled = !state.loading)
            Text(
                text = "Forgot Password?",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { vm.onAction(LoginAction.ForgotPasswordPressed) }
            )
            Text(
                text = "Need an account? Register",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onRegister)
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onSuccess: () -> Unit,
    onLogin: () -> Unit,
    onBack: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modal: Boolean = false,
    vm: RegisterViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val backAction = onBack ?: onDismiss
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun startGoogleSignIn() {
        val activity = context.findActivity()
        if (activity == null) {
            vm.onAction(RegisterAction.SocialSignInFailed("Google", "Google sign-in needs an active screen."))
            return
        }
        scope.launch {
            runCatching { signInWithGoogle(activity) }
                .onSuccess { profile -> vm.onAction(RegisterAction.SocialSignInCompleted(profile, onSuccess)) }
                .onFailure { error ->
                    if (error.isSocialAuthCancellation()) {
                        vm.onAction(RegisterAction.SocialSignInCancelled)
                    } else {
                        vm.onAction(RegisterAction.SocialSignInFailed("Google", error.message))
                    }
                }
        }
    }
    fun startAppleSignIn() {
        runCatching { launchAppleSignIn(context) }
            .onFailure { error -> vm.onAction(RegisterAction.SocialSignInFailed("Apple", error.message)) }
    }

    AuthScreenScaffold(
        title = "Register",
        onBack = backAction,
        modal = modal
    ) {
            state.error?.let { MessageCard(it) }
            SignInWithGoogleButton(onClick = ::startGoogleSignIn, enabled = !state.loading)
            SignInWithAppleButton(onClick = ::startAppleSignIn, enabled = !state.loading)
            AuthDivider()
            ReportedField("First Name", state.firstName, onValueChange = { vm.onAction(RegisterAction.FieldsChanged(firstName = it)) })
            ReportedField("Last Name", state.lastName, onValueChange = { vm.onAction(RegisterAction.FieldsChanged(lastName = it)) })
            ReportedField("Phone", state.phone, onValueChange = { vm.onAction(RegisterAction.FieldsChanged(phone = it)) })
            ReportedField("Email", state.email, onValueChange = { vm.onAction(RegisterAction.FieldsChanged(email = it)) })
            ReportedPasswordField("Password", state.password, onValueChange = { vm.onAction(RegisterAction.FieldsChanged(password = it)) })
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.onAction(RegisterAction.FieldsChanged(testify = !state.testify)) },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.testify,
                    onCheckedChange = { vm.onAction(RegisterAction.FieldsChanged(testify = it)) }
                )
                Text(
                    text = "I'm willing to testify by phone if needed.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            PrimaryButton("Create Account", onClick = { vm.onAction(RegisterAction.RegisterPressed(onSuccess)) }, enabled = !state.loading)
            Text(
                text = "Already registered? Login",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onLogin)
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modal: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = if (modal) {
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        } else {
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        }
    ) {
        CenterAlignedTopAppBar(
            title = { Text(title) },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            }
        )
        Box(
            modifier = if (modal) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            },
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun AuthDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "or",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SignInWithGoogleButton(
    onClick: () -> Unit,
    enabled: Boolean
) {
    ProviderSignInButton(
        text = "Sign in with Google",
        onClick = onClick,
        enabled = enabled,
        containerColor = Color.White,
        contentColor = Color(0xFF3C4043),
        borderColor = Color(0xFFDADCE0),
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

@Composable
private fun SignInWithAppleButton(
    onClick: () -> Unit,
    enabled: Boolean
) {
    ProviderSignInButton(
        text = "Sign in with Apple",
        onClick = onClick,
        enabled = enabled,
        containerColor = Color.Black,
        contentColor = Color.White,
        borderColor = Color.Black,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_apple_mark),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(22.dp)
            )
        }
    )
}

@Composable
private fun ProviderSignInButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    icon: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.52f),
            disabledContentColor = contentColor.copy(alpha = 0.52f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Box(
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart)
            ) {
                icon()
            }
            Text(
                text = text,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
