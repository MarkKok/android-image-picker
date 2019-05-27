package com.esafirm.imagepicker.features.recyclers

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


abstract class EndlessRecyclerViewScrollListener(val layoutManager: LinearLayoutManager) : RecyclerView.OnScrollListener() {
    /**
     * The total number of items in the dataset after the last load
     */
    private var mPreviousTotal = 0
    /**
     * True if we are still waiting for the last set of data to load.
     */
    private var mLoading = true

    private val visibleThreshold = 10

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)

        val visibleItemCount = recyclerView.childCount
        val totalItemCount = layoutManager.itemCount
        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

        if (mLoading) {
            if (totalItemCount > mPreviousTotal) {
                mLoading = false
                mPreviousTotal = totalItemCount
            }
        }
        if (!mLoading && totalItemCount - visibleItemCount <= firstVisibleItem + visibleThreshold) {
            // End has been reached

            onLoadMore()

            mLoading = true
        }
    }

    abstract fun onLoadMore()
}