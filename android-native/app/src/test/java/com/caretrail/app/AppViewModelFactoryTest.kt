package com.caretrail.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression for the cold-start crash delivered in build 327f837: MainActivity obtains
 * AppViewModel through `by viewModels()`, which delegates to the AndroidViewModelProvider
 * factory. That factory reflectively requires an AppViewModel(Application) constructor,
 * and a Kotlin default parameter alone does not generate one, so the apiBase test seam
 * (added without @JvmOverloads) made every real-device launch die on the first frame with
 * RuntimeException "Cannot create an instance of AppViewModel" while the unit tests, which
 * construct the class directly, stayed green.
 *
 * This test drives the SAME factory entry point the activity uses, with the application
 * supplied through CreationExtras exactly as the runtime does.
 */
@RunWith(RobolectricTestRunner::class)
class AppViewModelFactoryTest {
    @Test
    fun androidViewModelProviderFactoryCreatesAppViewModel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val extras = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, app)
        }
        val vm = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            .create(AppViewModel::class.java, extras)
        assertNotNull(vm)
        assertNotNull(vm.journey)
    }
}
