package com.app.wifdirectdemo

import android.net.wifi.p2p.WifiP2pDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WifiP2pDeviceAdapter(private val deviceCollection: Collection<WifiP2pDevice>, var peerDetailsInterface: PeerDetailsInterface) :
    RecyclerView.Adapter<WifiP2pDeviceAdapter.DeviceViewHolder>() {

    // ViewHolder class to hold references to each view
    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPeerName: TextView = itemView.findViewById(R.id.tvPeerName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.adapter_peer_list, parent, false)
        return DeviceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = deviceCollection.elementAt(position)  // Access elements from Collection

        // Set the name of the peer
        holder.tvPeerName.text = device.deviceName

        // Set the name of the peer
        holder.tvPeerName.text = device.deviceName
        holder.tvPeerName.setOnClickListener {
            peerDetailsInterface.peerDetails(device)
        }
    }

    override fun getItemCount(): Int {
        return deviceCollection.size
    }
}
