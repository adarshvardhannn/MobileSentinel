package com.example.mobilesentinel.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.mobilesentinel.MainActivity
import com.example.mobilesentinel.R
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsLayout: LinearLayout
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: TextView
    private val dots = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.vpOnboarding)
        dotsLayout = findViewById(R.id.dotsLayout)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)

        viewPager.adapter = OnboardingAdapter(this)
        setupDots(3)
        updateDots(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                btnNext.text = if (position == 2) getString(R.string.onboarding_get_started) else getString(R.string.onboarding_next)
                btnSkip.visibility = if (position == 2) View.GONE else View.VISIBLE
            }
        })

        btnNext.setOnClickListener {
            if (viewPager.currentItem < 2) {
                viewPager.currentItem += 1
            } else {
                completeOnboarding()
            }
        }

        btnSkip.setOnClickListener { completeOnboarding() }
    }

    private fun setupDots(count: Int) {
        dotsLayout.removeAllViews()
        dots.clear()
        for (i in 0 until count) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(24, 24).also { it.setMargins(8, 0, 8, 0) }
                setBackgroundResource(R.drawable.dot_inactive)
            }
            dots.add(dot)
            dotsLayout.addView(dot)
        }
    }

    private fun updateDots(position: Int) {
        dots.forEachIndexed { i, dot ->
            dot.setBackgroundResource(if (i == position) R.drawable.dot_active else R.drawable.dot_inactive)
        }
    }

    private fun completeOnboarding() {
        getSharedPreferences("sentinel_settings", MODE_PRIVATE)
            .edit().putBoolean("onboarding_complete", true).apply()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    // --- Fragments ---
    class OnboardingAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> WelcomeFragment()
                1 -> FeaturesFragment()
                2 -> PermissionsFragment()
                else -> WelcomeFragment()
            }
        }
    }

    class WelcomeFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, b: Bundle?): View {
            return inflater.inflate(R.layout.onboarding_1, container, false)
        }
    }

    class FeaturesFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, b: Bundle?): View {
            return inflater.inflate(R.layout.onboarding_2, container, false)
        }
    }

    class PermissionsFragment : Fragment() {
        private val notifPermLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* result handled via UI update */ }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, b: Bundle?): View {
            return inflater.inflate(R.layout.onboarding_3, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val btnGrant = view.findViewById<MaterialButton>(R.id.btnGrantPermissions)
            val btnUsageStats = view.findViewById<MaterialButton>(R.id.btnUsageStats)
            val ivNotifCheck = view.findViewById<ImageView>(R.id.ivNotifCheck)

            updateNotifStatus(ivNotifCheck)

            btnGrant.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                updateNotifStatus(ivNotifCheck)
            }

            btnUsageStats.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }

        override fun onResume() {
            super.onResume()
            view?.let { updateNotifStatus(it.findViewById(R.id.ivNotifCheck)) }
        }

        private fun updateNotifStatus(iv: ImageView?) {
            val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
            iv?.setImageResource(if (granted) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background)
        }
    }
}

