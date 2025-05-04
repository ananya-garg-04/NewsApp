package com.example.newsapiapp.mvvm

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy // Import this
import androidx.room.Query
import com.example.newsapiapp.db.SavedArticle // Make sure this is your @Entity class

@Dao
interface NewsDao {

    // Added OnConflictStrategy (common practice)
    // Make sure SavedArticle is annotated with @Entity
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNews(savedArticle: SavedArticle) // Suspend is correct here

    // This query fetches all news. Returns LiveData List.
    // Ensure "NEWSARTICLE" is the correct table name defined in your SavedArticle @Entity
    @Query("SELECT * FROM NEWSARTICLE")
    fun getAllNews() : LiveData<List<SavedArticle>>

    // Corrected: This query now fetches a SINGLE article by its ID.
    // It requires an 'id' parameter. Adjust 'id' and 'Int' type if your primary key is different.
    @Query("SELECT * FROM NEWSARTICLE WHERE id = :id") // Added WHERE clause
    fun getNewsById(id: Int): LiveData<SavedArticle> // Added id parameter

    // Corrected typo and added suspend keyword
    @Query("DELETE FROM NEWSARTICLE")
    suspend fun deleteAll() // Added suspend keyword

}