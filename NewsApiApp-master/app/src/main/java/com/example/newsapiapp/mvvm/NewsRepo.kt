package com.example.newsapiapp.mvvm

import androidx.lifecycle.LiveData
import com.example.newsapiapp.db.SavedArticle
import com.example.newsapiapp.service.RetrofitInstance
import kotlinx.coroutines.Dispatchers // Import Dispatchers if needed for DAO calls
import kotlinx.coroutines.withContext // Import withContext if needed

class NewsRepo(private val newsDao: NewsDao) { // Made newsDao private val (good practice)

    // Function to get all saved news (no changes needed here)
    fun getAllSavedNews(): LiveData<List<SavedArticle>> {
        return newsDao.getAllNews()
    }

    // ** FIX: Function to get a specific saved news article by ID **
    // It now accepts an 'id' parameter
    fun getNewsById(id: Int): LiveData<SavedArticle> {
        // Pass the received 'id' to the DAO function
        return newsDao.getNewsById(id)
    }

    // --- Network Operations ---

    // Function to get breaking news from the API
    suspend fun getBreakingNews(code: String, pageNumber: Int) =
        RetrofitInstance.api.getBreakingNews(code, pageNumber)

    // Function to get category news from the API
    suspend fun getCategoryNews(code: String) =
        RetrofitInstance.api.getByCategory(code)

    // --- Database Write Operations ---

    // ** FIX: Corrected typo and added suspend modifier **
    // Function to delete all saved news
    suspend fun deleteAll() {
        // It's good practice to run DAO writes off the main thread, even if suspend handles it
        // withContext(Dispatchers.IO) { // Optional: Explicitly use IO dispatcher
        newsDao.deleteAll() // Corrected spelling: deleteAll
        // }
    }

    // Function to insert a single saved article
    suspend fun insertNews(savedArticle: SavedArticle) {
        // withContext(Dispatchers.IO) { // Optional: Explicitly use IO dispatcher
        newsDao.insertNews(savedArticle)
        // }
    }
}