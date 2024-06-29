package com.huanyu.myblue

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import pub.devrel.easypermissions.EasyPermissions
import java.io.IOException
import java.util.UUID


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
    @RequiresApi(Build.VERSION_CODES.S)
    private val BLUETOOTH_PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    private var MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceListAdapter: DeviceListAdapter
    private lateinit var receivedMessagesTextView: TextView
    private lateinit var nowconnected: TextView
    private var bluetoothSocket:BluetoothSocket? = null
    private var connectedDevice: BluetoothDevice? = null
    private var nowconnectedDevice: BluetoothDevice? = null
    private val bluetoothDevices = mutableListOf<BluetoothDevice>()
    // Handler for incoming messages
    private val handler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MESSAGE_READ -> {
                val readMessage = msg.obj as String
                receivedMessagesTextView.append("Received: $readMessage\n")
                true
            }
            else -> false
        }
    }
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.getAdapter()
        deviceListAdapter = DeviceListAdapter(bluetoothDevices,this) { device ->
            connectedDevice = device
            Log.d("myblue","onclick"+device.name)
            // Handle device click
            discoverServices(device)
        }
        receivedMessagesTextView = findViewById(R.id.receivedMessagesTextView)
        val deviceList: RecyclerView = findViewById(R.id.deviceList)
        val messageEdit: EditText = findViewById(R.id.messageEdit)
        val sendBtn:Button = findViewById(R.id.sendButton)
        nowconnected = findViewById(R.id.connectDevice)
        var message:String = ""
        messageEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // 在文本变化前调用
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 在文本变化时调用
                message = s.toString()
            }

            override fun afterTextChanged(s: Editable?) {

            }
        })
        sendBtn.setOnClickListener {
            bluetoothSocket?.let { it1 -> sendData(it1,message) }
        }
        deviceList.layoutManager = LinearLayoutManager(this)
        deviceList.adapter = deviceListAdapter
        if (hasBluetoothPermissions()) {
            // 已经拥有权限，可以开始蓝牙操作
            setupBluetooth()
        } else {
            // 请求权限
            EasyPermissions.requestPermissions(
                this,
                "此应用需要蓝牙权限以进行蓝牙操作。",
                REQUEST_CODE_BLUETOOTH_PERMISSIONS,
                *BLUETOOTH_PERMISSIONS
            )
        }
        // 启动服务器线程以接受传入的连接
        val acceptThread = AcceptThread()
        acceptThread.start()



   }
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice,uuid: UUID) : Thread() {

//        val uuid: UUID = device.uuids[0].uuid
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }
        private val mmInStream = mmSocket?.inputStream
        private val mmOutStream = mmSocket?.outputStream
        public override fun run() {
            Log.d("myblue","ConnectThread2")
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                socket.connect()

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
//                manageMyConnectedSocket(socket)
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e("myblue", "Could not close the client socket", e)
            }
        }
    }
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice, uuid: UUID) {
        bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
        bluetoothAdapter.cancelDiscovery()
        try {
            Log.d("myblue","ConnectThread2")
            bluetoothSocket!!.connect()
            nowconnectedDevice = device
            runOnUiThread {
                nowconnected.append(nowconnectedDevice!!.name)
            }
            manageConnectedSocket(bluetoothSocket!!)
            sendData(bluetoothSocket!!, "Hello, Bluetooth!")
        } catch (e: IOException) {

            Log.e("BluetoothApp", "Could not connect to the device", e)
            try {
                bluetoothSocket!!.close()
            } catch (closeException: IOException) {
                Log.e("BluetoothApp", "Could not close the client socket", closeException)
            }
        }
    }
    private fun manageConnectedSocket(socket: BluetoothSocket) {
        val thread = ConnectedThread(socket)
        thread.start()
    }

    private fun sendData(socket: BluetoothSocket, data: String) {
        val thread = ConnectedThread(socket)
        thread.write(data.toByteArray())
    }
    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter.listenUsingRfcommWithServiceRecord("BluetoothApp", MY_UUID)
        }

        override fun run() {
//            var socket: BluetoothSocket? = null
            while (true) {
                bluetoothSocket = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e("BluetoothApp", "Socket's accept() method failed", e)
                    break
                }

                bluetoothSocket?.also {
                    nowconnectedDevice = it.remoteDevice
                    runOnUiThread {
                        nowconnected.append(nowconnectedDevice!!.name)
                    }
                    manageConnectedSocket(it)
                    mmServerSocket?.close()
                    return
                }
            }
        }

        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e("BluetoothApp", "Could not close the connect socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream = mmSocket.inputStream
        private val mmOutStream = mmSocket.outputStream

        override fun run() {
            Log.d("myblue","ConnectThread3")
            val buffer = ByteArray(1024)
            var bytes: Int

            while (true) {
                try {
                    bytes = mmInStream.read(buffer)
                    val readMessage = String(buffer, 0, bytes)
                    handler.obtainMessage(MESSAGE_READ, readMessage).sendToTarget()
                } catch (e: IOException) {
                    Log.e("BluetoothApp", "Disconnected", e)
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e("BluetoothApp", "Error during write", e)
            }
        }

        fun cancel() {
            try {
                mmInStream.close()
                mmOutStream.close()
                mmSocket.close()
            } catch (e: IOException) {
                Log.e("BluetoothApp", "Could not close the client socket", e)
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasBluetoothPermissions(): Boolean {
        return EasyPermissions.hasPermissions(this, *BLUETOOTH_PERMISSIONS)
    }

    private fun setupBluetooth() {
        // 初始化蓝牙相关代码

        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
        }
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            startActivityForResult(enableBtIntent, 1)
        }
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->

            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
            Log.d("mybluetoo",deviceName)
        }
        bluetoothAdapter?.startDiscovery()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

    }
    private fun discoverServices(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        device.fetchUuidsWithSdp()
        val filter = IntentFilter(BluetoothDevice.ACTION_UUID)
        registerReceiver(uuidReceiver, filter)
    }

    private val uuidReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (BluetoothDevice.ACTION_UUID == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                if (device == connectedDevice && uuids != null) {
                    uuids.forEach {
                        val tempuuid = it as ParcelUuid
                        Log.d("myblue",tempuuid.uuid.toString())
                    }
                    val uuid = uuids[0] as ParcelUuid

                    Log.d("myblue",uuids.toString())
                    Log.d("myblue",uuid.uuid.toString())
                    if (device != null) {
                        Log.d("myblue","ConnectThread1")
                        connectToDevice(device,uuid.uuid)
                    }
                }
            }
        }
    }
    // Create a BroadcastReceiver for ACTION_FOUND.
    private val receiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action.toString()
            when(action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (BluetoothDevice.ACTION_FOUND == action) {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && !bluetoothDevices.contains(device)) {
                            bluetoothDevices.add(device)
                            deviceListAdapter.notifyDataSetChanged()
                        }
                    }
                    val deviceName = device?.name
                    val deviceHardwareAddress = device?.address // MAC address
                    if (deviceName != null) {
                        Log.d("mybluetoo",deviceName)
                    }
                }
            }
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        if (requestCode == REQUEST_CODE_BLUETOOTH_PERMISSIONS) {
            // 权限被授予，可以进行蓝牙操作
            setupBluetooth()
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        if (requestCode == REQUEST_CODE_BLUETOOTH_PERMISSIONS) {
            // 权限被拒绝，显示一条消息
            Toast.makeText(this, "蓝牙权限被拒绝，无法进行蓝牙操作。", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQUEST_CODE_BLUETOOTH_PERMISSIONS = 1
        private const val TAG = "MY_APP_DEBUG_TAG"

        // Defines several constants used when transmitting messages between the
    // service and the UI.
        const val MESSAGE_READ: Int = 0
        const val MESSAGE_WRITE: Int = 1
        const val MESSAGE_TOAST: Int = 2
    }

    override fun onDestroy() {
        super.onDestroy()

        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver)
    }

}