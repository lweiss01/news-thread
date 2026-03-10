package com.newsthread.app.presentation.settings

import android.content.Context
import com.newsthread.app.data.repository.UserPreferencesRepository
import com.newsthread.app.domain.model.ArticleFetchPreference
import com.newsthread.app.domain.model.SyncStrategy
import com.newsthread.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    private val fetchPrefFlow = MutableStateFlow(ArticleFetchPreference.WIFI_ONLY)
    private val bgSyncFlow = MutableStateFlow(true)
    private val syncStrategyFlow = MutableStateFlow(SyncStrategy.BALANCED)
    private val meteredSyncFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        context = mock()
        userPreferencesRepository = mock()

        whenever(userPreferencesRepository.articleFetchPreference).thenReturn(fetchPrefFlow)
        whenever(userPreferencesRepository.backgroundSyncEnabled).thenReturn(bgSyncFlow)
        whenever(userPreferencesRepository.syncStrategy).thenReturn(syncStrategyFlow)
        whenever(userPreferencesRepository.meteredSyncAllowed).thenReturn(meteredSyncFlow)

        viewModel = SettingsViewModel(context, userPreferencesRepository)
    }

    @Test
    fun `initial state reflects repository values`() = runTest {
        runCurrent()
        assertEquals(ArticleFetchPreference.WIFI_ONLY, viewModel.articleFetchPreference.value)
        assertEquals(true, viewModel.backgroundSyncEnabled.value)
        assertEquals(SyncStrategy.BALANCED, viewModel.syncStrategy.value)
        assertEquals(false, viewModel.meteredSyncAllowed.value)
    }

    @Test
    fun `values update correctly when repository emits new values`() = runTest {
        // Collect flows to keep them active
        val collectJob1 = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.articleFetchPreference.collect {} }
        val collectJob2 = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.backgroundSyncEnabled.collect {} }
        val collectJob3 = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.syncStrategy.collect {} }
        val collectJob4 = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.meteredSyncAllowed.collect {} }

        fetchPrefFlow.value = ArticleFetchPreference.ALWAYS
        bgSyncFlow.value = false
        syncStrategyFlow.value = SyncStrategy.PERFORMANCE
        meteredSyncFlow.value = true

        runCurrent()

        assertEquals(ArticleFetchPreference.ALWAYS, viewModel.articleFetchPreference.value)
        assertEquals(false, viewModel.backgroundSyncEnabled.value)
        assertEquals(SyncStrategy.PERFORMANCE, viewModel.syncStrategy.value)
        assertEquals(true, viewModel.meteredSyncAllowed.value)

        collectJob1.cancel()
        collectJob2.cancel()
        collectJob3.cancel()
        collectJob4.cancel()
    }
}
