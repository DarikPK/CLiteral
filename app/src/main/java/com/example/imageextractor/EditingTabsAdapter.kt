package com.example.imageextractor

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class EditingTabsAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ViewGalleryFragment()
            1 -> EditGalleryFragment()
            else -> throw IllegalStateException("Posición de pestaña inválida")
        }
    }
}