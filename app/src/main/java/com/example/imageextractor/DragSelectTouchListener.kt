package com.example.imageextractor

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView

class DragSelectTouchListener(
    private val recyclerView: RecyclerView,
    private val adapter: FolderAdapter,
    private val isInSelectionMode: () -> Boolean,
    private val onDragSelectionFinished: () -> Unit
) : RecyclerView.OnItemTouchListener {

    private var isDragging = false
    private var lastDraggedPosition = RecyclerView.NO_POSITION

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (!isInSelectionMode()) return false

        val action = e.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            val view = rv.findChildViewUnder(e.x, e.y)
            if (view != null) {
                lastDraggedPosition = rv.getChildAdapterPosition(view)
                isDragging = true
            }
        }
        return isDragging
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (!isInSelectionMode()) return

        val action = e.actionMasked
        when (action) {
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val view = rv.findChildViewUnder(e.x, e.y)
                    val currentPosition = if (view != null) rv.getChildAdapterPosition(view) else RecyclerView.NO_POSITION

                    if (currentPosition != RecyclerView.NO_POSITION && currentPosition != lastDraggedPosition) {
                        if (currentPosition > lastDraggedPosition) {
                            adapter.selectRange(lastDraggedPosition, currentPosition)
                        } else {
                            adapter.deselectRange(lastDraggedPosition, currentPosition)
                        }
                        lastDraggedPosition = currentPosition
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    onDragSelectionFinished()
                }
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}
