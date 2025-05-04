// File: ArticleAdapter.kt
package com.example.newsapiapp.adapters

// Import the generic ItemClickListener
import com.example.newsapiapp.adapters.ItemClickListener
// Import the network Article data class (adjust path if needed)
import com.example.newsapiapp.db.Article

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.newsapiapp.R
import com.example.newsapiapp.Utils

// Consider using ListAdapter with DiffUtil for better performance
class ArticleAdapter() : RecyclerView.Adapter<ArticleHolder>() {

    var newslist = listOf<Article>()
    // Use the generic interface, specifying the network Article type
    private var listener: ItemClickListener<Article>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.newlist, parent, false)
        return ArticleHolder(view)
    }

    override fun getItemCount(): Int {
        return newslist.size
    }

    override fun onBindViewHolder(holder: ArticleHolder, position: Int) {
        // Get the specific network Article for this position
        val article: Article = newslist[position]

        holder.itemView.apply {
            holder.pb.visibility = View.VISIBLE

            Glide.with(this)
                .load(article.urlToImage)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        holder.pb.visibility = View.GONE
                        return false
                    }
                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        holder.pb.visibility = View.GONE
                        return false
                    }
                })
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.imageView)

            holder.textTitle.text = article.title ?: ""
            holder.tvSource.text = article.source?.name ?: "Unknown Source"
            holder.tvDescription.text = article.description ?: ""
            holder.tvPubslishedAt.text = article.publishedAt?.let { Utils.DateFormat(it) } ?: ""
        }

        // Click Listener Implementation
        holder.itemView.setOnClickListener {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                val clickedArticle = newslist[currentPosition] // This is an Article
                // Call the listener, passing the Article - types match now
                listener?.onItemClicked(currentPosition, clickedArticle)
            }
        }
    }

    // Update the function signature to expect the specific generic type
    fun setItemClickListener(listener: ItemClickListener<Article>) {
        this.listener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setList(articles: List<Article>) {
        this.newslist = articles
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun filteredList(newFilteredList: List<Article>) {
        this.newslist = newFilteredList
        notifyDataSetChanged()
    }
}

// ViewHolder class remains the same structure
class ArticleHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val textTitle: TextView = itemView.findViewById(R.id.tvTitle)
    val tvSource: TextView = itemView.findViewById(R.id.tvSource)
    val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
    val tvPubslishedAt: TextView = itemView.findViewById(R.id.tvPublishedAt)
    val imageView: ImageView = itemView.findViewById(R.id.ivArticleImage)
    val pb: ProgressBar = itemView.findViewById(R.id.pbImage)
}

// *** NO INTERFACE DEFINITION HERE ANYMORE ***