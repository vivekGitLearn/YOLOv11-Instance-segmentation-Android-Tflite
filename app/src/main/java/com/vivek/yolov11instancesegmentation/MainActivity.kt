
package com.vivek.yolov11instancesegmentation

import android.Manifest
import android.R
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.os.SystemClock
import com.vivek.yolov11instancesegmentation.databinding.ActivityMainBinding
import java.time.Duration
import java.time.LocalTime


class MainActivity : AppCompatActivity(), InstanceSegmentation.InstanceSegmentationListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var instanceSegmentation: InstanceSegmentation
    private lateinit var drawImages: DrawImages
    private var selectedModel ="best_float16.tflite"
    private val VIDEO_PICK_CODE = 2001
    public var video_mode = 0
    public var total_processed_frame =0
//    private lateinit var pickVideoLauncher: ActivityResultLauncher<String>
    private var total_rbc = 0
    private var total_wbc = 0
    private var total_platelet = 0
    private  var Start_time = LocalTime.now()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()


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

//        pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
//            uri?.let {
//                binding.ivTopVideo.setVideoURI(it)
//                binding.ivTopVideo.setOnPreparedListener { mediaPlayer ->
//                    mediaPlayer.isLooping = false
//
//                    binding.ivTopVideo.start()
//                }
//
//                // 🧠 Process the video frames
//                processVideo(it)
//            }
//        }


////        Setup button to select image from API
//        binding.ApiButton.setOnClickListener {
//            pickImageURLLauncher.launch("image/*")
//        }

        // Set up button to select image from gallery
        binding.buttonSelectImage.setOnClickListener {
            binding.ivTop.visibility = View.VISIBLE
            binding.ivTopVideo.visibility = View.GONE
            video_mode = 0

            pickImageLauncher.launch("image/*")

        }
        // Set up button to select video from gallery
        binding.buttonSelectVideo.setOnClickListener {
            binding.ivTop.visibility = View.VISIBLE
//            binding.ivTopVideo.visibility = View.VISIBLE
//            binding.previewView.visibility = View.VISIBLE


            video_mode = 1
//            total_processed_frame =0
            pickVideoLauncher.launch("video/*")
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
//    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
//        uri?.let {
//            try {
//                val inputStream = contentResolver.openInputStream(it)
//                selectedBaseBitmap = BitmapFactory.decodeStream(inputStream)
//                inputStream?.close()
//                selectedBaseBitmap?.let { bitmap -> processImage(bitmap) }
//            } catch (e: Exception) {
//                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri?.let {
        binding.ivTopVideo.setVideoURI(it)
        binding.ivTopVideo.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = false
            binding.ivTopVideo.start()
        }

        // 🧠 Process frames from the selected video
        processVideo(it)

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
        total_rbc = 0
        total_wbc = 0
        total_platelet = 0
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
        postProcessTime: Long,
        classCounts: Map<String, Int>

    ) {

        val rbcCount = classCounts["rbc"] ?: 0
        val wbcCount = classCounts["wbc"] ?: 0
        val plateletCount = classCounts["platelet"] ?: 0

        total_rbc = total_rbc + rbcCount
        total_wbc = total_wbc + wbcCount
        total_platelet = total_platelet + plateletCount

        val overlayBitmap = drawImages.invoke(results)  // Output image from segmentation
        val finalImage = overlayImages(selectedBaseBitmap!!, overlayBitmap)  // Merge base and overlay images
        // Get screen width
        val screenWidth = resources.displayMetrics.widthPixels

        // Resize finalImage to screen width (both width and height)
        val resizedFinalImage = Bitmap.createScaledBitmap(finalImage, screenWidth, screenWidth, true)
        
//        get label
        val label = instanceSegmentation.getLabels()
        Log.d("MainActivity", "onDetect:clss name $classCounts ")
        val message_cell_count = "Rcb: $total_rbc Wbc: $total_wbc Platelet: $total_platelet"

        runOnUiThread {
            binding.tvPreprocess.text = preProcessTime.toString()
            binding.tvInference.text = interfaceTime.toString()
            binding.tvPostprocess.text = postProcessTime.toString()
            binding.resultModel.text = message_cell_count

//            binding.ivTop.setImageBitmap(finalImage)
            if (video_mode != 1)
                binding.ivTop.setImageBitmap(resizedFinalImage)
            else
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
    private fun processVideo(uri: Uri) {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(applicationContext, uri)
        Start_time = LocalTime.now()

        val duration =
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L
        val frameRate =30
        val estimatedFrameCount = (duration/1000) * frameRate
        binding.totalFrameCount.text = estimatedFrameCount.toString()

        val frameIntervalMs = 210L // 1000L = 1 second interval
        total_processed_frame = 0

        total_rbc = 0
        total_wbc = 0
        total_platelet = 0

        Thread {
            var timeMs = 0L
            while (timeMs < duration) {
                val frameBitmap =
                    retriever.getFrameAtTime(timeMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                frameBitmap?.let {
                    val scaledBitmap = Bitmap.createScaledBitmap(it, 256, 256, true)
                    selectedBaseBitmap = scaledBitmap // for overlay
                    runOnUiThread {
                        instanceSegmentation.invoke(scaledBitmap)
                    }
                        total_processed_frame+=1
                    runOnUiThread {
//                        binding.totalFrameRead.text = total_processed_frame.toString()
                        binding.totalFrameProcessed.text = timeMs.toString()
                        binding.videoStopTime.text = Duration.between(Start_time, LocalTime.now()).toMinutes().toString()
//                        binding.videoStartTime.text = Duration.between(Start_time, LocalTime.now()).seconds.toString()
                    }
                    Thread.sleep(frameIntervalMs) // wait before processing next frame
//                    binding.totalFrameRead.text = timeMs.toString()
                }
                timeMs = timeMs+1
            }
            retriever.release()
        }.start()

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

