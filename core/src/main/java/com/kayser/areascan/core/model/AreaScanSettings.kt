package com.kayser.areascan.core.model

/**
 * Yüzey Tarama ızgara (grid) ayarları.
 * Thuban Lodestar'daki "Izgara Ayarları" (FragmentGridProperties) panelinin karşılığıdır.
 */
data class GridConfig(
    /** Izgara genişliği (metre) */
    val widthMeters: Float = 5f,
    /** Izgara yüksekliği/derinliği (metre) */
    val heightMeters: Float = 5f,
    /** Bir satır/sütundaki hücre sayısı (çözünürlük) */
    val resolution: Int = 50,
    /** Izgara çizgilerinin görünürlüğü */
    val showGridLines: Boolean = true,
    /** Izgara hücre rengi opaklığı (0f..1f) */
    val gridOpacity: Float = 0.6f
) {
    val cellSizeX: Float get() = widthMeters / resolution
    val cellSizeY: Float get() = heightMeters / resolution
}

/**
 * Sınırlandırma kutusu (bounding box) ayarları.
 * Thuban'daki "Sınırlandırma Kutusu Ayarları" (FragmentBoundingBoxProperties) karşılığı.
 */
data class BoundingBoxConfig(
    val minX: Float = 0f,
    val maxX: Float = 5f,
    val minY: Float = 0f,
    val maxY: Float = 5f,
    val minDepth: Float = 0f,
    val maxDepth: Float = 2f,
    val visible: Boolean = true
)

/**
 * Yüzey (surface/heatmap) görselleştirme ayarları.
 * Thuban'daki "Yüzey Ayarları" (FragmentSurfaceProperties) karşılığı.
 */
data class SurfaceConfig(
    val colormap: Colormap = Colormap.JET,
    val opacity: Float = 1.0f,
    val wireframe: Boolean = false,
    val smoothShading: Boolean = true,
    /** Yükseklik/değer abartma çarpanı — küçük anomalileri görsel olarak belirginleştirmek için */
    val exaggerationFactor: Float = 1.0f
)

enum class Colormap {
    JET, GRAYSCALE, VIRIDIS, RAINBOW, THERMAL
}

/**
 * RBF (Radial Basis Function) enterpolasyon ayarları.
 * Thuban'daki "Enterpolasyon Ayarları" (FragmentRbfProperties) karşılığı.
 * Hasan'ın util/rbf_interp çalışmasındaki RBF_Types ile aynı aileden.
 */
data class RbfConfig(
    val kernel: RbfKernel = RbfKernel.MULTIQUADRIC,
    /** Şekil/yayılım parametresi (epsilon) */
    val epsilon: Float = 1.0f,
    /** Çıkış ızgarasının enterpolasyon çözünürlüğü çarpanı (orijinal grid'e göre) */
    val outputResolutionMultiplier: Int = 2,
    val smoothing: Float = 0f
)

enum class RbfKernel {
    LINEAR, CUBIC, THIN_PLATE_SPLINE, GAUSSIAN, MULTIQUADRIC, INVERSE_MULTIQUADRIC
}

/**
 * Toprak/zemin (soil) referans ayarları.
 * Thuban'daki "Toprak Ayarları" (FragmentSoilProperties) karşılığı.
 * Zemin manyetik taban seviyesini (baseline) tanımlar; anomaliler bu seviyeye göre hesaplanır.
 */
data class SoilConfig(
    val soilType: SoilType = SoilType.GENERIC,
    /** Ölçülen veya kullanıcı tarafından girilen zemin taban manyetik değeri (µT) */
    val baselineMagnitude: Float = 0f,
    /** Otomatik taban hesaplama açık mı (ilk N örneğin ortalaması alınır) */
    val autoBaseline: Boolean = true,
    val autoBaselineSampleCount: Int = 50
)

enum class SoilType {
    GENERIC, CLAY, SAND, LIMESTONE, VOLCANIC, ALLUVIAL
}

/**
 * Marching Cubes / hacimsel (isosurface) ayarları.
 * Thuban'daki "Hacim Ayarları" (FragmentMarchingCubesProperties) karşılığı.
 */
data class MarchingCubesConfig(
    /** Isosurface'in çıkarılacağı eşik (threshold) değeri */
    val isoLevel: Float = 0.5f,
    /** 3B voxel ızgarasının her eksendeki hücre sayısı */
    val voxelResolution: Int = 32,
    val smoothNormals: Boolean = true
)

/** Tüm Yüzey Tarama ayarlarını bir arada tutan kapsayıcı (AreaScanActivity'nin tuttuğu state). */
data class AreaScanSettings(
    val grid: GridConfig = GridConfig(),
    val boundingBox: BoundingBoxConfig = BoundingBoxConfig(),
    val surface: SurfaceConfig = SurfaceConfig(),
    val rbf: RbfConfig = RbfConfig(),
    val soil: SoilConfig = SoilConfig(),
    val marchingCubes: MarchingCubesConfig = MarchingCubesConfig(),
    val arcoreEnabled: Boolean = false
)
