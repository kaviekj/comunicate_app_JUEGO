package com.example.comunicate_apptest

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import java.io.IOException
import java.io.InputStream
import java.util.*
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var charTextView: TextView
    private lateinit var drawer: DrawerLayout
    private lateinit var tts: TextToSpeech
    private lateinit var gridLetras: GridView
    private lateinit var signContainer: LinearLayout
    private lateinit var imgSign: ImageView
    private lateinit var txtEvaluation: TextView
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var readThread: Thread? = null
    private var mantenerHiloActivo = true

    companion object {
        private val deviceName = "HC-05"
        private val uuidBt = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        const val REQUEST_ENABLE_BT = 1
        const val REQUEST_BLUETOOTH_PERMISSION = 2
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Agrega este observer para iniciar animaciones cuando el layout esté listo
        val mainLayout = findViewById<ViewGroup>(R.id.drawer_layout)
        mainLayout.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mainLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                iniciarAnimaciones()
            }
        })
        charTextView = findViewById(R.id.charTextView)
        drawer = findViewById(R.id.drawer_layout)
        gridLetras = findViewById(R.id.gridLetras)
        signContainer = findViewById(R.id.signContainer)
        imgSign = findViewById(R.id.imgSign)
        txtEvaluation = findViewById(R.id.txtEvaluation)

        val btnClose: ImageButton = findViewById(R.id.btnClose)
        btnClose.setOnClickListener {
            signContainer.visibility = View.GONE
        }

        val letras = listOf(
            "A", "B", "C", "D", "E", "F", "G", "H", "I",
            "J", "K", "L", "M", "N", "Ñ", "O", "P", "Q",
            "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
        )

        gridLetras.adapter = LetrasAdapter(this, letras)
        gridLetras.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            mostrarSignoLetra(letras[position])
        }

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale("es", "ES")
            }
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(
            this,
            drawer,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
        navigationView.itemIconTintList = null

        verificarPermisosYEstadoBluetooth()
    }

    private fun mostrarSignoLetra(letra: String) {
        val nombreSigno = when (letra.toLowerCase()) {
            "ñ" -> "sign_enne"
            else -> "sign_${letra.toLowerCase()}"
        }

        txtEvaluation.visibility = View.GONE
        val resourceId = resources.getIdentifier(
            nombreSigno,
            "drawable",
            packageName
        )

        signContainer.setOnTouchListener { _, _ -> true }
        runOnUiThread {
            if (resourceId != 0) {
                imgSign.setImageResource(resourceId)
                signContainer.visibility = View.VISIBLE
                tts.speak(letra, TextToSpeech.QUEUE_FLUSH, null, null)
                txtEvaluation.text = "Evaluación: ${obtenerEvaluacionAleatoria()}"
            } else {
                Toast.makeText(this, "Seña no disponible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun obtenerEvaluacionAleatoria(): String {
        return when ((0..2).random()) {
            0 -> "Bien"
            1 -> "Muy bien"
            else -> "Regular"
        }
    }

    private fun verificarPermisosYEstadoBluetooth() {
        if (bluetoothAdapter == null) {
            mostrarError("Este dispositivo no soporta Bluetooth")
            return
        }

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        ),
                        REQUEST_BLUETOOTH_PERMISSION
                    )
                    return
                }
            }

            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_BLUETOOTH_PERMISSION
                )
                return
            }
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }
            } else {
                @Suppress("DEPRECATION")
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
            return
        }

        conectarDispositivoBluetooth()
    }
    private fun iniciarAnimaciones() {
        // Animación para el título
        val titulo = findViewById<TextView>(R.id.charTextView)
        titulo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))

        // Animación para el GridView
        gridLetras.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))

        // Animación para los elementos del Navigation Drawer
        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.animate()
            .translationX(0f)
            .setStartDelay(300)
            .setDuration(500)
            .start()

        // Animación escalado para el ícono de señas
        imgSign.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(500)
            .setDuration(600)
            .start()

    }

    private fun conectarDispositivoBluetooth() {
        try {
            val device: BluetoothDevice? = bluetoothAdapter?.bondedDevices?.find {
                it.name.equals(deviceName, ignoreCase = true)
            }

            if (device == null) {
                mostrarError("Dispositivo $deviceName no encontrado")
                return
            }

            val socket = device.createRfcommSocketToServiceRecord(uuidBt)
            socket.connect()

            bluetoothSocket = socket
            inputStream = socket.inputStream

            comenzarLecturaContinua()
            mostrarMensaje("Conectado a ${device.name}")

        } catch (e: SecurityException) {
            mostrarError("Error de permisos: ${e.message}")
        } catch (e: IOException) {
            mostrarError("Error de conexión: ${e.message}")
        }
    }

    private fun comenzarLecturaContinua() {
        mantenerHiloActivo = true
        readThread?.interrupt()

        readThread = Thread {
            val buffer = ByteArray(1024)
            val sb = StringBuilder()

            while (mantenerHiloActivo && !Thread.currentThread().isInterrupted) {
                try {
                    val bytesLeidos = inputStream?.read(buffer) ?: -1
                    if (bytesLeidos > 0) {
                        val chunk = String(buffer, 0, bytesLeidos, Charsets.UTF_8)
                        sb.append(chunk)

                        var fin: Int
                        while (sb.indexOf("\n").also { fin = it } != -1) {
                            var linea = sb.substring(0, fin).trim()
                            sb.delete(0, fin + 1)
                            linea = linea.replace(Regex("[^\\p{Print}\\r\\n]"), "")
                            if (linea.isNotEmpty()) {
                                procesarDatosRecibidos(linea)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (mantenerHiloActivo) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Error en lectura: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    break
                }
            }
        }.apply { start() }
    }

    private fun procesarDatosRecibidos(datos: String) {
        runOnUiThread {
            charTextView.text = datos
            tts.speak(datos, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSION -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    verificarPermisosYEstadoBluetooth()
                } else {
                    mostrarError("Permisos necesarios")
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {}
            R.id.nav_second -> startActivity(Intent(this, SecondActivity::class.java))
        }
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            conectarDispositivoBluetooth()
        }
    }

    private fun mostrarError(mensaje: String) {
        runOnUiThread {
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }
    }

    private fun mostrarMensaje(mensaje: String) {
        runOnUiThread {
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        mantenerHiloActivo = false
        readThread?.interrupt()
        tts.stop()
        tts.shutdown()
        bluetoothSocket?.close()
        super.onDestroy()
    }
}
