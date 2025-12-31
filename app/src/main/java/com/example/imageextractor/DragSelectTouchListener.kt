package com.example.imageextractor

import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

class DragSelectTouchListener(
    private val recyclerView: RecyclerView,
    private val adapter: FolderAdapter,
    private val onDragSelectionChanged: (Int, Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    private var startPosition = RecyclerView.NO_POSITION
    private var endPosition = RecyclerView.NO_POSITION
    private var isDragging = false

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        val action = e.actionMasked
        val view = rv.findChildViewUnder(e.x, e.y) ?: return false
        val position = rv.getChildAdapterPosition(view)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                startPosition = position
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging && startPosition != RecyclerView.NO_POSITION) {
                    isDragging = true
                    rv.parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return isDragging
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        val action = e.actionMasked
        val view = rv.findChildViewUnder(e.x, e.y)
        val position = if (view != null) rv.getChildAdapterPosition(view) else RecyclerView.NO_POSITION

        when (action) {
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && position != RecyclerView.NO_POSITION) {
                    endPosition = position
                    onDragSelectionChanged(startPosition, endPosition)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                rv.parent.requestDisallowInterceptTouchEvent(false)

                // Si no hubo arrastre (solo clic), se trata como un toque normal.
                if (startPosition != RecyclerView.NO_POSITION && endPosition == RecyclerView.NO_POSITION) {
                     if (position == startPosition) {
                        adapter.toggleSelection(position)
                    }
                }

                startPosition = RecyclerView.NO_POSITION
                endPosition = RecyclerView.NO_POSITION
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // No es necesario implementar
    }
}
