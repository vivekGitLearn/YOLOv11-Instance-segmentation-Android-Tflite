
package com.vivek.yolov11instancesegmentation

import android.Manifest
import android.R
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.vivek.yolov11instancesegmentation.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), InstanceSegmentation.InstanceSegmentationListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var instanceSegmentation: InstanceSegmentation
    private lateinit var drawImages: DrawImages
    private var selectedModel ="best_float16.tflite"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Model Selection Dropdown (Spinner)
        setupModelSpinner()
        // Initialize DrawImages
        drawImages = DrawImages(applicationContext)

//        // Initialize YOLOv11 model for instance segmentation
//        instanceSegmentation = InstanceSegmentation(
//            context = applicationContext,
//            modelPath = selectedModel,
//            labelPath = null,
//            instanceSegmentationListener = this,
//            message = {
//                Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
//            },
//        )

        // Request permissions
        checkPermission()

        // Set up button to select image from gallery
        binding.buttonSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

    }

    // Setup model selection spinner
    private fun setupModelSpinner() {
        val modelList = listOf("best_float16.tflite", "best_float32.tflite")
        val adapter = ArrayAdapter(this, R.layout.simple_spinner_dropdown_item, modelList)
        binding.spinnerModels.adapter = adapter

        binding.spinnerModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedModel = modelList[position]
                initializeSegmentationModel()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
    private fun initializeSegmentationModel() {
        instanceSegmentation = InstanceSegmentation(
            context = applicationContext,
            modelPath = selectedModel,
            labelPath = null,
            instanceSegmentationListener = this,
            message = {
                Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
            }
        )

        // Show Toast with selected model name
        Toast.makeText(applicationContext, "$selectedModel Model Initialized Successfully", Toast.LENGTH_SHORT).show()
    }


    // Request permission for external storage (Needed for Android 10 and below)
    private fun checkPermission() {
        val isGranted = REQUIRED_PERMISSIONS.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!isGranted) {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }
    //    val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    // Image Picker: Opens the gallery
    private var selectedBaseBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                selectedBaseBitmap = BitmapFactory.decodeStream(inputStream)  // Store selected base image
                inputStream?.close()
                selectedBaseBitmap?.let { bitmap -> processImage(bitmap) }  // Pass image for processing
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickImageURLLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                selectedBaseBitmap = BitmapFactory.decodeStream(inputStream)  // Store selected base image
                inputStream?.close()
                selectedBaseBitmap?.let { bitmap -> processImage(bitmap) }  // Pass image for processing
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }



    // Process selected image
    private fun processImage(bitmap: Bitmap) {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true) // Resize if needed
        instanceSegmentation.invoke(scaledBitmap)
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
            binding.ivTop.setImageResource(0)
        }
    }

    override fun onDetect(
        interfaceTime: Long,
        results: List<SegmentationResult>,
        preProcessTime: Long,
        postProcessTime: Long
    ) {
        val overlayBitmap = drawImages.invoke(results)  // Output image from segmentation
        val finalImage = overlayImages(selectedBaseBitmap!!, overlayBitmap)  // Merge base and overlay images
        // Get screen width
        val screenWidth = resources.displayMetrics.widthPixels

        // Resize finalImage to screen width (both width and height)
        val resizedFinalImage = Bitmap.createScaledBitmap(finalImage, screenWidth, screenWidth, true)

        runOnUiThread {
            binding.tvPreprocess.text = preProcessTime.toString()
            binding.tvInference.text = interfaceTime.toString()
            binding.tvPostprocess.text = postProcessTime.toString()
//            binding.ivTop.setImageBitmap(finalImage)
            binding.ivTop.setImageBitmap(resizedFinalImage)
        }
    }

    override fun onEmpty() {
        runOnUiThread {
            binding.ivTop.setImageResource(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceSegmentation.close()
    }

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
    fun overlayImages(baseBitmap: Bitmap, overlayBitmap: Bitmap): Bitmap {
        // Ensure overlay image is scaled to fit the base image if needed
        val scaledOverlay = Bitmap.createScaledBitmap(overlayBitmap, baseBitmap.width, baseBitmap.height, true)

        // Create a mutable copy of the base image
        val resultBitmap = Bitmap.createBitmap(
            baseBitmap.width,
            baseBitmap.height,
            baseBitmap.config ?: Bitmap.Config.ARGB_8888  // Provide a default config
        )

        // Create a canvas to draw on the new bitmap
        val canvas = Canvas(resultBitmap)

        // Draw base image first
        canvas.drawBitmap(baseBitmap, 0f, 0f, null)

        // Set up paint for overlay (optional transparency)
        val paint = Paint()
        paint.alpha = 200  // Adjust transparency (0-255)

        // Draw overlay image on top
        canvas.drawBitmap(scaledOverlay, 0f, 0f, paint)

        return resultBitmap
    }



    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { map ->
        if (map.all { it.value }) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission required", Toast.LENGTH_LONG).show()
        }
    }
}
