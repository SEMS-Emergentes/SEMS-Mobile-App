package pe.edu.upc.sems.energy.presentation.meters

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.presentation.common.FormScaffold

@Composable
fun MeterScannerScreen(onBack: () -> Unit, onManual: () -> Unit, onScanned: (String, String?) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    FormScaffold(title = androidx.compose.ui.res.stringResource(R.string.meter_scan_title), onBack = onBack) {
        if (granted) CameraQrPreview { raw -> parseMeterQr(raw)?.let { onScanned(it.serial, it.model) } }
        else {
            Text(androidx.compose.ui.res.stringResource(R.string.meter_camera_explanation))
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(R.string.meter_camera_permission)) }
        }
        Button(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(R.string.meter_enter_manually)) }
    }
}

@Composable private fun CameraQrPreview(onValue: (String) -> Unit) {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current; var handled by remember { mutableStateOf(false) }
    AndroidView(factory = { androidContext ->
        PreviewView(androidContext).also { previewView ->
        val provider = ProcessCameraProvider.getInstance(context)
        provider.addListener({
            val cameraProvider = provider.get()
            val preview = androidx.camera.core.Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val scanner = BarcodeScanning.getClient()
            val analysis = ImageAnalysis.Builder().build().also { analyzer -> analyzer.setAnalyzer(ContextCompat.getMainExecutor(context)) { image ->
                val mediaImage = image.image
                if (mediaImage == null) image.close() else {
                    val input = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                    scanner.process(input).addOnSuccessListener { codes -> if (!handled) codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE && parseMeterQr(it.rawValue.orEmpty()) != null }?.rawValue?.let { value -> handled = true; onValue(value) } }.addOnCompleteListener { image.close() }
                }
            } }
            cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        }
    }, modifier = Modifier.fillMaxWidth().height(320.dp).padding(vertical = pe.edu.upc.sems.shared.designsystem.SemsSpacing.md))
}
