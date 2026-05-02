package ru.vsu.cs.diplom_kotl.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.data.auth.AuthManager
import ru.vsu.cs.diplom_kotl.data.auth.UserRole
import ru.vsu.cs.diplom_kotl.data.preferences.UserPreferencesRepository
import ru.vsu.cs.diplom_kotl.presentation.ArViewModelFactory
import ru.vsu.cs.diplom_kotl.ui.auth.AuthActivity
import ru.vsu.cs.diplom_kotl.ui.home.HomeFragment
import ru.vsu.cs.diplom_kotl.ui.onboarding.OnboardingActivity
import ru.vsu.cs.diplom_kotl.ui.search.SearchFragment
import ru.vsu.cs.diplom_kotl.ui.settings.SettingsFragment
import ru.vsu.cs.diplom_kotl.ui.theme.RoomPaletteUi

class MainShellActivity : AppCompatActivity() {

    lateinit var prefsRepository: UserPreferencesRepository
        private set

    val arViewModelFactory: ArViewModelFactory by lazy {
        ArViewModelFactory(applicationContext, prefsRepository)
    }

    private lateinit var recommendationsFragment: HomeFragment
    private lateinit var searchFragment: SearchFragment
    private lateinit var arFragment: ArFragment
    private lateinit var settingsFragment: SettingsFragment
    private lateinit var profileFragment: ProfileFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsRepository = UserPreferencesRepository(this)
        val authManager = AuthManager(this).also { it.ensureBuiltinAdminAccount() }
        if (authManager.currentUser() == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        val user = authManager.currentUser()!!
        if (user.role == UserRole.USER && !prefsRepository.isOnboardingComplete()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main_shell)
        RoomPaletteUi.applyIfNeeded(this)

        if (savedInstanceState == null) {
            recommendationsFragment = HomeFragment()
            searchFragment = SearchFragment()
            arFragment = ArFragment()
            settingsFragment = SettingsFragment()
            profileFragment = ProfileFragment()
            supportFragmentManager.commit {
                add(R.id.mainFragmentContainer, recommendationsFragment, TAG_RECOMMENDATIONS)
                add(R.id.mainFragmentContainer, searchFragment, TAG_SEARCH)
                add(R.id.mainFragmentContainer, arFragment, TAG_AR)
                add(R.id.mainFragmentContainer, settingsFragment, TAG_SETTINGS)
                add(R.id.mainFragmentContainer, profileFragment, TAG_PROFILE)
                hide(searchFragment)
                hide(arFragment)
                hide(settingsFragment)
                hide(profileFragment)
                setMaxLifecycle(searchFragment, Lifecycle.State.STARTED)
                setMaxLifecycle(arFragment, Lifecycle.State.STARTED)
                setMaxLifecycle(settingsFragment, Lifecycle.State.STARTED)
                setMaxLifecycle(profileFragment, Lifecycle.State.STARTED)
            }
        } else {
            recommendationsFragment =
                supportFragmentManager.findFragmentByTag(TAG_RECOMMENDATIONS) as HomeFragment
            searchFragment = supportFragmentManager.findFragmentByTag(TAG_SEARCH) as SearchFragment
            arFragment = supportFragmentManager.findFragmentByTag(TAG_AR) as ArFragment
            settingsFragment = supportFragmentManager.findFragmentByTag(TAG_SETTINGS) as SettingsFragment
            profileFragment = supportFragmentManager.findFragmentByTag(TAG_PROFILE) as ProfileFragment
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.mainBottomNav)
        val startTab = intent.getStringExtra(EXTRA_START_TAB) ?: TAB_HOME
        bottomNav.selectedItemId = tabIdFromExtra(startTab)
        showTab(fragmentForTabId(bottomNav.selectedItemId))

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_recommendations -> {
                    showTab(recommendationsFragment)
                    true
                }
                R.id.nav_search -> {
                    showTab(searchFragment)
                    true
                }
                R.id.nav_ar -> {
                    showTab(arFragment)
                    true
                }
                R.id.nav_settings -> {
                    showTab(settingsFragment)
                    true
                }
                R.id.nav_profile -> {
                    showTab(profileFragment)
                    true
                }
                else -> false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyStartTabIntent(intent)
    }

    private fun applyStartTabIntent(intent: Intent?) {
        val tab = intent?.getStringExtra(EXTRA_START_TAB) ?: return
        val bottomNav = findViewById<BottomNavigationView>(R.id.mainBottomNav) ?: return
        val id = tabIdFromExtra(tab)
        bottomNav.selectedItemId = id
        showTab(fragmentForTabId(id))
        intent?.removeExtra(EXTRA_START_TAB)
    }

    private fun tabIdFromExtra(tab: String): Int = when (tab) {
        TAB_AR -> R.id.nav_ar
        TAB_SEARCH -> R.id.nav_search
        TAB_SETTINGS -> R.id.nav_settings
        TAB_PROFILE -> R.id.nav_profile
        else -> R.id.nav_recommendations
    }

    private fun fragmentForTabId(id: Int): Fragment = when (id) {
        R.id.nav_search -> searchFragment
        R.id.nav_ar -> arFragment
        R.id.nav_settings -> settingsFragment
        R.id.nav_profile -> profileFragment
        else -> recommendationsFragment
    }

    /** Переход на вкладку «Рекомендации» (бывш. главная). */
    fun openRecommendationsTab() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.mainBottomNav)
        bottomNav.selectedItemId = R.id.nav_recommendations
        showTab(recommendationsFragment)
    }

    private fun showTab(tab: Fragment) {
        supportFragmentManager.commit {
            listOf(
                recommendationsFragment,
                searchFragment,
                arFragment,
                settingsFragment,
                profileFragment,
            ).forEach { fragment ->
                if (fragment === tab) {
                    show(fragment)
                    setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
                } else {
                    hide(fragment)
                    setMaxLifecycle(fragment, Lifecycle.State.STARTED)
                }
            }
        }
    }

    companion object {
        const val EXTRA_START_TAB = "extra_start_tab"
        const val EXTRA_AR_ITEM_ID = "extra_ar_item_id"
        const val TAB_HOME = "home"
        const val TAB_AR = "ar"
        const val TAB_SEARCH = "search"
        const val TAB_SETTINGS = "settings"
        const val TAB_PROFILE = "profile"
        private const val TAG_RECOMMENDATIONS = "tag_recommendations"
        private const val TAG_SEARCH = "tag_search"
        private const val TAG_AR = "tag_ar"
        private const val TAG_SETTINGS = "tag_settings"
        private const val TAG_PROFILE = "tag_profile"
    }
}
