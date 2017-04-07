package com.cdk.bettermapsearch

import android.support.annotation.CallSuper
import android.support.v7.widget.RecyclerView
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import com.cdk.bettermapsearch.interfaces.MapClusterItem
import com.google.android.gms.maps.model.LatLng
import com.jakewharton.rxrelay.BehaviorRelay

/**
 * This class encapsulates the handling of the list that backs both the ViewPager and the marker clustering
 * It also handles the callbacks that are required for the ViewPager item translation animations
 *
 * @param <VH> The class type of your custom ViewHolder
 */
@Suppress("unused")
abstract class MapPagerAdapter<T : MapClusterItem, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    val backingList = mutableListOf<T>()
    val callbackMap: SparseArray<BehaviorRelay<Any>> = SparseArray(3)

    abstract override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): VH

    @CallSuper
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.itemView.y = 0f
        holder.itemView.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int = backingList.size

    @CallSuper
    override fun onViewAttachedToWindow(holder: VH) {
        super.onViewAttachedToWindow(holder)

        holder.itemView.visibility = View.VISIBLE

        val position = holder.adapterPosition
        val viewCreatedCallback = callbackMap.get(position)
        viewCreatedCallback?.call("attached")
    }

    fun setCallback(position: Int, callback: BehaviorRelay<Any>): BehaviorRelay<Any> {
        callbackMap.put(position, callback)
        return callback
    }

    fun clearCallbacks() = callbackMap.clear()

    fun getItemPositionOnMap(index: Int): LatLng = backingList[index].position

    fun updateItems(items: List<T>) {
        backingList.clear()
        backingList.addAll(items)
    }

    fun getPositionOfItem(item: T?): Int = backingList.indexOf(item)

    fun getItemAtPosition(position: Int): T = backingList[position]
}
