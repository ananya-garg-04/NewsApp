// File: FragmentBreakingNews.kt
package com.example.newsapiapp.ui

// Import the generic ItemClickListener
import com.example.newsapiapp.adapters.ItemClickListener

// ** ADD IMPORT FOR News data class **
import com.example.newsapiapp.db.News
// Import the network Article data class
import com.example.newsapiapp.db.Article

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer // Ensure androidx.lifecycle.Observer is imported
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.newsapiapp.R
import com.example.newsapiapp.adapters.ArticleAdapter
import com.example.newsapiapp.mvvm.NewsDatabase
import com.example.newsapiapp.mvvm.NewsRepo
import com.example.newsapiapp.mvvm.NewsViewModel
import com.example.newsapiapp.mvvm.NewsViewModelFac
import com.example.newsapiapp.wrapper.Resource // Ensure Resource is imported
import de.hdodenhof.circleimageview.CircleImageView

// Implement the CORRECT generic interface ItemClickListener<Article>
class FragmentBreakingNews : Fragment(), ItemClickListener<Article>, MenuProvider {

    private lateinit var viewModel: NewsViewModel
    private lateinit var newsAdapter: ArticleAdapter
    private lateinit var rv: RecyclerView
    private lateinit var pb: ProgressBar
    private var fullArticleList = listOf<Article>() // Store full list for filtering

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_breaking_news, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = "Breaking News"

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // Initialize Views
        rv = view.findViewById(R.id.rvBreakingNews)
        pb = view.findViewById(R.id.paginationProgressBar)
        val sportCat: CircleImageView = view.findViewById(R.id.sportsImage)
        val techCat: CircleImageView = view.findViewById(R.id.techImage)
        val breakingImage: CircleImageView = view.findViewById(R.id.breakingImage)
        val businessCat: CircleImageView = view.findViewById(R.id.businessImage)
        val noWifi: ImageView = view.findViewById(R.id.noWifi)
        val noWifiText: TextView = view.findViewById(R.id.noWifiText)

        // Setup ViewModel
        // ** FIX: Call newsDao() as a function **
        val dao = NewsDatabase.getInstance(requireActivity()).newsDao()
        val repository = NewsRepo(dao)
        // Ensure NewsViewModelFac and NewsRepo constructors match dependencies
        val factory = NewsViewModelFac(repository, requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[NewsViewModel::class.java]

        setUpRecyclerView()

        if (isNetworkAvailable()) {
            noWifi.visibility = View.GONE
            noWifiText.visibility = View.GONE
            loadBreakingNews() // Observe breaking news initially
        } else {
            noWifi.visibility = View.VISIBLE
            noWifiText.visibility = View.VISIBLE
            hideProgressBar()
            Toast.makeText(context, "No Internet Connection", Toast.LENGTH_LONG).show()
        }

        val catListener = View.OnClickListener { clickedView ->
            if (!isNetworkAvailable()) {
                Toast.makeText(context, "No Internet Connection", Toast.LENGTH_LONG).show()
                return@OnClickListener
            }
            when (clickedView.id) {
                R.id.sportsImage -> {
                    (activity as? AppCompatActivity)?.supportActionBar?.title = "Sports"
                    viewModel.getCategory("sports")
                    observeCategoryNews()
                }
                R.id.techImage -> {
                    (activity as? AppCompatActivity)?.supportActionBar?.title = "Tech"
                    viewModel.getCategory("technology")
                    observeCategoryNews()
                }
                R.id.breakingImage -> {
                    (activity as? AppCompatActivity)?.supportActionBar?.title = "Breaking News"
                    loadBreakingNews() // Re-observe breaking news
                }
                R.id.businessImage -> {
                    (activity as? AppCompatActivity)?.supportActionBar?.title = "Business"
                    viewModel.getCategory("business")
                    observeCategoryNews()
                }
            }
        }

        techCat.setOnClickListener(catListener)
        breakingImage.setOnClickListener(catListener)
        businessCat.setOnClickListener(catListener)
        sportCat.setOnClickListener(catListener)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    // Observe category news LiveData
    // ** Ensure News is imported and LiveData in ViewModel is Resource<News> **
    private fun observeCategoryNews() {
        viewModel.categoryNews.observe(viewLifecycleOwner, Observer { response ->
            // Type inference for 'response' should work now
            handleResourceResponse(response)
        })
    }

    // Observe breaking news LiveData
    // ** Ensure News is imported and LiveData in ViewModel is Resource<News> **
    private fun loadBreakingNews() {
        viewModel.breakingNews.observe(viewLifecycleOwner, Observer { response ->
            // Type inference for 'response' should work now
            handleResourceResponse(response)
        })
    }

    // Common handler for news responses
    // ** Parameter type Resource<News> requires News to be imported **
    private fun handleResourceResponse(response: Resource<News>) {
        when (response) {
            is Resource.Success -> {
                hideProgressBar()
                response.data?.let { newsResponse -> // newsResponse inferred as News
                    val articles = newsResponse.articles ?: emptyList()
                    fullArticleList = articles
                    newsAdapter.setList(articles) // Use correct method name
                    if (articles.isNotEmpty()) rv.scrollToPosition(0)
                } ?: run {
                    Log.e("FragmentBreakingNews", "Success response data is null")
                    newsAdapter.setList(emptyList())
                    fullArticleList = emptyList()
                }
            }
            is Resource.Error -> {
                hideProgressBar()
                val errorMessage = response.message ?: "An unknown error occurred"
                Log.e("FragmentBreakingNews", "Error loading news: $errorMessage")
                Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_LONG).show()
                newsAdapter.setList(emptyList())
                fullArticleList = emptyList()
            }
            is Resource.Loading -> {
                showProgressBar()
            }
        }
    }

