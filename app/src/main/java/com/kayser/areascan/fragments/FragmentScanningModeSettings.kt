package com.kayser.areascan.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.kayser.areascan.R
import com.kayser.areascan.core.model.StepInterval
import com.kayser.areascan.core.model.SteppedScanPathPlanner
import com.kayser.areascan.sensor.HardIronCalibrator

/**
 * Tarama Ayarları paneli.
 * Thuban'daki "Tarama Ayarları" (FramgentAreasScanningModeSettings) karşılığı —
 * hard-iron kalibrasyon başlatma, ARCore aç/kapa, adımlı tarama yapılandırması ve
 * tarama oturumunu temizleme.
 *
 * Kalibrasyon UI'ı burada basit tutuldu; gerçek kalibrasyon örnekleri AreaScanActivity'den
 * gelen sensör akışına bağlanmalı (bu fragment sadece başlat/durdur ve durum göstergesi sunar).
 *
 * Adımlı tarama başlat/durdur butonunun gerçek davranışı (waypoint'e gitme, beep çalma,
 * "Ölçü Al" butonuyla entegrasyon) AreaScanActivity'de yönetilir; bu fragment sadece
 * ayarı (adım aralığı) ViewModel'e yazar ve başlatma isteğini iletir.
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
        val stepIntervalToggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.stepIntervalToggleGroup)
        val waypointCountText = view.findViewById<TextView>(R.id.waypointCountText)
        val startSteppedScanButton = view.findViewById<MaterialButton>(R.id.startSteppedScanButton)

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

        // --- Adımlı tarama ---
        val currentInterval = viewModel.settings.value.steppedScan.stepIntervalMeters
        val initialButtonId = when (currentInterval) {
            StepInterval.TEN_CM.meters -> R.id.stepInterval10cmButton
            StepInterval.FIFTY_CM.meters -> R.id.stepInterval50cmButton
            StepInterval.ONE_M.meters -> R.id.stepInterval1mButton
            else -> R.id.stepInterval20cmButton
        }
        stepIntervalToggleGroup.check(initialButtonId)
        updateWaypointPreview(waypointCountText, currentInterval)

        stepIntervalToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val interval = when (checkedId) {
                R.id.stepInterval10cmButton -> StepInterval.TEN_CM.meters
                R.id.stepInterval50cmButton -> StepInterval.FIFTY_CM.meters
                R.id.stepInterval1mButton -> StepInterval.ONE_M.meters
                else -> StepInterval.TWENTY_CM.meters
            }
            viewModel.updateSettings { it.copy(steppedScan = it.steppedScan.copy(stepIntervalMeters = interval)) }
            updateWaypointPreview(waypointCountText, interval)
        }

        startSteppedScanButton.setOnClickListener {
            if (viewModel.isSteppedScanRunning()) {
                viewModel.stopSteppedScan()
                startSteppedScanButton.text = getString(R.string.start_stepped_scan_text)
            } else {
                val count = viewModel.startSteppedScan()
                startSteppedScanButton.text = getString(R.string.stop_stepped_scan_text)
                waypointCountText.text = getString(R.string.waypoint_progress_format, 1, count)
            }
        }
    }

    private fun updateWaypointPreview(textView: TextView, intervalMeters: Float) {
        val grid = viewModel.settings.value.grid
        val count = SteppedScanPathPlanner.buildWaypoints(grid, intervalMeters).size
        textView.text = "Toplam durak sayısı: $count"
    }

    /** AreaScanActivity, kalibrasyon açıkken her ham örneği bu metoda iletmeli. */
    fun feedCalibrationSample(x: Float, y: Float, z: Float) {
        if (calibrating) calibrator.addSample(x, y, z)
    }
}
