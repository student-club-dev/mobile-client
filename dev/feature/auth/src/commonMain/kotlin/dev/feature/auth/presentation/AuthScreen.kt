package dev.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.feature.auth.social.SocialAuthController
import dev.feature.auth.social.rememberSocialAuthController
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val socialAuth = rememberSocialAuthController()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Student Clubs",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        state.user?.let { user ->
            Text(
                "Xush kelibsiz, ${user.fullName}",
                modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }

        TabRow(
            selectedTabIndex = state.mode.ordinal,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Tab(
                selected = state.mode == AuthMode.EMAIL,
                onClick = { viewModel.onModeChange(AuthMode.EMAIL) },
                text = { Text("Email") },
            )
            Tab(
                selected = state.mode == AuthMode.PHONE,
                onClick = { viewModel.onModeChange(AuthMode.PHONE) },
                text = { Text("Telefon") },
            )
        }

        when (state.mode) {
            AuthMode.EMAIL -> EmailForm(state, viewModel)
            AuthMode.PHONE -> PhoneForm(state, viewModel, socialAuth)
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        DividerWithText("yoki")

        OutlinedButton(
            onClick = { viewModel.signInWithGoogle(socialAuth) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Google bilan davom etish")
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun EmailForm(state: AuthUiState, viewModel: AuthViewModel) {
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("Parol") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(
        onClick = viewModel::login,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Text("Kirish")
    }
}

@Composable
private fun PhoneForm(
    state: AuthUiState,
    viewModel: AuthViewModel,
    socialAuth: SocialAuthController,
) {
    OutlinedTextField(
        value = state.phoneNumber,
        onValueChange = viewModel::onPhoneChange,
        label = { Text("Telefon (+998...)") },
        singleLine = true,
        enabled = !state.otpSent,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )

    if (!state.otpSent) {
        Button(
            onClick = { viewModel.sendOtp(socialAuth) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Kod yuborish")
        }
    } else {
        OutlinedTextField(
            value = state.otpCode,
            onValueChange = viewModel::onOtpChange,
            label = { Text("SMS kod") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = { viewModel.confirmOtp(socialAuth) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Tasdiqlash")
        }
    }
}

@Composable
private fun DividerWithText(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(modifier = Modifier.width(200.dp))
        Text(text, modifier = Modifier.padding(top = 8.dp))
    }
}