    private fun showProgressBar() {
        pb.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        pb.visibility = View.INVISIBLE
    }

    private fun setUpRecyclerView() {
        newsAdapter = ArticleAdapter()
        // 'this' implements ItemClickListener<Article>, so types match
        newsAdapter.setItemClickListener(this)
        rv.apply {
            adapter = newsAdapter
            layoutManager = LinearLayoutManager(activity)
        }
    }

    // Implementation of ItemClickListener<Article>
    override fun onItemClicked(position: Int, item: Article) { // Parameter 'item' is Article
        try {
            val action = FragmentBreakingNewsDirections.actionFragmentBreakingNewsToFragmentArticle(item)
            view?.findNavController()?.navigate(action)
        } catch (e: Exception) {
            Log.e("FragmentBreakingNews", "Navigation failed", e)
            Toast.makeText(context, "Could not open article details", Toast.LENGTH_SHORT).show()
        }
    }

    // --- MenuProvider Implementation ---

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu, menu)

        val deleteIcon = menu.findItem(R.id.deleteAll)
        deleteIcon.isVisible = false

        val savedIcon = menu.findItem(R.id.savedNewsFrag)
        // savedIcon?.isVisible = true // Visibility managed by expand listener

        val menuItem = menu.findItem(R.id.searchNews)
        val searchView = menuItem.actionView as? SearchView

        searchView?.queryHint = "Search News"

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                newFilterItems(query)
                searchView.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                newFilterItems(newText)
                return true
            }
        })

        menuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                savedIcon?.isVisible = false
                deleteIcon?.isVisible = false // Also hide delete when searching
                return true
            }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                savedIcon?.isVisible = true
                deleteIcon?.isVisible = false // Keep delete hidden regardless
                // Clear filter when search is closed? Optional.
                // newFilterItems(null)
                return true
            }
        })
    }

    private fun newFilterItems(query: String?) {
        val filteredList = if (query.isNullOrBlank()) {
            fullArticleList
        } else {
            fullArticleList.filter { article ->
                (article.title?.contains(query, ignoreCase = true) == true) ||
                        (article.description?.contains(query, ignoreCase = true) == true)
            }
        }
        // Ensure filteredList method exists in adapter and accepts List<Article>
        newsAdapter.filteredList(ArrayList(filteredList))
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.savedNewsFrag) {
            try {
                // Ensure the action ID is correct in your nav_graph.xml
                view?.findNavController()?.navigate(R.id.action_fragmentBreakingNews_to_fragmentSavedNews)
            } catch (e: Exception) {
                Log.e("FragmentBreakingNews", "Navigation to SavedNews failed", e)
                Toast.makeText(context, "Could not open saved news", Toast.LENGTH_SHORT).show()
            }
            return true // Handled
        }
        return false // Not handled
    }
}