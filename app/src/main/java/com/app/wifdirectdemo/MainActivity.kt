package com.app.wifdirectdemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity(),PeersListInterface {

    private lateinit var discoverPeerListLayout: ConstraintLayout
    private lateinit var peersDetailsLayout: ConstraintLayout
    private lateinit var rcvPeers: RecyclerView
    private lateinit var btnDiscoevrPeers : Button
    private lateinit var btnSendMessage : Button
    private lateinit var tvPeerList: TextView
    private lateinit var tvGroupOwnerIP: TextView
    private lateinit var tvResponseFromServer: TextView
    private lateinit var btnDisconnectPeer: Button
    private lateinit var peerAdapter: WifiP2pDeviceAdapter
    var connectedPeer: WifiP2pDevice? = null
    lateinit var edtMessage: EditText
    var host:String? = null
    private lateinit var progressDialog: AlertDialog

    private var isStreaming = false
    var audioSocket: Socket? = null
    var audioRecord: AudioRecord? = null

    val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }

    var channel: WifiP2pManager.Channel? = null
    var receiver: BroadcastReceiver? = null

    val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    //For speechListerner
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var listeningDialog: AlertDialog
    private lateinit var dialogTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        channel = manager?.initialize(this, mainLooper, null)
        channel?.also { channel ->
            receiver = manager?.let { WiFiDirectBroadcastReceiver(it, channel, this, this) }
        }
        initUI()
        initActionButtons()
        onClickEvents()
        initSpeechRecognizer()
    }

    private fun initUI(){
        discoverPeerListLayout = findViewById(R.id.discoverPeerListLayout)
        peersDetailsLayout = findViewById(R.id.peersDetailsLayout)
        rcvPeers = findViewById(R.id.rcvPeers)
        btnDisconnectPeer = findViewById(R.id.btnDisconnectPeer)
        edtMessage = findViewById(R.id.edtMessage)
        btnDiscoevrPeers = findViewById(R.id.btnDiscoverPeers)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        tvPeerList = findViewById(R.id.tvPeerList)
        tvGroupOwnerIP = findViewById(R.id.tvGroupOwnerIP)
        tvResponseFromServer = findViewById(R.id.tvResponseFromServer)
    }

    fun onClickEvents(){
        btnDiscoevrPeers.setOnClickListener {
            manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {

                override fun onSuccess() {
                    Log.d("MainActivity","Discovering Peers Success")
                }

                override fun onFailure(reasonCode: Int) {
                    Log.d("MainActivity","Discovering Peers failed $reasonCode")

                }
            })
        }
        btnSendMessage.setOnClickListener {
            if (host != null){
                CoroutineScope(Dispatchers.IO).launch {
                    val message = edtMessage.text.toString()
                    sendMessageToServer(message, tvResponseFromServer)
                }
            }
        }
        btnDisconnectPeer.setOnClickListener {
            if (connectedPeer != null){
                channel?.also { channel ->
                    manager?.removeGroup(channel,object : WifiP2pManager.ActionListener {

                        override fun onSuccess() {
                            discoverPeerListLayout.visibility = View.VISIBLE
                            peersDetailsLayout.visibility = View.GONE
                            connectedPeer = null
                            Log.d("MainActivity","DisConnection Success")
                        }

                        override fun onFailure(reason: Int) {
                            Log.d("MainActivity","DisConnection Failed")
                        }
                    }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        receiver?.also { receiver ->
            registerReceiver(receiver, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        receiver?.also { receiver ->
            unregisterReceiver(receiver)
        }
    }

    fun connectToPeers(device: WifiP2pDevice) {
        val config = WifiP2pConfig()
        config.deviceAddress = device.deviceAddress
        channel?.also { channel ->
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {

                override fun onSuccess() {
                   // manager?.requestConnectionInfo(channel, connectionInfoListener)
                    waitForGroupCreation()
                    Log.d("MainActivity","Connection Success")
                }

                override fun onFailure(reason: Int) {
                    Log.d("MainActivity","Connection Failed")
                }
            }
            )
        }
    }

    private fun waitForGroupCreation() {
        showProcessingDialog() // Show processing screen

        val handler = Handler(Looper.getMainLooper())
        val retryInterval = 2000L // 2 seconds

        val checkGroupTask = object : Runnable {
            override fun run() {
                manager?.requestConnectionInfo(channel) { info ->
                    if (info.groupFormed) {
                        Log.d("Client", "Group formed. Proceeding with connection.")
                        dismissProcessingDialog() // Dismiss the dialog
                        handler.removeCallbacks(this) // Stop further checks
                        onGroupFormed(info)
                    } else {
                        Log.d("Client", "Group not formed yet. Retrying...")
                        handler.postDelayed(this, retryInterval) // Retry after the interval
                    }
                }
            }
        }
        handler.post(checkGroupTask)
    }


    private fun onGroupFormed(info: WifiP2pInfo) {
        val groupOwnerIp = info.groupOwnerAddress.hostAddress
        tvGroupOwnerIP.setText(groupOwnerIp.toString())
        host = groupOwnerIp.toString()
        Log.d("Client", "Connected to Group Owner at IP: $groupOwnerIp")
        // Proceed with your connection logic here
    }


    private fun sendMessageToServer(message: String, responseTextView: TextView) {
        try {
            //val serverIP = "192.168.49.1" // Server (Group Owner) IP val serverIP = "192.168.1.3"
            val serverPort = 8888         // Port number

            // Connect to the server
            val socket = Socket(host, serverPort)

            // Send message to server
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.println(message)

            // Receive response from server
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val response = reader.readLine()

            // Update response on the main thread
            runOnUiThread {
                responseTextView.text = "Server Response: $response"
            }

            // Close streams and socket
            reader.close()
            writer.close()
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                responseTextView.text = "Error: ${e.message}"
            }
        }
    }

    fun initActionButtons(){
        findViewById<Button>(R.id.btnLeft).setOnClickListener {
            if (host != null){
                CoroutineScope(Dispatchers.IO).launch {
                    sendMessageToServer("left", tvResponseFromServer)
                }
            }
        }
        findViewById<Button>(R.id.btnRight).setOnClickListener {
            if (host != null){
                CoroutineScope(Dispatchers.IO).launch {
                    sendMessageToServer("right", tvResponseFromServer)
                }
            }
        }
        findViewById<Button>(R.id.btnUp).setOnClickListener {
            if (host != null){
                CoroutineScope(Dispatchers.IO).launch {
                    sendMessageToServer("up", tvResponseFromServer)
                }
            }
        }
        findViewById<Button>(R.id.btnDown).setOnClickListener {
            if (host != null){
                CoroutineScope(Dispatchers.IO).launch {
                    sendMessageToServer("down", tvResponseFromServer)
                }
            }
        }
        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            if (host != null){
                CoroutineScope(Dispatchers.IO).launch {
                    sendMessageToServer("enter", tvResponseFromServer)
                }
            }
        }
        findViewById<Button>(R.id.btnStartRecording).setOnClickListener {
            isStreaming = true
            Thread { sendAudioToServer(tvResponseFromServer) }.start()
        }

        findViewById<Button>(R.id.btnStopRecording).setOnClickListener {
            isStreaming = false
            if (audioSocket != null){
                audioSocket?.close()
            }
        }
    }

    override fun receiverPeersList(peers: WifiP2pDeviceList) {
        Log.d("MainActivity", "Peerls List $peers")
        peerAdapter = WifiP2pDeviceAdapter(peers.deviceList, object : PeerDetailsInterface{
            override fun peerDetails(peer: WifiP2pDevice) {
                connectedPeer = peer
                discoverPeerListLayout.visibility = View.GONE
                peersDetailsLayout.visibility = View.VISIBLE
                tvPeerList.setText(peer.deviceName)
                connectToPeers(peer)
            }
        })
        rcvPeers.adapter = peerAdapter
        rcvPeers.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }


    private fun showProcessingDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setView(R.layout.processing_dialog_layout) // Create a custom layout with a spinner/text
        progressDialog = builder.create()
        progressDialog.show()
    }

    private fun dismissProcessingDialog() {
        if (::progressDialog.isInitialized && progressDialog.isShowing) {
            progressDialog.dismiss()
        }
    }

    private fun sendAudioToServer(statusTextView: TextView) {
        val sampleRate = 44100
        val serverPort = 9999
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        var outputStream: OutputStream? = null

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioSocket = Socket(host, serverPort)
            outputStream = audioSocket?.getOutputStream()

            audioRecord?.startRecording()
            val buffer = ByteArray(bufferSize)

            runOnUiThread { statusTextView.text = "Streaming audio to server..."+bufferSize }

            while (isStreaming && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size)
                if (bytesRead != null && bytesRead > 0) {
                    outputStream?.write(buffer, 0, bytesRead)
                    Log.d("Buffer","------------------->Buffer "+buffer)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread { statusTextView.text = "Error: ${e.message}" }
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
                outputStream?.close()
                audioSocket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isStreaming && audioSocket != null){
            audioSocket?.close()
        }
    }

    fun initSpeechRecognizer(){
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        // Create a custom AlertDialog for listening state
        val dialogView = layoutInflater.inflate(R.layout.dialog_speechlisterner, null)
        dialogTitle = dialogView.findViewById(R.id.dialogTitle)

        listeningDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false) // Prevent dismissing manually
            .create()

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateDialogTitle("Ready to listen...")
                listeningDialog.show()
            }

            override fun onBeginningOfSpeech() {
                updateDialogTitle("Listening...")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Optional: You can use rmsdB to update UI dynamically
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                updateDialogTitle("Processing...")
            }

            override fun onError(error: Int) {
                listeningDialog.dismiss()
                Toast.makeText(this@MainActivity, "Error occurred: $error", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                listeningDialog.dismiss()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    Toast.makeText(this@MainActivity, "$recognizedText", Toast.LENGTH_LONG).show()
                    CoroutineScope(Dispatchers.IO).launch {
                        sendMessageToServer("$recognizedText", tvResponseFromServer)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        findViewById<Button>(R.id.btnStartSpeech).setOnClickListener {
            startSpeechRecognizer()
        }
    }

    private fun updateDialogTitle(title: String) {
        dialogTitle.text = title
    }

    fun startSpeechRecognizer(){
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // Specify the language
        }
        speechRecognizer.startListening(intent)
    }
}