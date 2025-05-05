package com.example.newsapiapp.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.newsapiapp.R
import com.example.newsapiapp.adapters.ArticleAdapter
import com.example.newsapiapp.adapters.ItemClickListener
import com.example.newsapiapp.db.Article
import com.example.newsapiapp.db.News
import com.example.newsapiapp.mvvm.NewsDatabase
import com.example.newsapiapp.mvvm.NewsRepo
import com.example.newsapiapp.mvvm.NewsViewModel
import com.example.newsapiapp.mvvm.NewsViewModelFac
import com.example.newsapiapp.wrapper.Resource
import de.hdodenhof.circleimageview.CircleImageView
import java.util.Locale

class FragmentBreakingNews : Fragment(), ItemClickListener<Article>, MenuProvider {


    private val TAG_FRAGMENT = "FragmentBreakingNews"
    private val TAG_SPEECH = "SpeechRecognition"


    private lateinit var viewModel: NewsViewModel
    private lateinit var newsAdapter: ArticleAdapter
    private var fullArticleList = listOf<Article>() // Cache for filtering

    // --- Views ---
    private lateinit var rv: RecyclerView
    private lateinit var pb: ProgressBar

    private var searchView: SearchView? = null
    private lateinit var noWifiImage: ImageView
    private lateinit var noWifiText: TextView

