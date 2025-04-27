package com.example.comunicate_apptest

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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

    // Componentes de UI
    private lateinit var charTextView: TextView
    private lateinit var drawer: DrawerLayout
    private lateinit var tts: TextToSpeech
    private lateinit var btnPausa: Button

    // Variables Bluetooth
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var readThread: Thread? = null
    private var mantenerHiloActivo = true

    // Configuración Bluetooth
    private val deviceName = "HC-05"
    private val uuidBt = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    // Estados y buffers
    private var lecturaPausada = false
    private val bufferPausa = StringBuilder()

    // Constantes para permisos
    private companion object {
        const val REQUEST_ENABLE_BT = 1
        const val REQUEST_BLUETOOTH_PERMISSION = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar componentes de UI
        charTextView = findViewById(R.id.charTextView)
        drawer = findViewById(R.id.drawer_layout)
        btnPausa = findViewById(R.id.btnPausa)

        // Configurar TextToSpeech
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale("es", "ES")
            }
        }

        // Configurar barra de navegación
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(this, drawer, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        // Verificar permisos y estado Bluetooth
        verificarPermisosYEstadoBluetooth()
    }

    private fun verificarPermisosYEstadoBluetooth() {
        // Verificar si el dispositivo soporta Bluetooth
        if (bluetoothAdapter == null) {
            mostrarError("Este dispositivo no soporta Bluetooth")
            return
        }

        // Verificar permisos según versión de Android
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                        REQUEST_BLUETOOTH_PERMISSION
                    )
                    return
                }
            }
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_BLUETOOTH_PERMISSION
                )
                return
            }
        }

        // Verificar si Bluetooth está activado
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }
            } else {
                @Suppress("DEPRECATION")
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
            return
        }

        // Todo está listo para conectar
        conectarDispositivoBluetooth()
    }

    private fun conectarDispositivoBluetooth() {
        try {
            // Buscar dispositivo HC-05 emparejado
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter?.bondedDevices?.find { it.name.equals(deviceName, ignoreCase = true) }
                } else {
                    null
                }
            } else {
                bluetoothAdapter?.bondedDevices?.find { it.name.equals(deviceName, ignoreCase = true) }
            }

            if (device == null) {
                mostrarError("Dispositivo $deviceName no encontrado. Empareje primero el dispositivo.")
                return
            }

            // Establecer conexión con el dispositivo
            val socket = device.createRfcommSocketToServiceRecord(uuidBt)
            socket.connect()

            // Asignar socket e inputStream a las variables de clase
            bluetoothSocket = socket
            inputStream = socket.inputStream

            // Iniciar hilo de lectura y mostrar mensaje
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
                        // decodificar siempre en UTF-8
                        val chunk = String(buffer, 0, bytesLeidos, Charsets.UTF_8)

                        if (lecturaPausada) {
                            // Acumula durante la pausa
                            bufferPausa.append(chunk)
                        } else {
                            // Procesa fragmentos hasta la primera línea completa
                            sb.append(chunk)
                            var fin: Int
                            while (sb.indexOf("\n").also { fin = it } != -1) {
                                var linea = sb.substring(0, fin).trim()
                                sb.delete(0, fin + 1)
                                // Limpia caracteres no imprimibles
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

    // Función para alternar pausa/reanudar
    fun togglePausaLectura(view: View) {
        lecturaPausada = !lecturaPausada

        runOnUiThread {
            if (lecturaPausada) {
                btnPausa.text = "Reanudar Lectura"
                btnPausa.setBackgroundColor(Color.GREEN)
                Toast.makeText(this, "Lectura pausada", Toast.LENGTH_SHORT).show()
            } else {
                btnPausa.text = "Pausar Lectura"
                btnPausa.setBackgroundColor(Color.RED)
                Toast.makeText(this, "Lectura reanudada", Toast.LENGTH_SHORT).show()

                // Procesa TODO lo acumulado durante la pausa
                if (bufferPausa.isNotEmpty()) {
                    procesarDatosRecibidos(
                        bufferPausa
                            .toString()
                            .replace(Regex("[^\\p{Print}\\r\\n]"), "")
                            .trim()
                    )
                    bufferPausa.clear()
                }
            }
        }
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
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    verificarPermisosYEstadoBluetooth()
                } else {
                    mostrarError("La aplicación necesita permisos para funcionar correctamente")
                }
            }
        }
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
            charTextView.text = "Error: $mensaje"
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
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {}
            R.id.nav_second -> {
                val intent = Intent(this, SecondActivity::class.java)
                startActivity(intent)
            }
        }
        drawer.closeDrawer(GravityCompat.START)
        return true
    }
}