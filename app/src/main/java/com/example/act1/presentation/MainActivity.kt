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
import android.widget.*
import androidx.activity.ComponentActivity
import com.example.act1.R
import com.google.android.gms.wearable.*
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.random.Random

class MainActivity : ComponentActivity(), SensorEventListener,
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var heartRateSensor: Sensor? = null
    private var nodeID: String? = null

    private val PAYLOAD_PATH = "/APP_OPEN"
    private var isMonitoring = false
    private var isMoving = true
    private var lastHeartRate = 0f
    private var heartRateAvailable = false

    private val handler = Handler(Looper.getMainLooper())
    private val inactivityTimeout = 10_000L
    private val MESSAGE_INTERVAL = 500L
    private var lastMessageSentTime = 0L

    private lateinit var botonMonitoreo: Button
    private lateinit var alertContainer: LinearLayout
    private lateinit var alertIcon: ImageView
    private lateinit var alertText: TextView
    private lateinit var sensorDataText: TextView

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        heartRateAvailable = heartRateSensor != null

        getConnectedNode()

        botonMonitoreo = findViewById(R.id.dess)
        alertContainer = findViewById(R.id.alert_container)
        alertIcon = findViewById(R.id.alert_icon)
        alertText = findViewById(R.id.alert_text)
        sensorDataText = findViewById(R.id.sensor_data)

        botonMonitoreo.setOnClickListener {
            showMonitoringMessage()
            startMonitoring()
        }

        alertContainer.setOnClickListener {
            alertContainer.visibility = View.GONE
            botonMonitoreo.visibility = View.VISIBLE
        }

        logAllSensors()
    }

    private fun showMonitoringMessage() {
        botonMonitoreo.visibility = View.GONE
        alertContainer.visibility = View.VISIBLE
        alertText.text = "Detectando movimiento..."
        alertIcon.setImageResource(R.drawable.ic_clock)
        sensorDataText.text = "Esperando datos..."
    }

    private fun showWakeUpMessage() {
        alertText.text = "¡Es hora de levantarse!"
        alertIcon.setImageResource(R.drawable.ic_alert)
        sensorDataText.text = ""
    }

    private fun startMonitoring() {
        isMonitoring = true
        isMoving = true
        lastX = 0f
        lastY = 0f
        lastZ = 0f

        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }

        if (heartRateAvailable) {
            heartRateSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }

        handler.postDelayed({
            if (!isMoving) {
                runOnUiThread { showWakeUpMessage() }
            } else {
                runOnUiThread {
                    alertContainer.visibility = View.GONE
                    botonMonitoreo.visibility = View.VISIBLE
                    Toast.makeText(this, "Movimiento detectado. Todo bien 👍", Toast.LENGTH_SHORT).show()
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

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
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


                val ritmo = if (lastHeartRate > 0f) lastHeartRate else Random.nextInt(60, 90).toFloat()

                val json = JSONObject()
                json.put("movimiento", movimiento)
                json.put("x", x)
                json.put("y", y)
                json.put("z", z)
                json.put("ritmo_cardiaco", ritmo)

                val mensaje = json.toString()

                sensorDataText.text = """
                    Movimiento: $movimiento
                    x: ${"%.2f".format(x)}
                    y: ${"%.2f".format(y)}
                    z: ${"%.2f".format(z)}
                    Ritmo: ${"%.1f".format(ritmo)} bpm
                """.trimIndent()

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastMessageSentTime > MESSAGE_INTERVAL) {
                    sendMessage(mensaje)
                    lastMessageSentTime = currentTime
                }
            }

            Sensor.TYPE_HEART_RATE -> {
                val heartRate = event.values[0]
                if (heartRate > 0) {
                    lastHeartRate = heartRate
                    Log.d("HeartRate", "Sensor HR detectado: $heartRate")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        stopMonitoring()
        Wearable.getDataClient(this).removeListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getCapabilityClient(this).removeListener(this)
    }

    override fun onResume() {
        super.onResume()
        getConnectedNode()
        Wearable.getDataClient(this).addListener(this)
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getCapabilityClient(this).addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
    }

    override fun onDataChanged(p0: DataEventBuffer) {}
    override fun onCapabilityChanged(p0: CapabilityInfo) {}

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val message = String(messageEvent.data, StandardCharsets.UTF_8)
        if (messageEvent.path == PAYLOAD_PATH && message == "SINCHOMIPA") {
            runOnUiThread {
                alertText.text = "Conectado al teléfono"
                alertIcon.setImageResource(R.drawable.ic_check)
                alertContainer.visibility = View.VISIBLE
                botonMonitoreo.visibility = View.GONE
            }
        }
    }

    private fun sendMessage(message: String) {
        if (nodeID == null) {
            getConnectedNode()
            handler.postDelayed({ sendMessage(message) }, 1000)
            return
        }

        Wearable.getMessageClient(this)
            .sendMessage(nodeID!!, "/MOVIMIENTO", message.toByteArray())
            .addOnSuccessListener { Log.d("SensorData", "✅ Mensaje enviado.") }
            .addOnFailureListener { e -> Log.e("SensorData", "❌ Error: ${e.message}") }
    }

    private fun getConnectedNode() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                nodeID = nodes[0].id
                Log.d("getConnectedNode", "Nodo conectado: $nodeID")
            }
        }
    }

    private fun logAllSensors() {
        val sensores = sensorManager.getSensorList(Sensor.TYPE_ALL)
        for (sensor in sensores) {
            Log.d("SENSORES", "Sensor: ${sensor.name}, Tipo: ${sensor.type}")
        }
    }
}
