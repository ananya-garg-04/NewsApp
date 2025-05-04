package com.example.newsapiapp.ui

import android.os.Bundle
import android.speech.tts.TextToSpeech // Import TTS
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.bumptech.glide.Glide
import com.example.newsapiapp.R
import com.example.newsapiapp.Utils // Make sure Utils.DateFormat exists and works
import com.example.newsapiapp.db.SavedArticle
import com.example.newsapiapp.db.Source // Keep if Source class is used directly
import com.example.newsapiapp.mvvm.NewsDatabase
import com.example.newsapiapp.mvvm.NewsRepo
import com.example.newsapiapp.mvvm.NewsViewModel
import com.example.newsapiapp.mvvm.NewsViewModelFac
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Locale // Import Locale for TTS


// Implement OnInitListener for TextToSpeech
class FragmentArticle : Fragment(), TextToSpeech.OnInitListener {

    // Add a TAG for logging
    private val TAG = "FragmentArticle"

    // ... (other variables remain the same) ...
    private lateinit var viewModel: NewsViewModel
    private lateinit var args: FragmentArticleArgs
    private var stringCheck = ""
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private lateinit var textTitle: TextView
    private lateinit var tSource: TextView
    private lateinit var tDescription: TextView
    private lateinit var tPubslishedAt: TextView
    private lateinit var imageView: ImageView
    private lateinit var fabSave: FloatingActionButton
    private lateinit var fabSpeak: FloatingActionButton


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        tts = TextToSpeech(requireContext(), this)
        return inflater.inflate(R.layout.fragment_article, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        (activity as? AppCompatActivity)?.supportActionBar?.title = "Article View"

        // --- ViewModel Setup ---
        try {
            val dao = NewsDatabase.getInstance(requireActivity()).newsDao()
            val repository = NewsRepo(dao)
            val factory = NewsViewModelFac(repository, requireActivity().application)
            viewModel = ViewModelProvider(this, factory)[NewsViewModel::class.java]
            Log.d(TAG, "ViewModel initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ViewModel", e)
            // Handle error appropriately
        }


        // --- Get Navigation Args ---
        try {
            args = FragmentArticleArgs.fromBundle(requireArguments())
            // ** LOG THE RECEIVED ARTICLE **
            Log.d(TAG, "Arguments received. Article: ${args.article}")
            if (args.article == null) {
                Log.e(TAG, "Received article argument is NULL!")
            }
        } catch (e: Exception) { // Catch specific exceptions if possible
            Log.e(TAG, "Error getting arguments", e)
            Toast.makeText(context, "Error loading article data", Toast.LENGTH_SHORT).show()
            view.findNavController().navigateUp()
            return
        }

        // --- Initialize Views ---
        try {
            fabSave = view.findViewById(R.id.fab_save)
            fabSpeak = view.findViewById(R.id.fab_speak)
            textTitle = view.findViewById(R.id.tvTitle)
            tSource = view.findViewById(R.id.tvSource)
            tDescription = view.findViewById(R.id.tvDescription)
            tPubslishedAt = view.findViewById(R.id.tvPublishedAt)
            imageView = view.findViewById(R.id.articleImage)
            Log.d(TAG, "Views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error finding views by ID", e)
            Toast.makeText(context, "Layout error", Toast.LENGTH_SHORT).show()
            return // Stop if views can't be found
        }


        // --- Populate Views ---
        populateUI()

        // --- Check if article is already saved ---
        observeSavedStatus()

        // --- FAB Click Listeners ---
        setupFabListeners()
    }

