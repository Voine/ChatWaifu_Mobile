package com.chatwaifu.mobile.ui.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ProviderCategory
import com.chatwaifu.chat.core.ProviderDescriptor
import com.chatwaifu.chat.core.ProviderSettingField
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.chat.CloudProviderProfile
import com.chatwaifu.mobile.data.chat.EmbeddedLocalProviderProfile
import com.chatwaifu.mobile.data.chat.LocalNetworkProviderProfile
import com.chatwaifu.mobile.data.chat.ProviderProfile

private val GlassColor = Color(0xB5162944)
private val GlassBorder = Color(0x406FA8D8)
private val Accent = Color(0xFF8CBFFF)

@Composable
fun ModelSettingsSection(
    state: ModelSettingsState,
    onTestConnection: () -> Unit,
) {
    val descriptors = ChatProviderFactory.descriptors()
    val active = ChatProviderFactory.descriptor(state.activeProvider)
    val editing = ChatProviderFactory.descriptor(state.editorProvider)

    SectionLabel(stringResource(R.string.model_settings_title))
    GlassCard {
        Text(
            stringResource(R.string.model_settings_current),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.62f),
            fontFamily = FontFamily.SansSerif,
        )
        Text(
            "${active.displayName} · ${state.profileOf(state.activeProvider).modelId.ifBlank {
                active.models.firstOrNull()?.id ?: stringResource(R.string.model_settings_default_model)
            }}",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
        )
    }

    SectionLabel(stringResource(R.string.model_settings_service_type))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProviderCategory.entries.forEach { category ->
            val selected = state.selectedCategory == category
            OutlinedButton(
                onClick = { state.selectCategory(category) },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected) Accent.copy(alpha = 0.22f) else GlassColor,
                    contentColor = Color.White,
                ),
                border = BorderStroke(1.dp, if (selected) Accent else GlassBorder),
            ) {
                Text(category.label(), fontFamily = FontFamily.SansSerif)
            }
        }
    }

    descriptors.filter { it.category == state.selectedCategory }.forEach { descriptor ->
        ProviderCard(
            descriptor = descriptor,
            selected = descriptor.id == state.editorProvider,
            onClick = { state.editProvider(descriptor.id) },
        )
    }

    GlassCard {
        Text(
            editing.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
        )
        Text(
            if (editing.implemented) editing.summary
            else stringResource(R.string.setting_provider_not_implemented),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
            fontFamily = FontFamily.SansSerif,
        )
        CapabilityBadges(editing)
        ProviderEditor(state, editing, state.profileOf())

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { state.selectActiveProvider(editing.id) },
                enabled = state.canActivate(editing.id) && editing.id != state.activeProvider,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.model_settings_use_model))
            }
            OutlinedButton(
                onClick = onTestConnection,
                enabled = editing.implemented &&
                    !editing.capabilities.embedded &&
                    state.connectionStatus != ConnectionTestStatus.TESTING,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (state.connectionStatus == ConnectionTestStatus.TESTING) {
                        stringResource(R.string.model_settings_testing)
                    } else {
                        stringResource(R.string.model_settings_test_connection)
                    }
                )
            }
        }
        ConnectionStatus(state.connectionStatus)
    }
}

@Composable
private fun ProviderCard(
    descriptor: ProviderDescriptor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(onClick = onClick),
        color = if (selected) Accent.copy(alpha = 0.18f) else GlassColor,
        contentColor = Color.White,
        border = BorderStroke(1.dp, if (selected) Accent else GlassBorder),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(descriptor.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                descriptor.summary,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = if (descriptor.implemented) 0.62f else 0.38f),
                fontFamily = FontFamily.SansSerif,
            )
        }
    }
}

