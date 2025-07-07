package com.example.act1.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.act1.R
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import kotlin.math.abs

class MainActivity : ComponentActivity(), SensorEventListener, DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var activityContext: Context? = null
    private var nodeID: String? = null  // Ahora es nullable  para evitar errores
    private val PAYLOAD_PATH = "/APP_OPEN"

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var isMonitoring = false
    private var isMoving = true

    private val inactivityTimeout = 10_000L // 10 segundos
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var context: Context

    private var lastMessageSentTime = 0L
    private val MESSAGE_INTERVAL = 500L // ms, puedes ajustar (500 ms = 0.5 seg)


    private lateinit var botonMonitoreo: Button
    private lateinit var alertContainer: LinearLayout
    private lateinit var alertIcon: ImageView
    private lateinit var alertText: TextView
    private lateinit var sensorDataText: TextView  // <-- NUEVO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getConnectedNode()

        context = this
        activityContext = this
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        botonMonitoreo = findViewById(R.id.dess)
        alertContainer = findViewById(R.id.alert_container)
        alertIcon = findViewById(R.id.alert_icon)
        alertText = findViewById(R.id.alert_text)
        sensorDataText = findViewById(R.id.sensor_data)  // <-- NUEVO

        botonMonitoreo.setOnClickListener {
            showMonitoringMessage()
            startMonitoring()
        }

        alertContainer.setOnClickListener {
            alertContainer.visibility = View.GONE
            botonMonitoreo.visibility = View.VISIBLE
        }
    }

    private fun showMonitoringMessage() {
        botonMonitoreo.visibility = View.GONE
        alertContainer.visibility = View.VISIBLE
        alertText.text = "Detectando movimiento por 10 segundos..."
        alertIcon.setImageResource(R.drawable.ic_clock) // tu ícono de reloj
        sensorDataText.text = "Esperando datos..."  // <-- Reiniciar lectura
    }

    private fun showWakeUpMessage() {
        alertText.text = "¡Es hora de levantarse!"
        alertIcon.setImageResource(R.drawable.ic_alert) // tu ícono de alerta
        sensorDataText.text = "" // Limpiar lectura
    }

    private fun startMonitoring() {
        isMonitoring = true
        isMoving = true
        lastX = 0f
        lastY = 0f
        lastZ = 0f

        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        handler.postDelayed({
            if (!isMoving) {
                runOnUiThread {
                    showWakeUpMessage()
                }
            } else {
                runOnUiThread {
                    alertContainer.visibility = View.GONE
                    botonMonitoreo.visibility = View.VISIBLE
                    Toast.makeText(context, "Movimiento detectado. Todo bien 👍", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            stopMonitoring()
        }, inactivityTimeout)
    }

    private fun stopMonitoring() {
        isMonitoring = false
        sensorManager.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isMonitoring) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val deltaX = abs(x - lastX)
        val deltaY = abs(y - lastY)
        val deltaZ = abs(z - lastZ)

        lastX = x
        lastY = y
        lastZ = z

        isMoving = deltaX > 0.5 || deltaY > 0.5 || deltaZ > 0.5

        val movimiento = if (isMoving) "Detectado" else "No detectado"

        // 📌 Aquí creas el JSON
        val json = JSONObject()
        json.put("movimiento", movimiento)
        json.put("x", "%.2f".format(x))
        json.put("y", "%.2f".format(y))
        json.put("z", "%.2f".format(z))

        val mensaje = json.toString()

        sensorDataText.text = """
        Movimiento: $movimiento
        x: ${"%.2f".format(x)}
        y: ${"%.2f".format(y)}
        z: ${"%.2f".format(z)}
    """.trimIndent()

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMessageSentTime > MESSAGE_INTERVAL) {
            sendMessage(mensaje) // ← envías el JSON como string
            lastMessageSentTime = currentTime
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se usa
    }


    override fun onPause() {
        super.onPause()
        stopMonitoring()
        try {
            Wearable.getDataClient(activityContext!!).removeListener(this)
            Wearable.getMessageClient(activityContext!!).removeListener(this)
            Wearable.getCapabilityClient(activityContext!!).removeListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()

        getConnectedNode() // 🔁 Reconectar nodo al reanudar

        try {
            Wearable.getDataClient(activityContext!!).addListener(this)
            Wearable.getMessageClient(activityContext!!).addListener(this)
            Wearable.getCapabilityClient(activityContext!!)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDataChanged(p0: DataEventBuffer) {

    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val message = String(messageEvent.data, StandardCharsets.UTF_8)
        Log.d("onMessageReceived", "Mensaje recibido desde ${messageEvent.sourceNodeId}")
        Log.d("onMessageReceived", "Payload: $message")

        if (messageEvent.path == PAYLOAD_PATH && message == "SINCHOMIPA") {
            runOnUiThread {
                alertText.text = "Conectado al teléfono"
                alertIcon.setImageResource(R.drawable.ic_check) // usa un ícono de check verde si tienes
                alertContainer.visibility = View.VISIBLE
                botonMonitoreo.visibility = View.GONE
            }
        }
    }


    override fun onCapabilityChanged(p0: CapabilityInfo) {

    }

    private fun sendMessage(message: String) {
        if (nodeID == null) {
            Log.e("sendMessage", "nodeID no inicializado, reintentando en 1 segundo...")
            getConnectedNode()
            handler.postDelayed({ sendMessage(message) }, 1000) // 🔁 Reintenta
            return
        }

        Wearable.getMessageClient(activityContext!!)
            .sendMessage(nodeID!!, "/MOVIMIENTO", message.toByteArray())
            .addOnSuccessListener {
                Log.d("SensorData", "✅ Mensaje de movimiento enviado al teléfono.")
            }
            .addOnFailureListener { e ->
                Log.e("SensorData", "❌ Error al enviar mensaje: ${e.message}")
            }
    }


    private fun getConnectedNode() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                nodeID = nodes[0].id
                Log.d("getConnectedNode", "Nodo conectado: $nodeID")
            } else {
                Log.d("getConnectedNode", "No se encontró nodo conectado")
            }

        }
    }


}