package com.kayser.areascan.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.kayser.areascan.R
import com.kayser.areascan.core.model.Colormap

/**
 * Yüzey Ayarları paneli.
 * Thuban'daki "Yüzey Ayarları" (FragmentSurfaceProperties) karşılığı — colormap,
 * opaklık, abartma çarpanı ve wireframe modu kontrolü.
 */
class FragmentSurfaceProperties : BaseSettingsFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_surface_properties, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.colormapToggleGroup)
        val opacityLabel = view.findViewById<TextView>(R.id.opacityLabel)
        val opacitySlider = view.findViewById<Slider>(R.id.opacitySlider)
        val exaggerationLabel = view.findViewById<TextView>(R.id.exaggerationLabel)
        val exaggerationSlider = view.findViewById<Slider>(R.id.exaggerationSlider)
        val wireframeSwitch = view.findViewById<MaterialSwitch>(R.id.wireframeSwitch)

        val current = viewModel.settings.value.surface

        val initialButtonId = when (current.colormap) {
            Colormap.JET -> R.id.colormapJetButton
            Colormap.GRAYSCALE -> R.id.colormapGrayscaleButton
            Colormap.VIRIDIS -> R.id.colormapViridisButton
            Colormap.THERMAL -> R.id.colormapThermalButton
            Colormap.RAINBOW -> R.id.colormapJetButton
        }
        toggleGroup.check(initialButtonId)

        opacitySlider.value = current.opacity
        exaggerationSlider.value = current.exaggerationFactor
        wireframeSwitch.isChecked = current.wireframe
        opacityLabel.text = "Opaklık: %d%%".format((current.opacity * 100).toInt())
        exaggerationLabel.text = "Abartma çarpanı: %.1fx".format(current.exaggerationFactor)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val colormap = when (checkedId) {
                R.id.colormapJetButton -> Colormap.JET
                R.id.colormapGrayscaleButton -> Colormap.GRAYSCALE
                R.id.colormapViridisButton -> Colormap.VIRIDIS
                R.id.colormapThermalButton -> Colormap.THERMAL
                else -> Colormap.JET
            }
            viewModel.updateSettings { it.copy(surface = it.surface.copy(colormap = colormap)) }
        }

        opacitySlider.addOnChangeListener { _, value, _ ->
            opacityLabel.text = "Opaklık: %d%%".format((value * 100).toInt())
            viewModel.updateSettings { it.copy(surface = it.surface.copy(opacity = value)) }
        }

        exaggerationSlider.addOnChangeListener { _, value, _ ->
            exaggerationLabel.text = "Abartma çarpanı: %.1fx".format(value)
            viewModel.updateSettings { it.copy(surface = it.surface.copy(exaggerationFactor = value)) }
        }

        wireframeSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.updateSettings { it.copy(surface = it.surface.copy(wireframe = checked)) }
        }
    }
}
