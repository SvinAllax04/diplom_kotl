package ru.vsu.cs.diplom_kotl.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.data.preferences.UserPreferencesRepository
import ru.vsu.cs.diplom_kotl.presentation.HomeViewModel
import ru.vsu.cs.diplom_kotl.ui.product.ProductDetailsActivity

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: RecommendationCardAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val subtitle = view.findViewById<TextView>(R.id.homeSubtitle)
        val recycler = view.findViewById<RecyclerView>(R.id.homeRecycler)
        adapter = RecommendationCardAdapter(onItemClick = { item ->
            startActivity(
                Intent(requireContext(), ProductDetailsActivity::class.java).apply {
                    putExtra(ProductDetailsActivity.EXTRA_ITEM_ID, item.id)
                },
            )
        })
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.rows.collect { adapter.submit(it) }
                }
                launch {
                    viewModel.personalizedActive.collect {
                        subtitle.text = homeSubtitleText()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        view?.findViewById<TextView>(R.id.homeSubtitle)?.text = homeSubtitleText()
    }

    private fun homeSubtitleText(): String {
        val prefs = UserPreferencesRepository(requireContext())
        val styleOn = prefs.isRecommendationStyleEnabled()
        val paletteOn = prefs.isRecommendationPaletteEnabled()
        return when {
            styleOn && paletteOn -> getString(R.string.home_subtitle_personalized)
            styleOn -> getString(R.string.home_subtitle_style_only)
            paletteOn -> getString(R.string.home_subtitle_palette_only)
            else -> getString(R.string.home_subtitle_catalog_only)
        }
    }
}
