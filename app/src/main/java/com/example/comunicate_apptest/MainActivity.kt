package com.example.comunicate_apptest

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
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

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    // UI Components
    private lateinit var drawer: DrawerLayout
    private lateinit var btnPausa: Button
    private lateinit var tts: TextToSpeech
    private lateinit var gridLetras: GridView
    private lateinit var signContainer: LinearLayout
    private lateinit var imgSign: ImageView  // <-- ¡Faltaba un salto de línea aquí!
    private lateinit var txtEvaluation: TextView

    // Bluetooth
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var readThread: Thread? = null
    private var mantenerHiloActivo = true
    private var lecturaPausada = false
    private val bufferPausa = StringBuilder()

    companion object {
        const val REQUEST_ENABLE_BT = 1
        const val REQUEST_BLUETOOTH_PERMISSION = 2
        const val DEVICE_NAME = "HC-05"
        val UUID_BT: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar componentes UI PRIMERO
        drawer = findViewById(R.id.drawer_layout)
        btnPausa = findViewById(R.id.btnPausa)
        gridLetras = findViewById(R.id.gridLetras) // ✅ Inicializado correctamente
        signContainer = findViewById(R.id.signContainer)
        imgSign = findViewById(R.id.imgSign)
        txtEvaluation = findViewById(R.id.txtEvaluation)
        val btnClose: ImageButton = findViewById(R.id.btnClose)
        btnClose.setOnClickListener {
            signContainer.visibility = View.GONE
        }
        // Configurar GridView DESPUÉS de inicializar
        val letras = listOf(
            "A", "B", "C", "D", "E", "F", "G", "H", "I",
            "J", "K", "L", "M", "N", "Ñ", "O", "P", "Q",
            "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
        )

        gridLetras.adapter = LetrasAdapter(this, letras) // ✅ Adaptador asignado después de inicializar

        gridLetras.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            mostrarSignoLetra(letras[position])
        }

        // Configurar TTS
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale("es", "ES")
            }
        }

        // Configurar Navigation Drawer
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
        signContainer.setOnTouchListener { _, _ -> true } // Intercepta todos los toques
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

        if (!permisosBluetoothConcedidos()) {
            pedirPermisosBluetooth()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
        }

        conectarDispositivoBluetooth()
    }

    private fun permisosBluetoothConcedidos(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun pedirPermisosBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                REQUEST_BLUETOOTH_PERMISSION
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_BLUETOOTH_PERMISSION
            )
        }
    }

    private fun conectarDispositivoBluetooth() {
        try {
            val device: BluetoothDevice? = if (permisosBluetoothConcedidos()) {
                bluetoothAdapter?.bondedDevices?.find { it.name.equals(DEVICE_NAME, ignoreCase = true) }
            } else null

            if (device == null) {
                mostrarError("Dispositivo $DEVICE_NAME no encontrado. Empareje primero el dispositivo.")
                return
            }

            val socket = device.createRfcommSocketToServiceRecord(UUID_BT)
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

                        if (lecturaPausada) {
                            bufferPausa.append(chunk)
                        } else {
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

    fun togglePausaLectura(view: View) {
        lecturaPausada = !lecturaPausada

        if (lecturaPausada) {
            btnPausa.text = "Reanudar Lectura"
            btnPausa.setBackgroundColor(Color.GREEN)
            Toast.makeText(this, "Lectura pausada", Toast.LENGTH_SHORT).show()
        } else {
            btnPausa.text = "Pausar Lectura"
            btnPausa.setBackgroundColor(Color.RED)
            Toast.makeText(this, "Lectura reanudada", Toast.LENGTH_SHORT).show()

            if (bufferPausa.isNotEmpty()) {
                val datosLimpios = bufferPausa.toString()
                    .replace(Regex("[^\\p{Print}\\r\\n]"), "")
                    .trim()
                procesarDatosRecibidos(datosLimpios)
                bufferPausa.clear()
            }
        }
    }

    private fun procesarDatosRecibidos(datos: String) {
        runOnUiThread {
            mostrarSignoLetra(datos.trim())
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {}
            R.id.nav_second -> {
                startActivity(Intent(this, SecondActivity::class.java))
            }
        }
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                conectarDispositivoBluetooth()
            } else {
                mostrarError("El Bluetooth debe estar activado para usar esta función")
            }
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

        bluetoothSocket?.let {
            try {
                it.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        super.onDestroy()
    }
}
