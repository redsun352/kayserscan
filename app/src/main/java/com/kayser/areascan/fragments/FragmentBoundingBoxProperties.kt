package com.kayser.areascan.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.kayser.areascan.R

/**
 * Sınırlandırma Kutusu Ayarları paneli.
 * Thuban'daki "Sınırlandırma Kutusu Ayarları" (FragmentBoundingBoxProperties) karşılığı.
 */
class FragmentBoundingBoxProperties : BaseSettingsFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bounding_box_properties, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val depthLabel = view.findViewById<TextView>(R.id.depthLabel)
        val depthRangeSlider = view.findViewById<RangeSlider>(R.id.depthRangeSlider)
        val visibleSwitch = view.findViewById<MaterialSwitch>(R.id.boundingBoxVisibleSwitch)

        val current = viewModel.settings.value.boundingBox
        depthRangeSlider.values = listOf(current.minDepth, current.maxDepth)
        visibleSwitch.isChecked = current.visible
        depthLabel.text = "Derinlik aralığı: %.1f – %.1f m".format(current.minDepth, current.maxDepth)

        depthRangeSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            val minDepth = values.getOrElse(0) { 0f }
            val maxDepth = values.getOrElse(1) { 2f }
            depthLabel.text = "Derinlik aralığı: %.1f – %.1f m".format(minDepth, maxDepth)
            viewModel.updateSettings {
                it.copy(boundingBox = it.boundingBox.copy(minDepth = minDepth, maxDepth = maxDepth))
            }
        }

        visibleSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.updateSettings { it.copy(boundingBox = it.boundingBox.copy(visible = checked)) }
        }
    }
}
