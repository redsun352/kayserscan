package com.kayser.areascan.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.kayser.areascan.R

/**
 * Toprak Ayarları paneli.
 * Thuban'daki "Toprak Ayarları" (FragmentSoilProperties) karşılığı — zemin manyetik taban
 * seviyesi otomatik/manuel hesaplama kontrolü.
 */
class FragmentSoilProperties : BaseSettingsFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_soil_properties, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val autoBaselineSwitch = view.findViewById<MaterialSwitch>(R.id.autoBaselineSwitch)
        val sampleCountLabel = view.findViewById<TextView>(R.id.sampleCountLabel)
        val sampleCountSlider = view.findViewById<Slider>(R.id.sampleCountSlider)
        val manualBaselineLabel = view.findViewById<TextView>(R.id.manualBaselineLabel)
        val manualBaselineSlider = view.findViewById<Slider>(R.id.manualBaselineSlider)

        val current = viewModel.settings.value.soil
        autoBaselineSwitch.isChecked = current.autoBaseline
        sampleCountSlider.value = current.autoBaselineSampleCount.toFloat()
        manualBaselineSlider.value = current.baselineMagnitude
        manualBaselineSlider.isEnabled = !current.autoBaseline

        sampleCountLabel.text = "Örnekleme sayısı: ${current.autoBaselineSampleCount}"
        manualBaselineLabel.text = "Manuel taban değeri: %.1f µT".format(current.baselineMagnitude)

        autoBaselineSwitch.setOnCheckedChangeListener { _, checked ->
            manualBaselineSlider.isEnabled = !checked
            viewModel.updateSettings { it.copy(soil = it.soil.copy(autoBaseline = checked)) }
        }

        sampleCountSlider.addOnChangeListener { _, value, _ ->
            sampleCountLabel.text = "Örnekleme sayısı: ${value.toInt()}"
            viewModel.updateSettings { it.copy(soil = it.soil.copy(autoBaselineSampleCount = value.toInt())) }
        }

        manualBaselineSlider.addOnChangeListener { _, value, _ ->
            manualBaselineLabel.text = "Manuel taban değeri: %.1f µT".format(value)
            viewModel.updateSettings { it.copy(soil = it.soil.copy(baselineMagnitude = value)) }
        }
    }
}