@Composable
private fun ProviderEditor(
    state: ModelSettingsState,
    descriptor: ProviderDescriptor,
    profile: ProviderProfile,
) {
    when (profile) {
        is CloudProviderProfile -> RemoteEditor(
            state = state,
            descriptor = descriptor,
            endpoint = profile.endpoint,
            credential = profile.apiKey,
            modelId = profile.modelId,
            streaming = profile.streaming,
            temperature = profile.temperature,
            maxTokens = profile.maxTokens,
            onEndpoint = { state.updateProfile(profile.copy(endpoint = it)) },
            onCredential = { state.updateProfile(profile.copy(apiKey = it)) },
            onModel = { state.updateProfile(profile.copy(modelId = it)) },
            onStreaming = { state.updateProfile(profile.copy(streaming = it)) },
            onTemperature = { state.updateProfile(profile.copy(temperature = it)) },
            onMaxTokens = { state.updateProfile(profile.copy(maxTokens = it)) },
        )
        is LocalNetworkProviderProfile -> RemoteEditor(
            state = state,
            descriptor = descriptor,
            endpoint = profile.endpoint,
            credential = profile.authToken,
            modelId = profile.modelId,
            streaming = profile.streaming,
            temperature = profile.temperature,
            maxTokens = profile.maxTokens,
            timeoutSeconds = profile.timeoutSeconds,
            onEndpoint = { state.updateProfile(profile.copy(endpoint = it)) },
            onCredential = { state.updateProfile(profile.copy(authToken = it)) },
            onModel = { state.updateProfile(profile.copy(modelId = it)) },
            onStreaming = { state.updateProfile(profile.copy(streaming = it)) },
            onTemperature = { state.updateProfile(profile.copy(temperature = it)) },
            onMaxTokens = { state.updateProfile(profile.copy(maxTokens = it)) },
            onTimeout = { state.updateProfile(profile.copy(timeoutSeconds = it)) },
        )
        is EmbeddedLocalProviderProfile -> EmbeddedEditor(state, profile)
    }
}

@Composable
private fun RemoteEditor(
    state: ModelSettingsState,
    descriptor: ProviderDescriptor,
    endpoint: String,
    credential: String,
    modelId: String,
    streaming: Boolean,
    temperature: Float?,
    maxTokens: Int?,
    timeoutSeconds: Long? = null,
    onEndpoint: (String) -> Unit,
    onCredential: (String) -> Unit,
    onModel: (String) -> Unit,
    onStreaming: (Boolean) -> Unit,
    onTemperature: (Float?) -> Unit,
    onMaxTokens: (Int?) -> Unit,
    onTimeout: (Long) -> Unit = {},
) {
    val fields = descriptor.settingFields
    SectionLabel(stringResource(R.string.model_settings_connection))
    if (ProviderSettingField.ENDPOINT in fields) {
        ModelTextField(
            value = endpoint,
            onValueChange = onEndpoint,
            label = stringResource(R.string.setting_title_provider_base_url),
            placeholder = descriptor.defaultBaseUrl.orEmpty(),
        )
    }
    if (ProviderSettingField.API_KEY in fields || ProviderSettingField.AUTH_TOKEN in fields) {
        ModelTextField(
            value = credential,
            onValueChange = onCredential,
            label = stringResource(
                if (ProviderSettingField.AUTH_TOKEN in fields) {
                    R.string.model_settings_auth
                } else {
                    R.string.setting_title_provider_key
                }
            ),
            secret = true,
            secretVisible = state.keyVisible,
            onSecretVisibilityChange = { state.keyVisible = !state.keyVisible },
        )
    }
    if (ProviderSettingField.MODEL_ID in fields) {
        ModelTextField(
            value = modelId,
            onValueChange = onModel,
            label = stringResource(R.string.setting_title_provider_model),
            placeholder = descriptor.models.firstOrNull()?.id.orEmpty(),
        )
    }

    TextButton(onClick = { state.advancedExpanded = !state.advancedExpanded }) {
        Text(stringResource(R.string.model_settings_advanced))
        Icon(
            if (state.advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
        )
    }
    AnimatedVisibility(state.advancedExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ProviderSettingField.STREAMING in fields) {
                ModelSwitch(
                    stringResource(R.string.model_settings_streaming),
                    streaming,
                    onStreaming,
                )
            }
            if (ProviderSettingField.TEMPERATURE in fields &&
                descriptor.capabilities.supportsTemperature
            ) {
                ModelTextField(
                    value = temperature?.toString().orEmpty(),
                    onValueChange = { onTemperature(it.toFloatOrNull()) },
                    label = stringResource(R.string.model_settings_temperature),
                    placeholder = stringResource(R.string.model_settings_optional),
                )
            }
            if (ProviderSettingField.MAX_TOKENS in fields) {
                ModelTextField(
                    value = maxTokens?.toString().orEmpty(),
                    onValueChange = { onMaxTokens(it.toIntOrNull()) },
                    label = stringResource(R.string.model_settings_max_tokens),
                    placeholder = stringResource(R.string.model_settings_optional),
                )
            }
            if (ProviderSettingField.CONNECTION_TIMEOUT in fields && timeoutSeconds != null) {
                ModelTextField(
                    value = timeoutSeconds.toString(),
                    onValueChange = {
                        it.toLongOrNull()
                            ?.takeIf { value ->
                                value in LocalNetworkProviderProfile.MIN_TIMEOUT_SECONDS..
                                    LocalNetworkProviderProfile.MAX_TIMEOUT_SECONDS
                            }
                            ?.let(onTimeout)
                    },
                    label = stringResource(R.string.model_settings_timeout),
                )
            }
        }
    }
}

