package com.kayser.areascan.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.kayser.areascan.R
import com.kayser.areascan.sensor.HardIronCalibrator

/**
 * Tarama Ayarları paneli.
 * Thuban'daki "Tarama Ayarları" (FramgentAreasScanningModeSettings) karşılığı —
 * hard-iron kalibrasyon başlatma, ARCore aç/kapa ve tarama oturumunu temizleme.
 *
 * Kalibrasyon UI'ı burada basit tutuldu; gerçek kalibrasyon örnekleri AreaScanActivity'den
 * gelen sensör akışına bağlanmalı (bu fragment sadece başlat/durdur ve durum göstergesi sunar).
 */
class FragmentScanningModeSettings : BaseSettingsFragment() {

    private val calibrator = HardIronCalibrator()
    private var calibrating = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_scanning_mode_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val statusText = view.findViewById<TextView>(R.id.calibrationStatusText)
        val startCalibrationButton = view.findViewById<MaterialButton>(R.id.startCalibrationButton)
        val arcoreSwitch = view.findViewById<MaterialSwitch>(R.id.arcoreSwitch)
        val clearScanButton = view.findViewById<MaterialButton>(R.id.clearScanButton)

        arcoreSwitch.isChecked = viewModel.settings.value.arcoreEnabled

        startCalibrationButton.setOnClickListener {
            calibrating = !calibrating
            if (calibrating) {
                calibrator.reset()
                statusText.text = getString(R.string.calibration_in_progress)
                startCalibrationButton.text = "Kalibrasyonu Durdur"
            } else {
                statusText.text = if (calibrator.isSufficient()) {
                    getString(R.string.calibration_complete)
                } else {
                    "Yetersiz veri — daha fazla hareket gerekli"
                }
                startCalibrationButton.text = "Kalibrasyonu Başlat"
            }
        }

        arcoreSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.updateSettings { it.copy(arcoreEnabled = checked) }
        }

        clearScanButton.setOnClickListener {
            viewModel.clearScan()
        }
    }

    /** AreaScanActivity, kalibrasyon açıkken her ham örneği bu metoda iletmeli. */
    fun feedCalibrationSample(x: Float, y: Float, z: Float) {
        if (calibrating) calibrator.addSample(x, y, z)
    }
}
