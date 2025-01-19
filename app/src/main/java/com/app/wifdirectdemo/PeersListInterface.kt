package com.app.wifdirectdemo

import android.net.wifi.p2p.WifiP2pDeviceList

interface PeersListInterface {
    fun receiverPeersList(peers: WifiP2pDeviceList)
}