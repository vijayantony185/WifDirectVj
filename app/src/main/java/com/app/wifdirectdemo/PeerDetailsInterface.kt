package com.app.wifdirectdemo

import android.net.wifi.p2p.WifiP2pDevice

interface PeerDetailsInterface {
    fun peerDetails(peer: WifiP2pDevice)
}