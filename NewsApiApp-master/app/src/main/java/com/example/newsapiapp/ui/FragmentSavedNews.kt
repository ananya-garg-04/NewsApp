//@file:Suppress("DEPRECATION") // Keep if needed

package com.example.newsapiapp.ui

// Add necessary imports back
import android.content.res.Configuration
import com.example.newsapiapp.ThemeHelper

// Other existing imports...
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newsapiapp.R
import com.example.newsapiapp.adapters.SavedArticleAdapter
import com.example.newsapiapp.databinding.FragmentSavedNewsBinding // Use View Binding
import com.example.newsapiapp.mvvm.NewsDatabase
import com.example.newsapiapp.mvvm.NewsRepo
import com.example.newsapiapp.mvvm.NewsViewModel
import com.example.newsapiapp.mvvm.NewsViewModelFac


class FragmentSavedNews : Fragment(), MenuProvider {

    // --- View Binding Setup ---
    private var _binding: FragmentSavedNewsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NewsViewModel
    private lateinit var newsAdapter: SavedArticleAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = "Saved News"
        setupMenu()
        setupViewModel()
        setupRecyclerView()
        observeSavedNews()

        // --- ADD THEME SWITCH LOGIC BACK for the switch in this fragment's layout ---
        setupThemeSwitch()
    }

    // --- Helper function for Menu (inflates ONLY saved_news_menu) ---
    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    // --- Helper function for ViewModel ---
    private fun setupViewModel() {
        val dao = NewsDatabase.getInstance(requireActivity()).newsDao()
        val repository = NewsRepo(dao)
        val factory = NewsViewModelFac(repository, requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[NewsViewModel::class.java]
    }

    // --- Helper function for RecyclerView ---
    private fun setupRecyclerView() {
        newsAdapter = SavedArticleAdapter()
        binding.rvSavedNews.apply {
            adapter = newsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        // Add Item Click Listener setup here if needed
    }

    // --- ADD THEME SWITCH SETUP FUNCTION BACK ---
    private fun setupThemeSwitch() {
        // Access switch via binding using the ID from the XML
        val themeSwitch = binding.switchThemeFragment // Use the ID you set in XML

        // Set initial state
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        themeSwitch.isChecked = (currentNightMode == Configuration.UI_MODE_NIGHT_YES)

        // Set listener
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val newTheme = if (isChecked) ThemeHelper.DARK_MODE else ThemeHelper.LIGHT_MODE
            ThemeHelper.saveTheme(requireContext(), newTheme)
            ThemeHelper.applyTheme(newTheme)
            // Recreate activity to apply theme changes fully
            activity?.recreate()
        }
    }

    // --- Helper function for Observing Data ---
    private fun observeSavedNews() {
        viewModel.getSavedNews.observe(viewLifecycleOwner, Observer { savedArticles ->
            newsAdapter.setList(savedArticles ?: emptyList())
        })
    }

    // --- MenuProvider Implementation (Handles ONLY delete action) ---
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.saved_news_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.deleteAll -> {
                showDeleteConfirmationDialog()
                true // Handled
            }
            else -> false // Not handled by this provider
        }
    }

    // --- Delete Confirmation Dialog ---
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete All Saved News")
            .setMessage("Are you sure you want to delete all saved articles? This cannot be undone.")
            .setPositiveButton("Delete All") { dialog, _ ->
                viewModel.deleteAll()
                Toast.makeText(context, "All saved articles deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    // --- Clean up View Binding ---
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}