    // --- Speech Recognition ---
    private var speechRecognizer: SpeechRecognizer? = null
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG_SPEECH, "RECORD_AUDIO permission granted by user.")
                startVoiceRecognition()
            } else {
                Log.w(TAG_SPEECH, "RECORD_AUDIO permission denied by user.")
                Toast.makeText(context, "Permission denied. Voice search requires microphone access.", Toast.LENGTH_LONG).show()
            }
        }

    private val recognizerIntent: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now to search news...") // User prompt
        }
    }



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG_FRAGMENT, "onCreateView")

        return inflater.inflate(R.layout.fragment_breaking_news, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG_FRAGMENT, "onViewCreated")


        (activity as? AppCompatActivity)?.supportActionBar?.title = "Breaking News"
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)


        rv = view.findViewById(R.id.rvBreakingNews)
        pb = view.findViewById(R.id.paginationProgressBar)
        noWifiImage = view.findViewById(R.id.noWifi)
        noWifiText = view.findViewById(R.id.noWifiText)
        // No need to find micButton ImageView anymore

        // --- Setup ViewModel ---
        val dao = NewsDatabase.getInstance(requireActivity()).newsDao()
        val repository = NewsRepo(dao)
        val factory = NewsViewModelFac(repository, requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[NewsViewModel::class.java]


        setUpRecyclerView()



        // --- Initial Data Load (Check Network First) ---
        if (isNetworkAvailable()) {
            showConnectedState()
            loadBreakingNews() // Load initial breaking news
        } else {
            showDisconnectedState()
        }


        setupCategoryClicks(view)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG_FRAGMENT, "onDestroyView")

        cleanupSpeechRecognizer()
        // Avoid memory leaks with RecyclerView adapter
        rv.adapter = null

        searchView?.setOnQueryTextListener(null)
        searchView?.setOnCloseListener(null)
        searchView?.setOnQueryTextFocusChangeListener(null)
        searchView = null
    }

    // --- Network and UI State ---

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val available = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) // Check if connection is actually working

        Log.d(TAG_FRAGMENT, "Network Available: $available")
        return available
    }

    private fun showConnectedState() {
        noWifiImage.visibility = View.GONE
        noWifiText.visibility = View.GONE
    }

    private fun showDisconnectedState() {
        noWifiImage.visibility = View.VISIBLE
        noWifiText.visibility = View.VISIBLE
        Toast.makeText(context, "No Internet Connection", Toast.LENGTH_LONG).show()
    }

    // --- Data Loading and Observation ---

    private fun loadBreakingNews() {
        Log.d(TAG_FRAGMENT, "Attempting to load Breaking News")

        viewModel.getBreakingNews("us")
        observeBreakingNews() // Start observing the LiveData for breaking news
    }

    private fun observeBreakingNews() {
        viewModel.breakingNews.removeObservers(viewLifecycleOwner)
        viewModel.breakingNews.observe(viewLifecycleOwner, Observer { response ->
            Log.d(TAG_FRAGMENT, "Observed breakingNews update: ${response::class.java.simpleName}")
            handleResourceResponse(response)
        })
    }

    private fun observeCategoryNews() {
        viewModel.categoryNews.removeObservers(viewLifecycleOwner)
        viewModel.categoryNews.observe(viewLifecycleOwner, Observer { response ->
            Log.d(TAG_FRAGMENT, "Observed categoryNews update: ${response::class.java.simpleName}")
            handleResourceResponse(response)
        })
    }

    private fun handleResourceResponse(response: Resource<News>) {
        when (response) {
            is Resource.Success -> {
                hideProgressBar()
                // Filter out potential null articles from the API response
                val articles = response.data?.articles?.filterNotNull() ?: emptyList()
                Log.d(TAG_FRAGMENT, "Success - Received ${articles.size} articles")
                fullArticleList = articles // Update the cache for search/filtering
                // Re-apply any existing filter when new data arrives
                filterArticles(searchView?.query?.toString() ?: "")
                if (articles.isNotEmpty() && searchView?.query.isNullOrBlank()) {
                    rv.scrollToPosition(0) // Scroll to top for new data if no filter active
                }
            }
            is Resource.Error -> {
                hideProgressBar()
                val errorMsg = response.message ?: "An unknown error occurred"
                Log.e(TAG_FRAGMENT, "Error loading news: $errorMsg")
                Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
            }
            is Resource.Loading -> {
                Log.d(TAG_FRAGMENT, "Loading news...")
                showProgressBar()
            }
        }
    }

    // --- RecyclerView Setup ---

    private fun setUpRecyclerView() {
        // Check if adapter is already initialized (e.g., during config change)
        if (!::newsAdapter.isInitialized) {
            newsAdapter = ArticleAdapter() // Instantiate your adapter only if needed
            newsAdapter.setItemClickListener(this) // Set the click listener
        }
        rv.apply {
            // Set adapter only if it's not already set or has changed
            if(adapter == null || adapter != newsAdapter) {
                adapter = newsAdapter
            }
            // Set layout manager only if not already set
            if(layoutManager == null) {
                layoutManager = LinearLayoutManager(activity) // Use LinearLayoutManager
            }
            setHasFixedSize(true) // Optimization if item sizes don't change
        }
        Log.d(TAG_FRAGMENT, "RecyclerView setup complete.")
    }

    override fun onItemClicked(position: Int, item: Article) {
        Log.d(TAG_FRAGMENT, "Article clicked at position $position: ${item.title}")
        try {
            // Use Safe Args to pass the Article object
            val action = FragmentBreakingNewsDirections.actionFragmentBreakingNewsToFragmentArticle(item)
            // Use null-safe navigation
            view?.findNavController()?.navigate(action)
        } catch (e: Exception) {
            // Catch potential navigation errors (e.g., NavController not found, illegal state)
            Log.e(TAG_FRAGMENT, "Navigation failed for article: ${item.title}", e)
            Toast.makeText(context, "Could not open article details.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Category Click Handling ---

    private fun setupCategoryClicks(view: View) {
        val sportCat: CircleImageView = view.findViewById(R.id.sportsImage)
        val techCat: CircleImageView = view.findViewById(R.id.techImage)
        val breakingImage: CircleImageView = view.findViewById(R.id.breakingImage) // This likely reloads breaking news
        val businessCat: CircleImageView = view.findViewById(R.id.businessImage)

        val catListener = View.OnClickListener { clickedView ->
            if (!isNetworkAvailable()) {
                showDisconnectedState() // Show no connection message/UI
                return@OnClickListener // Don't proceed if offline
            }
            showConnectedState() // Ensure connected UI is shown

            var category: String? = null
            var title: String? = null

            when (clickedView.id) {
                R.id.sportsImage -> { category = "sports"; title = "Sports News" }
                R.id.techImage -> { category = "technology"; title = "Technology News" }
                R.id.businessImage -> { category = "business"; title = "Business News" }
                R.id.breakingImage -> {
                    // Special case: Reload breaking news
                    Log.d(TAG_FRAGMENT, "Category Clicked: Breaking News")
                    (activity as? AppCompatActivity)?.supportActionBar?.title = "Breaking News"
                    loadBreakingNews() // Reload general breaking news
                    return@OnClickListener // Exit listener early
                }
            }

            if (category != null && title != null) {
                Log.d(TAG_FRAGMENT, "Category Clicked: $title ($category)")
                (activity as? AppCompatActivity)?.supportActionBar?.title = title
                viewModel.getCategory(category) // Fetch news for the selected category
                observeCategoryNews() // Start observing the category news LiveData
            }
        }

        sportCat.setOnClickListener(catListener)
        techCat.setOnClickListener(catListener)
        breakingImage.setOnClickListener(catListener)
        businessCat.setOnClickListener(catListener)
    }

    // --- Progress Bar ---

    private fun showProgressBar() {
        pb.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        pb.visibility = View.GONE
    }

    // --- Speech Recognition Logic ---

    private fun checkAndStartVoiceRecognition() {
        Log.d(TAG_SPEECH, "checkAndStartVoiceRecognition called")

        // 1. Check if Recognizer is available on the device
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Log.e(TAG_SPEECH, "Speech Recognition Service not available on this device.")
            Toast.makeText(context, "Speech recognition not available on this device.", Toast.LENGTH_LONG).show()
            return // Exit if not available
        }
        Log.d(TAG_SPEECH, "Speech Recognizer Service is available.")

        // 2. Check for RECORD_AUDIO permission
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted
                Log.d(TAG_SPEECH, "RECORD_AUDIO permission is already granted.")
                startVoiceRecognition() // Proceed to start listening
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Explain why the permission is needed (e.g., in a dialog) then request
                Log.i(TAG_SPEECH, "Showing rationale for RECORD_AUDIO permission.")
                // You should ideally show a dialog here explaining why you need the mic
                Toast.makeText(context, "Microphone access is needed for voice search.", Toast.LENGTH_LONG).show()
                // Request the permission after showing rationale (or via dialog button)
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                // Directly request the permission if it hasn't been requested before or denied permanently
                Log.i(TAG_SPEECH, "Requesting RECORD_AUDIO permission.")
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startVoiceRecognition() {
        Log.d(TAG_SPEECH, "Attempting to start voice recognition...")

        // Ensure any previous instance is properly stopped and destroyed
        cleanupSpeechRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            if (speechRecognizer == null) {
                Log.e(TAG_SPEECH, "SpeechRecognizer.createSpeechRecognizer returned null!")
                Toast.makeText(context, "Could not create speech recognizer component.", Toast.LENGTH_SHORT).show()
                return
            }

            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            Log.d(TAG_SPEECH, "Starting listening with intent...")
            speechRecognizer?.startListening(recognizerIntent) // Start listening for speech

        } catch (e: Exception) {
            Log.e(TAG_SPEECH, "Exception occurred when starting voice recognition", e)
            Toast.makeText(context, "Error initializing speech recognition.", Toast.LENGTH_SHORT).show()
            cleanupSpeechRecognizer() // Clean up if initialization fails
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG_SPEECH, "onReadyForSpeech: Ready.")
                Toast.makeText(context, "Listening...", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG_SPEECH, "onBeginningOfSpeech.")
            }

            override fun onRmsChanged(rmsdB: Float) { /* For visualizer maybe */ }

            override fun onBufferReceived(buffer: ByteArray?) { /* Log.d(TAG_SPEECH, "onBufferReceived.") */ }

            override fun onEndOfSpeech() {
                Log.d(TAG_SPEECH, "onEndOfSpeech.")
            }

            override fun onError(error: Int) {
                val errorMsg = getErrorText(error)
                Log.e(TAG_SPEECH, "onError: $error - $errorMsg")
                Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_SHORT).show()
                cleanupSpeechRecognizer() // Important: Clean up on error
            }

            override fun onResults(results: Bundle?) {
                Log.d(TAG_SPEECH, "--- onResults START ---")
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()?.trim() // Get the most likely result and trim whitespace
                Log.d(TAG_SPEECH, "onResults: Extracted spokenText = '$spokenText'")

                if (!spokenText.isNullOrBlank()) {
                    // Use post to ensure execution on the main thread and after current processing
                    view?.post {
                        Log.d(TAG_SPEECH, "onResults (post): Applying query '$spokenText' to SearchView and filtering.")
                        // 1. Update the SearchView text visually
                        searchView?.setQuery(spokenText, false) // false = don't submit automatically
                        // 2. Expand the search view
                        searchView?.isIconified = false

                        filterArticles(spokenText)
                    }
                } else {
                    Log.w(TAG_SPEECH, "onResults: Received null or empty text.")
                    Toast.makeText(context, "Could not recognize speech, please try again.", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG_SPEECH, "--- onResults END ---")
                cleanupSpeechRecognizer() // Important: Clean up after getting results or errors
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Provides intermediate results as the user speaks (optional)
                val partialText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                Log.d(TAG_SPEECH, "onPartialResults: $partialText")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG_SPEECH, "onEvent: $eventType")
            }
        }
    }

    private fun cleanupSpeechRecognizer() {
        if (speechRecognizer != null) {
            try {
                Log.d(TAG_SPEECH, "Cleaning up SpeechRecognizer instance.")
                speechRecognizer?.stopListening() // Stop listening if active
                speechRecognizer?.cancel()      // Cancel any ongoing recognition
                speechRecognizer?.destroy()     // Release resources
            } catch (e: Exception) {
                Log.e(TAG_SPEECH, "Exception during SpeechRecognizer cleanup", e)
            } finally {
                speechRecognizer = null // Set to null regardless of exceptions
            }
        }
    }

    // Helper to get user-friendly error messages
    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions (Check Manifest/Runtime)"
            SpeechRecognizer.ERROR_NETWORK -> "Network error (Check internet)"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match - Try speaking clearer"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy - Try again"
            SpeechRecognizer.ERROR_SERVER -> "Server error - Issue with Google voice services?"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input - Did you speak?"
            else -> "Unknown speech recognition error ($errorCode)"
        }
    }

    // --- MenuProvider Implementation (Handles Toolbar Menu Items) ---

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        Log.d(TAG_FRAGMENT, "onCreateMenu called by MenuProvider")
        // Inflate the menu resource (which should NOW contain the voice search item)
        menuInflater.inflate(R.menu.menu, menu)

        // Find the search item and configure the SearchView
        val searchItem = menu.findItem(R.id.searchNews)
        searchView = searchItem?.actionView as? SearchView

        if (searchView == null) {
            Log.e(TAG_FRAGMENT, "Could not find SearchView in menu!")
            // Disable voice search if search view isn't found? Seems reasonable.
            menu.findItem(R.id.action_voice_search)?.isVisible = false
            return
        }
        Log.d(TAG_FRAGMENT,"SearchView configured.")

        searchView?.queryHint = "Search News..."

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // Called when user presses search button on keyboard or submits query
            override fun onQueryTextSubmit(query: String?): Boolean {
                val trimmedQuery = query?.trim() ?: ""
                Log.d(TAG_FRAGMENT, "SearchView - onQueryTextSubmit: '$trimmedQuery'")
                searchView?.clearFocus() // Hide keyboard
                filterArticles(trimmedQuery) // Filter the list based on the query
                return true // Indicate query was handled
            }

            // Called whenever the text in the SearchView changes
            override fun onQueryTextChange(newText: String?): Boolean {

                return true // Indicate change was handled (even if doing nothing)
            }
        })

        // Restore full list when search view is closed (e.g., back button or 'X')
        searchView?.setOnCloseListener {
            Log.d(TAG_FRAGMENT, "SearchView closed.")
            // Don't clear focus here, allow default close to iconify
            filterArticles("") // Restore original list by filtering with empty string
            false
        }

        searchView?.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (!hasFocus && searchView?.query?.toString().isNullOrBlank()) {
                Log.d(TAG_FRAGMENT, "SearchView focus lost and query empty, restoring list.")
                filterArticles("")
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        Log.d(TAG_FRAGMENT, "onMenuItemSelected: ${menuItem.title} (ID: ${menuItem.itemId})")
        // Handle clicks on items in the toolbar menu
        return when (menuItem.itemId) {
            // --- HANDLE VOICE SEARCH CLICK HERE ---
            R.id.action_voice_search -> {
                Log.d(TAG_FRAGMENT, "Voice Search menu item selected!")
                // Call the permission check and recognition logic
                checkAndStartVoiceRecognition()
                true // Indicate the item selection was handled
            }

            R.id.savedNewsFrag -> {
                Log.d(TAG_FRAGMENT, "Saved News menu item selected.")
                // TODO: Implement navigation to Saved News Fragment using NavController

                Toast.makeText(context, "Navigate to Saved News (Not Implemented)", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.deleteAll -> {
                Log.d(TAG_FRAGMENT, "Delete All menu item selected.")
                // TODO: Implement delete logic (e.g., show confirmation dialog, call ViewModel to delete all)
                Toast.makeText(context, "Delete All Clicked (Not Implemented)", Toast.LENGTH_SHORT).show()
                true
            }

            else -> {
                Log.d(TAG_FRAGMENT, "Menu item selection not handled by this fragment.")
                false
            }
        }
    }


    private fun filterArticles(query: String) {
        val trimmedQuery = query.trim()
        Log.d(TAG_FRAGMENT, "--- filterArticles START --- Query: '$trimmedQuery'")
        Log.d(TAG_FRAGMENT, "filterArticles: Current fullArticleList size = ${fullArticleList.size}")


        if (!::newsAdapter.isInitialized) {
            Log.e(TAG_FRAGMENT, "filterArticles: newsAdapter is not initialized! Cannot filter.")
            return
        }


        val filteredList = if (trimmedQuery.isBlank()) {
            Log.d(TAG_FRAGMENT, "filterArticles: Query is blank, showing full list.")
            fullArticleList // Show all if query is empty
        } else {
            Log.d(TAG_FRAGMENT, "filterArticles: Filtering list for '$trimmedQuery'...")
            fullArticleList.filter { article ->

                (article.title?.contains(trimmedQuery, ignoreCase = true) == true) ||
                        (article.description?.contains(trimmedQuery, ignoreCase = true) == true) ||
                        (article.source?.name?.contains(trimmedQuery, ignoreCase = true) == true)
            }
        }
        Log.d(TAG_FRAGMENT, "filterArticles: Filtering resulted in ${filteredList.size} articles.")


        view?.post {
            Log.d(TAG_FRAGMENT, "filterArticles (post): Calling newsAdapter.setList() with ${filteredList.size} items")
            newsAdapter.setList(filteredList)
            if (filteredList.isEmpty() && trimmedQuery.isNotEmpty()) {

                Log.d(TAG_FRAGMENT, "filterArticles (post): No results found for '$trimmedQuery'")
            }
        }
        Log.d(TAG_FRAGMENT, "--- filterArticles END ---")
    }
}