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
 * Hacim Ayarları paneli.
 * Thuban'daki "Hacim Ayarları" (FragmentMarchingCubesProperties) karşılığı — isosurface
 * eşik seviyesi ve voxel çözünürlüğü kontrolü.
 */
class FragmentMarchingCubesProperties : BaseSettingsFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_marching_cubes_properties, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isoLevelLabel = view.findViewById<TextView>(R.id.isoLevelLabel)
        val isoLevelSlider = view.findViewById<Slider>(R.id.isoLevelSlider)
        val voxelResolutionLabel = view.findViewById<TextView>(R.id.voxelResolutionLabel)
        val voxelResolutionSlider = view.findViewById<Slider>(R.id.voxelResolutionSlider)
        val smoothNormalsSwitch = view.findViewById<MaterialSwitch>(R.id.smoothNormalsSwitch)

        val current = viewModel.settings.value.marchingCubes
        isoLevelSlider.value = current.isoLevel
        voxelResolutionSlider.value = current.voxelResolution.toFloat()
        smoothNormalsSwitch.isChecked = current.smoothNormals

        isoLevelLabel.text = "Iso seviyesi: %.2f".format(current.isoLevel)
        voxelResolutionLabel.text = "Voxel çözünürlüğü: ${current.voxelResolution}"

        isoLevelSlider.addOnChangeListener { _, value, _ ->
            isoLevelLabel.text = "Iso seviyesi: %.2f".format(value)
            viewModel.updateSettings { it.copy(marchingCubes = it.marchingCubes.copy(isoLevel = value)) }
        }

        voxelResolutionSlider.addOnChangeListener { _, value, _ ->
            voxelResolutionLabel.text = "Voxel çözünürlüğü: ${value.toInt()}"
            viewModel.updateSettings {
                it.copy(marchingCubes = it.marchingCubes.copy(voxelResolution = value.toInt()))
            }
        }

        smoothNormalsSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.updateSettings { it.copy(marchingCubes = it.marchingCubes.copy(smoothNormals = checked)) }
        }
    }
}
