package com.example.act1

import android.app.Activity
import okhttp3.OkHttpClient
import okhttp3.Request
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private lateinit var conectar: Button
    private lateinit var mensaje: Button
    private var activityContext: Context? = null

    private lateinit var txtDatosMovimiento: TextView


    private val PAYLOAD_PATH = "/APP_OPEN"
    private var nodeID: String? = null  // Ahora es nullable  para evitar errores




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  // primero inflar layout
        txtDatosMovimiento = findViewById(R.id.txtDatosMovimiento)
        Wearable.getMessageClient(this).addListener(this)


        activityContext = this

        conectar = findViewById(R.id.boton)
        conectar.setOnClickListener {
            getNodes(this)
        }

        mensaje = findViewById(R.id.dess)
        mensaje.setOnClickListener {
            if (nodeID != null) {
                sendMessage()
            } else {
                Log.d("sendMessage", "Nodo no disponible aún. Conéctate primero.")
            }
        }

        // Puedes iniciar conexión automáticamente si lo deseas
        //getNodes(this)
    }

    private fun getNodes(context: Context) {
        launch(Dispatchers.Default) {
            val nodeList = Wearable.getNodeClient(context).connectedNodes
            try {
                val nodes = Tasks.await(nodeList)
                for (node in nodes) {
                    Log.d("NODO", "El id del nodo es: ${node.id}")
                    nodeID = node.id
                }

                if (nodeID == null) {
                    Log.d("NODO", "No se encontró ningún nodo conectado.")
                }

            } catch (exception: Exception) {
                Log.e("getNodes", "Error al obtener nodos: ${exception.message}")
            }
        }
    }

    private fun sendMessage() {
        nodeID?.let { id ->
            Wearable.getMessageClient(activityContext!!)
                .sendMessage(id, PAYLOAD_PATH, "SINCHOMIPA".toByteArray())
                .addOnSuccessListener {
                    Log.d("sendMessage", "Mensaje enviado correctamente")
                }
                .addOnFailureListener { e ->
                    Log.e("sendMessage", "Error al enviar mensaje: ${e.message}")
                }
        } ?: Log.d("sendMessage", "nodeID no inicializado")
    }

    override fun onDataChanged(p0: DataEventBuffer) {
        // Puedes implementar esto si necesitas trabajar con DataClient
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val message = String(messageEvent.data, StandardCharsets.UTF_8)

        when (messageEvent.path) {
            "/MOVIMIENTO" -> {
                Log.d("SensorData", "Datos de movimiento recibidos:\n$message")
                runOnUiThread {
                    txtDatosMovimiento.text = message
                }

                // Enviar los datos al servidor
                enviarDatosAlServidor(message)
            }

            "/APP_OPEN" -> {
                Log.d("onMessageReceived", "APP_OPEN recibido")
            }

            else -> {
                Log.d("onMessageReceived", "Otro mensaje: $message")
            }
        }
    }



    override fun onCapabilityChanged(p0: CapabilityInfo) {
        // No estás usando esto de momento
    }

    override fun onPause() {
        super.onPause()
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
        try {
            Wearable.getDataClient(activityContext!!).addListener(this)
            Wearable.getMessageClient(activityContext!!).addListener(this)
            Wearable.getCapabilityClient(activityContext!!)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun enviarDatosAlServidor(datos: String) {
        val url = "https://smart-3-2fii.onrender.com/api/movimientos"
        val client = OkHttpClient()

        try {
            val json = JSONObject(datos)  // Asegúrate que 'datos' es JSON válido
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("EnviarDatos", "❌ Error enviando datos: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        Log.d("EnviarDatos", " Datos enviados correctamente: ${response.code}")
                    } else {
                        Log.e("EnviarDatos", " Error en respuesta: ${response.code}")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("EnviarDatos", "❌ Error al crear JSON: ${e.message}")
        }
    }


}
