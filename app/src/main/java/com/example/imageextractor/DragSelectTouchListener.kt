package com.example.imageextractor

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView

class DragSelectTouchListener(
    private val recyclerView: RecyclerView,
    private val adapter: FolderAdapter,
    private val isInSelectionMode: () -> Boolean
) : RecyclerView.OnItemTouchListener {

    private var isDragging = false
    private var lastDraggedPosition = RecyclerView.NO_POSITION

    private val gestureDetector = GestureDetectorCompat(recyclerView.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (isInSelectionMode()) {
                val view = recyclerView.findChildViewUnder(e.x, e.y)
                if (view != null) {
                    val position = recyclerView.getChildAdapterPosition(view)
                    if (position != RecyclerView.NO_POSITION) {
                        adapter.toggleSelection(position)
                    }
                }
                return true
            }
            return super.onSingleTapUp(e)
        }

        override fun onLongPress(e: MotionEvent) {
            if (isInSelectionMode()) {
                val view = recyclerView.findChildViewUnder(e.x, e.y)
                if (view != null) {
                    val position = recyclerView.getChildAdapterPosition(view)
                    if (position != RecyclerView.NO_POSITION) {
                        isDragging = true
                        lastDraggedPosition = position
                        recyclerView.parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
        }
    })

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (!isInSelectionMode()) return false
        gestureDetector.onTouchEvent(e)
        return isDragging
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (!isInSelectionMode()) return

        gestureDetector.onTouchEvent(e)
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
                    recyclerView.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}
