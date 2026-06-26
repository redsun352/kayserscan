package com.kayser.areascan.fragments

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.kayser.areascan.AreaScanViewModel

/**
 * Yedi ayar fragment'inin (Tarama/Grid/BoundingBox/Surface/RBF/Soil/MarchingCubes) ortak tabanı.
 * `activityViewModels()` ile AreaScanActivity'nin ViewModel'ini paylaşırlar, böylece bir
 * panelde yapılan değişiklik anında 3D görünüme yansır.
 */
abstract class BaseSettingsFragment : Fragment() {
    protected val viewModel: AreaScanViewModel by activityViewModels()
}
