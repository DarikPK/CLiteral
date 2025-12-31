package com.example.imageextractor

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView

class DragSelectTouchListener(
    private val recyclerView: RecyclerView,
    private val adapter: FolderAdapter
) : RecyclerView.OnItemTouchListener {

    private var isDragging = false
    private var startPosition = RecyclerView.NO_POSITION
    private var lastDraggedPosition = RecyclerView.NO_POSITION

    private val gestureDetector = GestureDetectorCompat(recyclerView.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val view = recyclerView.findChildViewUnder(e.x, e.y)
            if (view != null) {
                val position = recyclerView.getChildAdapterPosition(view)
                if (position != RecyclerView.NO_POSITION) {
                    adapter.toggleSelection(position)
                }
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val view = recyclerView.findChildViewUnder(e.x, e.y)
            if (view != null) {
                val position = recyclerView.getChildAdapterPosition(view)
                if (position != RecyclerView.NO_POSITION) {
                    isDragging = true
                    startPosition = position
                    lastDraggedPosition = position
                    if (adapter.getSelectedItems().none { it.partidaId == adapter.currentList[position].partidaId }) {
                       adapter.toggleSelection(position)
                    }
                    recyclerView.parent.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
    })

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(e)
        return isDragging
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
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
                    startPosition = RecyclerView.NO_POSITION
                    lastDraggedPosition = RecyclerView.NO_POSITION
                    rv.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
    }
}
