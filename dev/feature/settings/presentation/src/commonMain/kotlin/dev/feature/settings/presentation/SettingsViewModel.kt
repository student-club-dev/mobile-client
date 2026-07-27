package dev.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.domain.model.Region
import dev.core.domain.repository.DiscountRepository
import dev.core.domain.repository.RegionRepository
import dev.feature.settings.domain.model.ThemeMode
import dev.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pushEnabled: Boolean = true,
    val emailEnabled: Boolean = false,
    /** Feed qaysi viloyat bo'yicha filtrlanadi; `null` — butun O'zbekiston. */
    val region: Region? = null,
)

/** Viloyat tanlash oynasining holati. */
data class RegionPickerUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val regions: List<Region> = emptyList(),
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val regionRepository: RegionRepository,
    private val discountRepository: DiscountRepository,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        settings.observeThemeMode(),
        settings.observeFlag(SettingsRepository.KEY_NOTIF_PUSH, default = true),
        settings.observeFlag(SettingsRepository.KEY_NOTIF_EMAIL, default = false),
        regionRepository.observeSelected(),
    ) { theme, push, email, region ->
        SettingsUiState(themeMode = theme, pushEnabled = push, emailEnabled = email, region = region)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    private val _regionPicker = MutableStateFlow(RegionPickerUiState())
    val regionPicker: StateFlow<RegionPickerUiState> = _regionPicker.asStateFlow()

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setPush(enabled: Boolean) { viewModelScope.launch { settings.setFlag(SettingsRepository.KEY_NOTIF_PUSH, enabled) } }
    fun setEmail(enabled: Boolean) { viewModelScope.launch { settings.setFlag(SettingsRepository.KEY_NOTIF_EMAIL, enabled) } }

    /** Oyna ochilganda — viloyatlar ro'yxati (bir marta tortiladi). */
    fun loadRegions() {
        if (_regionPicker.value.regions.isNotEmpty() || _regionPicker.value.loading) return
        _regionPicker.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = regionRepository.regions()) {
                is Resource.Success -> _regionPicker.update { it.copy(loading = false, regions = res.data) }
                is Resource.Error -> _regionPicker.update { it.copy(loading = false, error = res.message) }
                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Viloyatni saqlaydi va feed'ni DARHOL qayta tortadi — aks holda ekranlarda eski
     * viloyatning e'lonlari qolib ketardi. [region] `null` — filtrni olib tashlash.
     */
    fun selectRegion(region: Region?) {
        viewModelScope.launch {
            regionRepository.select(region)
            discountRepository.refresh()
        }
    }
}
