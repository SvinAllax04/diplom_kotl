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

class MainShellActivity : AppCompatActivity() {

    lateinit var prefsRepository: UserPreferencesRepository
        private set

    val arViewModelFactory: ArViewModelFactory by lazy {
        ArViewModelFactory(applicationContext, prefsRepository)
    }

    private lateinit var homeFragment: HomeFragment
    private lateinit var arFragment: ArFragment
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

        if (savedInstanceState == null) {
            homeFragment = HomeFragment()
            arFragment = ArFragment()
            profileFragment = ProfileFragment()
            supportFragmentManager.commit {
                add(R.id.mainFragmentContainer, homeFragment, TAG_HOME)
                add(R.id.mainFragmentContainer, arFragment, TAG_AR)
                add(R.id.mainFragmentContainer, profileFragment, TAG_PROFILE)
                hide(arFragment)
                hide(profileFragment)
                setMaxLifecycle(arFragment, Lifecycle.State.STARTED)
                setMaxLifecycle(profileFragment, Lifecycle.State.STARTED)
            }
        } else {
            homeFragment = supportFragmentManager.findFragmentByTag(TAG_HOME) as HomeFragment
            arFragment = supportFragmentManager.findFragmentByTag(TAG_AR) as ArFragment
            profileFragment = supportFragmentManager.findFragmentByTag(TAG_PROFILE) as ProfileFragment
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.mainBottomNav)
        val startTab = intent.getStringExtra(EXTRA_START_TAB) ?: TAB_HOME
        bottomNav.selectedItemId = if (startTab == TAB_AR) R.id.nav_ar else R.id.nav_home
        if (startTab == TAB_AR) {
            showTab(arFragment)
        }
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showTab(homeFragment)
                    true
                }
                R.id.nav_ar -> {
                    showTab(arFragment)
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

    private fun showTab(tab: Fragment) {
        supportFragmentManager.commit {
            listOf(homeFragment, arFragment, profileFragment).forEach { fragment ->
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
        private const val TAG_HOME = "tag_home"
        private const val TAG_AR = "tag_ar"
        private const val TAG_PROFILE = "tag_profile"
    }
}