    private fun populateUI() {
        Log.d(TAG, "Attempting to populate UI...")
        args.article?.let { article -> // Use safe call on args.article itself
            Log.d(TAG, "Populating with Article Title: ${article.title}")
            textTitle.text = article.title ?: "No Title"

            Log.d(TAG, "Populating with Source Name: ${article.source?.name}")
            tSource.text = article.source?.name ?: "Unknown Source"

            Log.d(TAG, "Populating with Description: ${article.description}")
            tDescription.text = article.description ?: "No Description Available"

            val formattedDate = article.publishedAt?.let { Utils.DateFormat(it) } ?: "Date Unknown"
            Log.d(TAG, "Populating with Date: $formattedDate (Original: ${article.publishedAt})")
            tPubslishedAt.text = formattedDate

            val imageUrl = article.urlToImage
            Log.d(TAG, "Attempting to load image URL: $imageUrl")
            if (imageUrl.isNullOrBlank()) {
                Log.w(TAG, "Image URL is null or blank!")
                // Optionally set a default image if URL is missing
                imageView.setImageResource(R.drawable.ic_launcher_foreground) // Example default
            } else {
                Glide.with(requireActivity())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_launcher_background) // Ensure this drawable exists
                    .error(R.drawable.ic_launcher_foreground) // Ensure this drawable exists
                    .into(imageView)
                Log.d(TAG, "Glide load initiated for: $imageUrl")
            }
            Log.d(TAG, "UI Population attempt finished.")

        } ?: run {
            Log.e(TAG, "args.article is NULL, cannot populate UI.")
            Toast.makeText(context, "Failed to load article details.", Toast.LENGTH_SHORT).show()
            textTitle.text = "Error"
            tSource.text = ""
            tDescription.text = "Could not load article content."
            tPubslishedAt.text = ""
            // Optionally set default image
            imageView.setImageResource(R.drawable.ic_launcher_foreground) // Example default
        }
    }

    // ... (observeSavedStatus, updateSaveFabIcon, onInit, setupFabListeners, handleSaveArticle, speakArticleContent, onDestroyView remain the same as previous version) ...

    // --- TextToSpeech OnInitListener Implementation ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS Language not supported!")
                isTtsInitialized = false
                fabSpeak.isEnabled = false
            } else {
                Log.i(TAG, "TTS Initialized Successfully")
                isTtsInitialized = true
                fabSpeak.isEnabled = true
            }
        } else {
            Log.e(TAG, "TTS Initialization Failed!")
            isTtsInitialized = false
            fabSpeak.isEnabled = false
        }
    }

    private fun setupFabListeners() {
        fabSave.setOnClickListener {
            handleSaveArticle()
        }
        fabSpeak.setOnClickListener {
            speakArticleContent()
        }
        fabSpeak.isEnabled = isTtsInitialized
    }

    private fun handleSaveArticle() {
        val articleToSave = args.article ?: return
        if (stringCheck.isNotEmpty()) {
            Log.d(TAG, "Article already exists: ${articleToSave.title}")
            Toast.makeText(context, "Article already saved", Toast.LENGTH_SHORT).show()
        } else {
            try {
                val sourceForDb = Source(id = articleToSave.source?.id, name = articleToSave.source?.name ?: "Unknown")
                val savedArticle = SavedArticle(id = 0, description = articleToSave.description ?: "", publishedAt = articleToSave.publishedAt ?: "", source = sourceForDb, title = articleToSave.title ?: "No Title", url = articleToSave.url ?: "", urlToImage = articleToSave.urlToImage ?: "")
                viewModel.insertArticle(savedArticle)
                Log.i(TAG, "Article saved: ${savedArticle.title}")
                Toast.makeText(context, "Article Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving article", e)
                Toast.makeText(context, "Error saving article", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeSavedStatus() {
        viewModel.getSavedNews.observe(viewLifecycleOwner, Observer { savedArticles ->
            val currentArticleTitle = args.article?.title
            if (currentArticleTitle != null) {
                stringCheck = savedArticles?.find { it.title == currentArticleTitle }?.title ?: ""
                updateSaveFabIcon()
            } else {
                stringCheck = ""
                updateSaveFabIcon()
            }
            Log.d(TAG, "Saved status check. Found match: ${stringCheck.isNotEmpty()} for title: $currentArticleTitle")
        })
    }

    private fun updateSaveFabIcon() {
        if (view == null) return // Check if view is available
        try {
            if (stringCheck.isNotEmpty()) {
                fabSave.setImageResource(R.drawable.ic_favorite_filled)
            } else {
                fabSave.setImageResource(R.drawable.ic_favorite)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating FAB icon", e)
        }
    }


    private fun speakArticleContent() {
        if (!isTtsInitialized || tts == null) {
            Toast.makeText(context, "Text-to-Speech is not ready.", Toast.LENGTH_SHORT).show()
            return
        }
        val title = textTitle.text?.toString()?.takeIf { it.isNotBlank() } ?: "No Title Available."
        val source = tSource.text?.toString()?.takeIf { it.isNotBlank() }?.let { "Source: $it." } ?: ""
        val description = tDescription.text?.toString()?.takeIf { it.isNotBlank() } ?: "No Description Available."
        val textToSpeak = "$title $source $description"

        if (textToSpeak.length > 10) {
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
            Log.i(TAG, "Speaking: ${textToSpeak.take(50)}...")
        } else {
            Log.w(TAG, "No sufficient content available to speak.")
            Toast.makeText(context, "No content to read aloud.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView called")
        if (tts != null) {
            Log.i(TAG, "Stopping and shutting down TTS.")
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsInitialized = false
        }
        super.onDestroyView()
    }
} // End of Fragment Class

//// Implement OnInitListener for TextToSpeech
//class FragmentArticle : Fragment(), TextToSpeech.OnInitListener {
//
//    private lateinit var viewModel: NewsViewModel
//    private lateinit var args: FragmentArticleArgs
//    private var stringCheck = "" // Tracks if article title is in saved list
//
//    // TextToSpeech variable
//    private var tts: TextToSpeech? = null
//    private var isTtsInitialized = false
//
//    // Views (Consider View Binding for cleaner code)
//    private lateinit var textTitle: TextView
//    private lateinit var tSource: TextView
//    private lateinit var tDescription: TextView
//    private lateinit var tPubslishedAt: TextView
//    private lateinit var imageView: ImageView // Added ImageView reference
//    private lateinit var fabSave: FloatingActionButton
//    private lateinit var fabSpeak: FloatingActionButton
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        // Initialize TTS engine early
//        tts = TextToSpeech(requireContext(), this)
//        // Inflate the layout for this fragment
//        return inflater.inflate(R.layout.fragment_article, container, false)
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        (activity as? AppCompatActivity)?.supportActionBar?.title = "Article View"
//
//        // --- ViewModel Setup ---
//        val dao = NewsDatabase.getInstance(requireActivity()).newsDao()
//        val repository = NewsRepo(dao)
//        val factory = NewsViewModelFac(repository, requireActivity().application)
//        viewModel = ViewModelProvider(this, factory)[NewsViewModel::class.java]
//
//        // --- Get Navigation Args ---
//        try {
//            args = FragmentArticleArgs.fromBundle(requireArguments())
//        } catch (e: IllegalStateException) {
//            Log.e("FragmentArticle", "Error getting arguments", e)
//            Toast.makeText(context, "Error loading article data", Toast.LENGTH_SHORT).show()
//            view.findNavController().navigateUp()
//            return
//        }
//
//        // --- Initialize Views ---
//        // **** DOUBLE CHECK THESE IDs MATCH YOUR XML ****
//        fabSave = view.findViewById(R.id.fab_save)
//        fabSpeak = view.findViewById(R.id.fab_speak)
//        textTitle = view.findViewById(R.id.tvTitle)
//        tSource = view.findViewById(R.id.tvSource)
//        tDescription = view.findViewById(R.id.tvDescription)
//        tPubslishedAt = view.findViewById(R.id.tvPublishedAt)
//        imageView = view.findViewById(R.id.articleImage) // Initialize ImageView
//
//        // --- Populate Views ---
//        // This is the crucial part for displaying content
//        populateUI()
//
//        // --- Check if article is already saved ---
//        observeSavedStatus()
//
//        // --- FAB Click Listeners ---
//        setupFabListeners()
//    }
//
//    private fun populateUI() {
//        Log.d("FragmentArticle", "Populating UI for article: ${args.article?.title}") // Add Log
//        args.article?.let { article -> // Use safe call on args.article itself
//            textTitle.text = article.title ?: "No Title"
//            tSource.text = article.source?.name ?: "Unknown Source"
//            tDescription.text = article.description ?: "No Description Available"
//            tPubslishedAt.text = article.publishedAt?.let { Utils.DateFormat(it) } ?: "Date Unknown"
//
//            // Log the URL before loading
//            Log.d("FragmentArticle", "Loading image URL: ${article.urlToImage}")
//            Glide.with(requireActivity())
//                .load(article.urlToImage)
//                .placeholder(R.drawable.ic_launcher_background) // Add a placeholder drawable
//                .error(R.drawable.ic_launcher_foreground) // Add an error drawable
//                .into(imageView)
//        } ?: run {
//            // Handle case where args.article itself is null (shouldn't happen with safe args unless default value is null)
//            Log.e("FragmentArticle", "args.article is null, cannot populate UI.")
//            Toast.makeText(context, "Failed to load article details.", Toast.LENGTH_SHORT).show()
//            // Set default text or navigate back
//            textTitle.text = "Error"
//            tSource.text = ""
//            tDescription.text = "Could not load article content."
//            tPubslishedAt.text = ""
//        }
//    }
//
//    private fun observeSavedStatus() {
//        viewModel.getSavedNews.observe(viewLifecycleOwner, Observer { savedArticles ->
//            val currentArticleTitle = args.article?.title
//            if (currentArticleTitle != null) {
//                stringCheck = savedArticles?.find { it.title == currentArticleTitle }?.title ?: ""
//                updateSaveFabIcon() // Update icon based on saved status
//            } else {
//                stringCheck = "" // Reset if current article has no title
//                updateSaveFabIcon()
//            }
//            Log.d("FragmentArticle", "Saved status check complete. Found match: ${stringCheck.isNotEmpty()}")
//        })
//    }
//
//    private fun updateSaveFabIcon() {
//        if (stringCheck.isNotEmpty()) {
//            fabSave.setImageResource(R.drawable.ic_favorite_filled)
//        } else {
//            fabSave.setImageResource(R.drawable.ic_favorite)
//        }
//    }
//
//
//    // --- TextToSpeech OnInitListener Implementation ---
//    override fun onInit(status: Int) {
//        if (status == TextToSpeech.SUCCESS) {
//            val result = tts?.setLanguage(Locale.getDefault())
//            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                Log.e("TTS", "TTS Language not supported!")
//                isTtsInitialized = false
//                fabSpeak.isEnabled = false
//            } else {
//                Log.i("TTS", "TTS Initialized Successfully")
//                isTtsInitialized = true
//                fabSpeak.isEnabled = true
//            }
//        } else {
//            Log.e("TTS", "TTS Initialization Failed!")
//            isTtsInitialized = false
//            fabSpeak.isEnabled = false
//        }
//    }
//
//    private fun setupFabListeners() {
//        fabSave.setOnClickListener {
//            handleSaveArticle()
//        }
//        fabSpeak.setOnClickListener {
//            speakArticleContent()
//        }
//        // Initially disable speak button until TTS is ready
//        fabSpeak.isEnabled = isTtsInitialized
//    }
//
//    private fun handleSaveArticle() {
//        val articleToSave = args.article ?: return
//
//        if (stringCheck.isNotEmpty()) {
//            Log.d("FragmentArticle", "Article already exists: ${articleToSave.title}")
//            Toast.makeText(context, "Article already saved", Toast.LENGTH_SHORT).show()
//        } else {
//            try {
//                // Ensure source is handled correctly even if null from API
//                val sourceForDb = Source(
//                    id = articleToSave.source?.id,
//                    name = articleToSave.source?.name ?: "Unknown"
//                )
//                val savedArticle = SavedArticle(
//                    id = 0,
//                    description = articleToSave.description ?: "",
//                    publishedAt = articleToSave.publishedAt ?: "",
//                    source = sourceForDb, // Use the safely created Source object
//                    title = articleToSave.title ?: "No Title",
//                    url = articleToSave.url ?: "",
//                    urlToImage = articleToSave.urlToImage ?: ""
//                )
//                viewModel.insertArticle(savedArticle)
//                Log.i("FragmentArticle", "Article saved: ${savedArticle.title}")
//                Toast.makeText(context, "Article Saved", Toast.LENGTH_SHORT).show()
//                // Observer will update stringCheck and icon automatically
//            } catch (e: Exception) {
//                Log.e("FragmentArticle", "Error saving article", e)
//                Toast.makeText(context, "Error saving article", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    private fun speakArticleContent() {
//        if (!isTtsInitialized || tts == null) {
//            Toast.makeText(context, "Text-to-Speech is not ready.", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Get text safely from TextViews
//        val title = textTitle.text?.toString()?.takeIf { it.isNotBlank() } ?: "No Title Available."
//        val source = tSource.text?.toString()?.takeIf { it.isNotBlank() }?.let { "Source: $it." } ?: ""
//        val description = tDescription.text?.toString()?.takeIf { it.isNotBlank() } ?: "No Description Available."
//
//        // Combine parts
//        val textToSpeak = "$title $source $description"
//
//        if (textToSpeak.length > 10) { // Check if combined text is meaningful
//            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
//            Log.i("TTS", "Speaking: ${textToSpeak.take(50)}...")
//        } else {
//            Log.w("TTS", "No sufficient content available to speak.")
//            Toast.makeText(context, "No content to read aloud.", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    // --- Lifecycle Management for TTS ---
//    override fun onDestroyView() {
//        if (tts != null) {
//            Log.i("TTS", "Stopping and shutting down TTS.")
//            tts?.stop()
//            tts?.shutdown()
//            tts = null
//            isTtsInitialized = false
//        }
//        super.onDestroyView()
//    }
//}