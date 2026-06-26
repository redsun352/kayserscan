package com.kayser.areascan.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.kayser.areascan.R
import com.kayser.areascan.core.model.RbfKernel

/**
 * Enterpolasyon (RBF) Ayarları paneli.
 * Thuban'daki "Enterpolasyon Ayarları" (FragmentRbfProperties) karşılığı — Hasan'ın
 * util/rbf_interp / RBF_Types çalışmasındaki çekirdek (kernel) seçimi mantığıyla örtüşür.
 */
class FragmentRbfProperties : BaseSettingsFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_rbf_properties, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.kernelToggleGroup)
        val epsilonLabel = view.findViewById<TextView>(R.id.epsilonLabel)
        val epsilonSlider = view.findViewById<Slider>(R.id.epsilonSlider)
        val smoothingLabel = view.findViewById<TextView>(R.id.smoothingLabel)
        val smoothingSlider = view.findViewById<Slider>(R.id.smoothingSlider)

        val current = viewModel.settings.value.rbf

        val initialButtonId = when (current.kernel) {
            RbfKernel.MULTIQUADRIC -> R.id.kernelMultiquadricButton
            RbfKernel.GAUSSIAN -> R.id.kernelGaussianButton
            RbfKernel.THIN_PLATE_SPLINE -> R.id.kernelThinPlateButton
            RbfKernel.LINEAR -> R.id.kernelLinearButton
            else -> R.id.kernelMultiquadricButton
        }
        toggleGroup.check(initialButtonId)

        epsilonSlider.value = current.epsilon
        smoothingSlider.value = current.smoothing
        epsilonLabel.text = "Epsilon (yayılım): %.1f".format(current.epsilon)
        smoothingLabel.text = "Düzleştirme: %.2f".format(current.smoothing)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val kernel = when (checkedId) {
                R.id.kernelMultiquadricButton -> RbfKernel.MULTIQUADRIC
                R.id.kernelGaussianButton -> RbfKernel.GAUSSIAN
                R.id.kernelThinPlateButton -> RbfKernel.THIN_PLATE_SPLINE
                R.id.kernelLinearButton -> RbfKernel.LINEAR
                else -> RbfKernel.MULTIQUADRIC
            }
            viewModel.updateSettings { it.copy(rbf = it.rbf.copy(kernel = kernel)) }
        }

        epsilonSlider.addOnChangeListener { _, value, _ ->
            epsilonLabel.text = "Epsilon (yayılım): %.1f".format(value)
            viewModel.updateSettings { it.copy(rbf = it.rbf.copy(epsilon = value)) }
        }

        smoothingSlider.addOnChangeListener { _, value, _ ->
            smoothingLabel.text = "Düzleştirme: %.2f".format(value)
            viewModel.updateSettings { it.copy(rbf = it.rbf.copy(smoothing = value)) }
        }
    }
}
