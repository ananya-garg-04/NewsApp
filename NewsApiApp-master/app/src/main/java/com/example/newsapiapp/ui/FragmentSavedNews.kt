// File: FragmentSavedNews.kt
@file:Suppress("DEPRECATION") // Keep if needed for other parts, but MenuProvider avoids some deprecation

package com.example.newsapiapp.ui

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
// findFragment is usually not needed directly like this
// import androidx.fragment.app.findFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.newsapiapp.R
// Import the specific adapter
import com.example.newsapiapp.adapters.SavedArticleAdapter
// Import SavedArticle if needed directly (though handled by adapter/viewmodel)
// import com.example.newsapiapp.db.SavedArticle
import com.example.newsapiapp.mvvm.NewsDatabase
import com.example.newsapiapp.mvvm.NewsRepo
import com.example.newsapiapp.mvvm.NewsViewModel
import com.example.newsapiapp.mvvm.NewsViewModelFac

// Implement only MenuProvider, not the click listener (unless needed)
class FragmentSavedNews : Fragment(), MenuProvider {

    // Use lateinit cautiously or initialize differently
    private lateinit var viewModel: NewsViewModel
    private lateinit var newsAdapter: SavedArticleAdapter
    private lateinit var rv: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_saved_news, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Use safe cast for activity access
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Saved News"

        // Setup MenuProvider
        val menuHost: MenuHost = requireActivity()
        // Use RESUMED state for fragments usually
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // REMOVED: setHasOptionsMenu(true) - Not needed with MenuProvider

        rv = view.findViewById(R.id.rvSavedNews)

        // Setup ViewModel
        val dao = NewsDatabase.getInstance(requireActivity()).newsDao()
        val repository = NewsRepo(dao)
        val factory = NewsViewModelFac(repository, requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[NewsViewModel::class.java]

        // Initialize Adapter (data will be set via observer)
        newsAdapter = SavedArticleAdapter()
        // Setup RecyclerView before observing data
        setUpRecyclerView()

        // Observe LiveData for saved news
        // Ensure getSavedNews is the correct LiveData<List<SavedArticle>> in ViewModel
        viewModel.getSavedNews.observe(viewLifecycleOwner, Observer { savedArticles ->
            // ** FIX: Use the correct method name 'setList' **
            newsAdapter.setList(savedArticles ?: emptyList()) // Handle potential null list
            // No need to call setUpRecyclerView() again here, just update data
        })
    }

    private fun setUpRecyclerView() {
        rv.apply {
            adapter = newsAdapter
            // Set layout manager - consider Vertical if Horizontal clips text
            layoutManager = LinearLayoutManager(activity) // Changed to Vertical (adjust if needed)
        }
        // If you want item click handling here, you need to:
        // 1. Implement ItemClickListener<SavedArticle> in this Fragment
        // 2. Call newsAdapter.setItemClickListener(this) here
        // 3. Implement the onItemClicked(position, item) method
    }

    // --- MenuProvider Implementation ---

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu, menu)

        // Configure menu items specifically for this fragment
        val searchIcon = menu.findItem(R.id.searchNews)
        val savedIcon = menu.findItem(R.id.savedNewsFrag)
        val deleteIcon = menu.findItem(R.id.deleteAll)

        searchIcon?.isVisible = false // Hide search in saved news
        savedIcon?.isVisible = false  // Hide saved icon (already here)
        deleteIcon?.isVisible = true  // Show delete all option

        // REMOVED: super.onCreateOptionsMenu(menu, menuInflater) - Not needed with MenuProvider
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        // Handle delete all action
        if (menuItem.itemId == R.id.deleteAll) {
            showDeleteConfirmationDialog()
            return true // Handled
        }
        // Return false if the item is not handled by this provider
        return false
    }

    // Helper function for showing the delete dialog
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete All Saved News")
            .setMessage("Are you sure you want to delete all saved articles? This cannot be undone.")
            .setPositiveButton("Delete All") { dialog, _ ->
                viewModel.deleteAll() // Call ViewModel function to delete
                Toast.makeText(context, "All saved articles deleted", Toast.LENGTH_SHORT).show()
                // Optional: Navigate back after deletion, or the observer will just update the list to empty
                // view?.findNavController()?.navigateUp() // Or navigate to breaking news if preferred
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}