@Composable
private fun EmbeddedEditor(
    state: ModelSettingsState,
    profile: EmbeddedLocalProviderProfile,
) {
    Text(
        stringResource(R.string.model_settings_embedded_notice),
        color = Color.White.copy(alpha = 0.62f),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.SansSerif,
    )
    ModelTextField(
        value = profile.modelPath,
        onValueChange = { state.updateProfile(profile.copy(modelPath = it)) },
        label = stringResource(R.string.model_settings_model_placeholder),
    )
    ModelTextField(
        value = profile.runtimeType,
        onValueChange = { state.updateProfile(profile.copy(runtimeType = it)) },
        label = stringResource(R.string.model_settings_runtime),
        placeholder = "MNN / llama.cpp / ONNX / NCNN",
    )
    ModelTextField(
        value = profile.contextLength?.toString().orEmpty(),
        onValueChange = {
            state.updateProfile(profile.copy(contextLength = it.toIntOrNull()))
        },
        label = stringResource(R.string.model_settings_context_length),
        placeholder = stringResource(R.string.model_settings_optional),
    )
}

@Composable
private fun ModelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    secret: Boolean = false,
    secretVisible: Boolean = false,
    onSecretVisibilityChange: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = FontFamily.SansSerif) },
        placeholder = {
            Text(
                placeholder,
                color = Color.White.copy(alpha = 0.38f),
                fontFamily = FontFamily.SansSerif,
            )
        },
        visualTransformation = if (secret && !secretVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (secret) {
            {
                IconButton(onClick = onSecretVisibilityChange) {
                    Icon(
                        if (secretVisible) Icons.Outlined.VisibilityOff
                        else Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.model_settings_toggle_key),
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModelSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
        )
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun CapabilityBadges(descriptor: ProviderDescriptor) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (descriptor.capabilities.streaming) CapabilityBadge("Streaming")
        if (descriptor.capabilities.imageInput) CapabilityBadge("Vision")
        if (descriptor.capabilities.toolCalling) CapabilityBadge("Tools")
        if (descriptor.capabilities.modelDiscovery) CapabilityBadge("Discovery")
        CapabilityBadge(if (descriptor.capabilities.remote) "Remote" else "On-device")
    }
}

@Composable
private fun CapabilityBadge(label: String) {
    Surface(
        color = Accent.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.32f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.78f),
            fontFamily = FontFamily.SansSerif,
        )
    }
}

@Composable
private fun ConnectionStatus(status: ConnectionTestStatus) {
    if (status == ConnectionTestStatus.IDLE || status == ConnectionTestStatus.TESTING) return
    val text = when (status) {
        ConnectionTestStatus.SUCCESS -> R.string.model_settings_test_success
        ConnectionTestStatus.NETWORK_ERROR -> R.string.model_settings_test_network
        ConnectionTestStatus.AUTH_ERROR -> R.string.model_settings_test_auth
        ConnectionTestStatus.MODEL_NOT_FOUND -> R.string.model_settings_test_model
        ConnectionTestStatus.ENDPOINT_INCOMPATIBLE -> R.string.model_settings_test_endpoint
        ConnectionTestStatus.UNAVAILABLE -> R.string.model_settings_test_unavailable
        ConnectionTestStatus.UNKNOWN_ERROR -> R.string.model_settings_test_unknown
        ConnectionTestStatus.IDLE, ConnectionTestStatus.TESTING -> return
    }
    Text(
        stringResource(text),
        color = if (status == ConnectionTestStatus.SUCCESS) Color(0xFF9AD8B1)
        else Color(0xFFFFB4AB),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.SansSerif,
    )
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = GlassColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.SansSerif,
    )
}

@Composable
private fun ProviderCategory.label(): String = when (this) {
    ProviderCategory.CLOUD -> stringResource(R.string.model_settings_cloud)
    ProviderCategory.LOCAL_NETWORK -> stringResource(R.string.model_settings_local_network)
    ProviderCategory.EMBEDDED_LOCAL -> stringResource(R.string.model_settings_embedded)
}
