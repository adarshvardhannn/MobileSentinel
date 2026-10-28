package com.example.mobilesentinel.ui.scanner

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ScannerPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PortScanFragment()
            1 -> ProcessFragment()
            else -> PortScanFragment()
        }
    }
}

