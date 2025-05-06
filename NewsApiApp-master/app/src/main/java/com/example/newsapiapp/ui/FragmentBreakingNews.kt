package com.example.newsapiapp.ui

// --- Keep existing imports ---
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

    // --- Constants and Variables ---
    private val TAG_FRAGMENT = "FragmentBreakingNews"
    private val TAG_SPEECH = "SpeechRecognition" // Tag for speech logs

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

    // --- Fragment Lifecycle Methods ---

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

        val dao = NewsDatabase.getInstance(requireActivity()).newsDao()
        val repository = NewsRepo(dao)
        val factory = NewsViewModelFac(repository, requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[NewsViewModel::class.java]

        setUpRecyclerView()

        if (isNetworkAvailable()) {
            showConnectedState()
            loadBreakingNews()
        } else {
            showDisconnectedState()
        }

        setupCategoryClicks(view)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG_FRAGMENT, "onDestroyView")
        cleanupSpeechRecognizer() // Clean up speech recognizer
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
        val available = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
        observeBreakingNews()
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
                val articles = response.data?.articles?.filterNotNull() ?: emptyList()
                Log.d(TAG_FRAGMENT, "Success - Received ${articles.size} articles")
                fullArticleList = articles
                newFilterItems(searchView?.query?.toString() ?: "") // Re-apply filter
                if (articles.isNotEmpty() && searchView?.query.isNullOrBlank()) {
                    rv.post { rv.scrollToPosition(0) }
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
        newsAdapter = ArticleAdapter()
        newsAdapter.setItemClickListener(this)
        rv.apply {
            adapter = newsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
        Log.d(TAG_FRAGMENT, "RecyclerView setup complete.")
    }

    override fun onItemClicked(position: Int, item: Article) {
        Log.d(TAG_FRAGMENT, "Article clicked at position $position: ${item.title}")
        try {
            val action = FragmentBreakingNewsDirections.actionFragmentBreakingNewsToFragmentArticle(item)
            view?.findNavController()?.navigate(action)
        } catch (e: Exception) {
            Log.e(TAG_FRAGMENT, "Navigation failed for article: ${item.title}", e)
            Toast.makeText(context, "Could not open article details.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Category Click Handling ---

    private fun setupCategoryClicks(view: View) {
        val sportCat: CircleImageView? = view.findViewById(R.id.sportsImage)
        val techCat: CircleImageView? = view.findViewById(R.id.techImage)
        val breakingImage: CircleImageView? = view.findViewById(R.id.breakingImage)
        val businessCat: CircleImageView? = view.findViewById(R.id.businessImage)

        val catListener = View.OnClickListener { clickedView ->
            if (!isNetworkAvailable()) {
                showDisconnectedState(); return@OnClickListener
            }
            showConnectedState()
            var category: String? = null; var title: String? = null
            when (clickedView.id) {
                R.id.sportsImage -> { category = "sports"; title = "Sports News" }
                R.id.techImage -> { category = "technology"; title = "Technology News" }
                R.id.businessImage -> { category = "business"; title = "Business News" }
                R.id.breakingImage -> {
                    Log.d(TAG_FRAGMENT, "Category Clicked: Breaking News")
                    (activity as? AppCompatActivity)?.supportActionBar?.title = "Breaking News"
                    loadBreakingNews(); return@OnClickListener
                }
            }
            if (category != null && title != null) {
                Log.d(TAG_FRAGMENT, "Category Clicked: $title ($category)")
                (activity as? AppCompatActivity)?.supportActionBar?.title = title
                viewModel.getCategory(category)
                observeCategoryNews()
            }
        }
        sportCat?.setOnClickListener(catListener)
        techCat?.setOnClickListener(catListener)
        breakingImage?.setOnClickListener(catListener)
        businessCat?.setOnClickListener(catListener)
    }

    // --- Progress Bar ---

    private fun showProgressBar() { pb.visibility = View.VISIBLE }
    private fun hideProgressBar() { pb.visibility = View.GONE }

    // --- Speech Recognition Logic (Ensure checkAndStart is Public) ---

    // Make sure this is public (no 'private' keyword)
    fun checkAndStartVoiceRecognition() {
        Log.d(TAG_SPEECH, "--- checkAndStartVoiceRecognition CALLED ---") // Start marker
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Log.e(TAG_SPEECH, "ERROR: Recognizer not available.")
            Toast.makeText(context, "Speech recognition not available.", Toast.LENGTH_LONG).show()
            return
        }
        Log.d(TAG_SPEECH, "Speech Recognizer Service is available.")
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG_SPEECH, "RECORD_AUDIO permission is already granted.")
                startVoiceRecognition()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Log.i(TAG_SPEECH, "Showing rationale for RECORD_AUDIO permission.")
                Toast.makeText(context, "Microphone access is needed for voice search.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                Log.i(TAG_SPEECH, "Requesting RECORD_AUDIO permission.")
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        Log.d(TAG_SPEECH, "--- checkAndStartVoiceRecognition END ---") // End marker
    }

    // Make this private again if desired, only checkAndStart needs to be public
    private fun startVoiceRecognition() {
        Log.d(TAG_SPEECH, "--- startVoiceRecognition CALLED ---")
        cleanupSpeechRecognizer()
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            Log.d(TAG_SPEECH, "Recognizer created.")
            if (speechRecognizer == null) {
                Log.e(TAG_SPEECH, "SpeechRecognizer.createSpeechRecognizer returned null!")
                Toast.makeText(context, "Could not create speech recognizer.", Toast.LENGTH_SHORT).show()
                return
            }
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            Log.d(TAG_SPEECH, "Listener set. Starting listening...")
            speechRecognizer?.startListening(recognizerIntent)
            Log.d(TAG_SPEECH, "startListening called.")
        } catch (e: Exception) {
            Log.e(TAG_SPEECH, "EXCEPTION in startVoiceRecognition", e)
            Toast.makeText(context, "Error initializing speech recognition.", Toast.LENGTH_SHORT).show()
            cleanupSpeechRecognizer()
        }
        Log.d(TAG_SPEECH, "--- startVoiceRecognition END ---")
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG_SPEECH, "LISTENER: onReadyForSpeech")
                Toast.makeText(context, "Listening...", Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() { Log.d(TAG_SPEECH, "LISTENER: onBeginningOfSpeech.") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { Log.d(TAG_SPEECH, "LISTENER: onEndOfSpeech.") }

            override fun onError(error: Int) {
                val errorMsg = getErrorText(error)
                Log.e(TAG_SPEECH, "LISTENER: onError - Code: $error, Message: $errorMsg")
                Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_SHORT).show()
                cleanupSpeechRecognizer()
            }

            override fun onResults(results: Bundle?) {
                Log.d(TAG_SPEECH, "LISTENER: --- onResults START ---")
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d(TAG_SPEECH, "LISTENER: onResults - Matches: ${matches?.joinToString()}")
                val spokenText = matches?.firstOrNull()?.trim()
                Log.d(TAG_SPEECH, "LISTENER: onResults - Extracted spokenText: '$spokenText'")

                if (!spokenText.isNullOrBlank()) {
                    view?.post {
                        Log.d(TAG_SPEECH, "LISTENER: onResults (post) - About to set query: '$spokenText'")
                        Toast.makeText(context, "Searching for: $spokenText", Toast.LENGTH_SHORT).show()
                        searchView?.isIconified = false
                        searchView?.setQuery(spokenText, true) // Submit the query
                        Log.d(TAG_SPEECH, "LISTENER: onResults (post) - Query set.")
                    }
                } else {
                    Log.w(TAG_SPEECH, "LISTENER: onResults - Spoken text was null or blank.")
                    Toast.makeText(context, "Could not recognize speech.", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG_SPEECH, "LISTENER: --- onResults END ---")
                cleanupSpeechRecognizer()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                Log.d(TAG_SPEECH, "LISTENER: onPartialResults: ${partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()}")
            }
            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG_SPEECH, "LISTENER: onEvent: $eventType")
            }
        }
    }

    private fun cleanupSpeechRecognizer() {
        if (speechRecognizer != null) {
            try {
                Log.d(TAG_SPEECH, "Cleaning up SpeechRecognizer instance.")
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG_SPEECH, "Exception during SpeechRecognizer cleanup", e)
            } finally {
                speechRecognizer = null
            }
        }
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown speech recognition error ($errorCode)"
        }
    }

    // --- MenuProvider Implementation ---

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        Log.d(TAG_FRAGMENT, "onCreateMenu called by MenuProvider for BreakingNews")
        // MainActivity inflates the base menu (R.menu.menu)
        // We configure the SearchView instance obtained from that menu

        val searchItem = menu.findItem(R.id.searchNews)
        searchView = searchItem?.actionView as? SearchView

        if (searchView == null) {
            Log.e(TAG_FRAGMENT, "Could not find SearchView in menu!")
            menu.findItem(R.id.action_voice_search)?.isVisible = false
            return
        }
        Log.d(TAG_FRAGMENT,"SearchView configured by FragmentBreakingNews MenuProvider.")

        searchView?.queryHint = "Search News..."

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val trimmedQuery = query?.trim() ?: ""
                Log.d(TAG_FRAGMENT, "LISTENER: SearchView - onQueryTextSubmit: '$trimmedQuery'")
                searchView?.clearFocus()
                newFilterItems(trimmedQuery)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                Log.d(TAG_FRAGMENT, "LISTENER: SearchView - onQueryTextChange: '$newText'")
                newFilterItems(newText)
                return true
            }
        })

        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                Log.d(TAG_FRAGMENT, "SearchView Expanded")
                // menu.findItem(R.id.action_voice_search)?.isVisible = false // Optional: Hide mic
                return true
            }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                Log.d(TAG_FRAGMENT, "SearchView Collapsed")
                // menu.findItem(R.id.action_voice_search)?.isVisible = true // Optional: Show mic
                newFilterItems(null) // Clear filter
                return true
            }
        })
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        Log.d(TAG_FRAGMENT, "onMenuItemSelected called by FragmentBreakingNews: ${menuItem.title}")
        // Let MainActivity handle clicks for items in R.menu.menu
        return false // Return false so MainActivity's onOptionsItemSelected is called
    }

    // --- Filter Function ---
    private fun newFilterItems(query: String?) {
        Log.d(TAG_FRAGMENT, "Filtering list with query: '$query'")
        val filteredList = if (query.isNullOrBlank()) {
            fullArticleList
        } else {
            fullArticleList.filter { article ->
                (article.title?.contains(query, ignoreCase = true) == true) ||
                        (article.description?.contains(query, ignoreCase = true) == true) ||
                        (article.source?.name?.contains(query, ignoreCase = true) == true)
            }
        }
        Log.d(TAG_FRAGMENT, "Filtered list size: ${filteredList.size}")
        newsAdapter.setList(ArrayList(filteredList)) // Assuming setList method
    }
}