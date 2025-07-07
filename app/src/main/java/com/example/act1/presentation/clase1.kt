package com.example.act1.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.act1.R
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import  android.hardware.SensorEvent
import android.hardware.SensorEventListener
import  android.hardware.SensorManager
import androidx.core.app.ActivityCompat
import android.Manifest
import android.widget.TextView

class clase1: ComponentActivity(), SensorEventListener
{
    private lateinit var sensorManager: SensorManager
    private var sensor:Sensor?=null
    private var sensorType=Sensor.TYPE_HEART_RATE
    private var texto:TextView?=null


    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        sensorManager=getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor=sensorManager.getDefaultSensor(sensorType)

        setContentView(R.layout.ventana2)
        texto =findViewById(R.id.texto)
        startSensor()
    }

    private fun startSensor() {
        if (ActivityCompat.checkSelfPermission( this, Manifest.permission.BODY_SENSORS ) != PackageManager.PERMISSION_GRANTED
        ){  ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), 1001)
        return
        }
    if(sensor!=null){
        sensorManager.registerListener(this,sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }
}
    override fun onSensorChanged(event: SensorEvent?) {
if (event?.sensor?.type==sensorType){
    val lectura=event.values[0]
    texto?.text="lectura: $lectura"
}
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }

    override fun onPause() {
      super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onResume(){
        super.onResume()
        sensor?.also { pressure ->
            sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

}