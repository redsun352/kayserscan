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
 * Izgara Ayarları paneli.
 * Thuban'daki "Izgara Ayarları" (FragmentGridProperties) karşılığı — genişlik, yükseklik,
 * çözünürlük ve ızgara çizgi görünürlüğünü kontrol eder.
 */
class FragmentGridProperties : BaseSettingsFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_grid_properties, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val widthLabel = view.findViewById<TextView>(R.id.widthLabel)
        val widthSlider = view.findViewById<Slider>(R.id.widthSlider)
        val heightLabel = view.findViewById<TextView>(R.id.heightLabel)
        val heightSlider = view.findViewById<Slider>(R.id.heightSlider)
        val resolutionLabel = view.findViewById<TextView>(R.id.resolutionLabel)
        val resolutionSlider = view.findViewById<Slider>(R.id.resolutionSlider)
        val gridLinesSwitch = view.findViewById<MaterialSwitch>(R.id.showGridLinesSwitch)

        val current = viewModel.settings.value.grid
        widthSlider.value = current.widthMeters
        heightSlider.value = current.heightMeters
        resolutionSlider.value = current.resolution.toFloat()
        gridLinesSwitch.isChecked = current.showGridLines

        widthLabel.text = "Genişlik: %.1f m".format(current.widthMeters)
        heightLabel.text = "Yükseklik: %.1f m".format(current.heightMeters)
        resolutionLabel.text = "Çözünürlük: ${current.resolution}"

        widthSlider.addOnChangeListener { _, value, _ ->
            widthLabel.text = "Genişlik: %.1f m".format(value)
            viewModel.updateSettings { it.copy(grid = it.grid.copy(widthMeters = value)) }
        }
        heightSlider.addOnChangeListener { _, value, _ ->
            heightLabel.text = "Yükseklik: %.1f m".format(value)
            viewModel.updateSettings { it.copy(grid = it.grid.copy(heightMeters = value)) }
        }
        resolutionSlider.addOnChangeListener { _, value, _ ->
            resolutionLabel.text = "Çözünürlük: ${value.toInt()}"
            viewModel.updateSettings { it.copy(grid = it.grid.copy(resolution = value.toInt())) }
        }
        gridLinesSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.updateSettings { it.copy(grid = it.grid.copy(showGridLines = checked)) }
        }
    }
}
