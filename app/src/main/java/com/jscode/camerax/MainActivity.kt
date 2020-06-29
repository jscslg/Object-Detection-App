package com.jscode.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.jscode.camerax.databinding.ActivityMainBinding
import com.jscode.camerax.ml.Objectmodel
import com.jscode.camerax.util.YuvToRgbConverter
import com.jscode.camerax.viewModel.MainViewModel
import com.jscode.camerax.viewModel.Recognition
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.model.Model
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 0
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA)
        private const val TAG = "MainActivity"
    }

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this).get(MainViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        val adapter = ListAdapter()
        binding.recognitionResults.adapter = adapter
        binding.recognitionResults.itemAnimator = null
        viewModel.recognitionList.observe(this, Observer {
            adapter.submitList(it)
        })
        binding.capture.setOnClickListener {
            if (binding.cameraPreview.isVisible) {
                binding.cameraPreview.bitmap?.let { it1 ->
                    imageAnalyzer?.clearAnalyzer()
                    binding.pic.setImageBitmap(it1)
                    viewModel.fade(binding.cameraPreview, binding.pic)
                    binding.capture.setImageDrawable(getDrawable(R.drawable.baseline_refresh_black_24dp))
                    processImage(it1)
                }
            } else {
                imageAnalyzer?.setAnalyzer(cameraExecutor, ImageAnalyzer(this) { items ->
                    viewModel.updateData(items)
                })
                viewModel.fade(binding.pic, binding.cameraPreview)
                binding.capture.setImageDrawable(getDrawable(R.drawable.baseline_camera_alt_black_24dp))
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Permissions not granted",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                .build()
            Log.i(TAG, "Start")

            // Select back camera
            imageAnalyzer = ImageAnalysis.Builder()
                // This sets the ideal size for the image to be analyse, CameraX will choose the
                // the most suitable resolution which may not be exactly the same or hold the same
                // aspect ratio
                .setTargetResolution(Size(224, 224))
                // How the Image Analyser should pipe in input, 1. every frame but drop no frame, or
                // 2. go to the latest frame and may drop some frame. The default is 2.
                // STRATEGY_KEEP_ONLY_LATEST. The following line is optional, kept here for clarity
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysisUseCase: ImageAnalysis ->
                    analysisUseCase.setAnalyzer(cameraExecutor, ImageAnalyzer(this) { items ->
                        viewModel.updateData(items)
                    })
                }
            val cameraSelector =
                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                    CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                preview?.setSurfaceProvider(binding.cameraPreview.createSurfaceProvider())
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun processImage(image: Bitmap) {
        val options = Model.Options.Builder().setDevice(Model.Device.GPU).build()
        val objectModel = Objectmodel.newInstance(applicationContext, options)
        val items = mutableListOf<Recognition>()
        val tfImage = TensorImage.fromBitmap(image)

        val outputs = objectModel.process(tfImage)
            .probabilityAsCategoryList.apply {
                sortByDescending { it.score } // Sort with highest confidence first
            }.take(3) // take the top results

        for (output in outputs) {
            if (output.score > 0) items.add(Recognition(output.label, output.score))
        }
        Log.i(TAG, "$items")
        viewModel.updateData(items)
    }

    private class ImageAnalyzer(
        ctx: Context,
        private val listener: (recognition: List<Recognition>) -> Unit
    ) :
        ImageAnalysis.Analyzer {

        private val options = Model.Options.Builder().setDevice(Model.Device.GPU).build()
        private val objectModel = Objectmodel.newInstance(ctx, options)

        override fun analyze(imageProxy: ImageProxy) {
            val items = mutableListOf<Recognition>()
            val tfImage = TensorImage.fromBitmap(toBitmap(imageProxy))

            val outputs = objectModel.process(tfImage)
                .probabilityAsCategoryList.apply {
                    sortByDescending { it.score } // Sort with highest confidence first
                }.take(3) // take the top results

            for (output in outputs) {
                items.add(Recognition(output.label, output.score))
            }
            listener(items.toList())
            imageProxy.close()
        }

        private val yuvToRgbConverter = YuvToRgbConverter(ctx)
        private lateinit var bitmapBuffer: Bitmap
        private lateinit var rotationMatrix: Matrix

        @SuppressLint("UnsafeExperimentalUsageError")
        private fun toBitmap(imageProxy: ImageProxy): Bitmap? {

            val image = imageProxy.image ?: return null

            // Initialise Buffer
            if (!::bitmapBuffer.isInitialized) {
                // The image rotation and RGB image buffer are initialized only once
                Log.d(TAG, "Initalise toBitmap()")
                rotationMatrix = Matrix()
                rotationMatrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                bitmapBuffer = Bitmap.createBitmap(
                    imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
                )
            }

            // Pass image to an image analyser
            yuvToRgbConverter.yuvToRgb(image, bitmapBuffer)

            // Create the Bitmap in the correct orientation
            return Bitmap.createBitmap(
                bitmapBuffer,
                0,
                0,
                bitmapBuffer.width,
                bitmapBuffer.height,
                rotationMatrix,
                false
            )
        }
    }
}