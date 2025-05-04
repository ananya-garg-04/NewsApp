// File: ItemClickListener.kt
package com.example.newsapiapp.adapters

/**
 * A generic interface for handling item clicks in a RecyclerView Adapter.
 * @param T The type of the item that was clicked.
 */
interface ItemClickListener<T> {
    /**
     * Called when an item at the specified position is clicked.
     * @param position The position of the clicked item in the adapter.
     * @param item The data item associated with the clicked view.
     */
    fun onItemClicked(position: Int, item: T)
}