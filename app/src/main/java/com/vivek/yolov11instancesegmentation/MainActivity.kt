
package com.vivek.yolov11instancesegmentation

import android.Manifest
import android.R
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.vivek.yolov11instancesegmentation.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalTime
import kotlin.math.log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import android.content.res.AssetManager
import  android.content.Context
import kotlinx.coroutines.CoroutineScope
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import calculateSHA256

class MainActivity : AppCompatActivity(), WebSocketMessageListener,InstanceSegmentation.InstanceSegmentationListener {
	private lateinit var binding: ActivityMainBinding
	private lateinit var instanceSegmentation: InstanceSegmentation
	private lateinit var drawImages: DrawImages
	private var selectedModel ="best_float16.tflite"
	private val VIDEO_PICK_CODE = 2001
	private var video_mode = 0
	private var total_processed_frame =0
	private var total_rbc = 0
	private var total_wbc = 0
	private var total_platelet = 0
	private  var Start_time = LocalTime.now()
	private var showVideoWithOverlay = false
	private val client = OkHttpClient()
	var fileName = "Unknown"
	var all_model_list: List<String> = mutableListOf()
	var job_id_temp = ""
	var image_url_temp = ""
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		enableEdgeToEdge()


//        Set client id
		val clientId = getOrCreateClientId(this)
		Log.d("CLIENT_ID", "Generated or fetched client ID: $clientId")
		binding.websocketId.text =clientId.toString()
		val webSocketClient = CustomWebSocketClient(this, clientId)
		webSocketClient.connect()



//		// Initialize Model Selection Dropdown (Spinner)
//        setupModelSpinner()
		// Initialize DrawImages
		drawImages = DrawImages(applicationContext)

		// Request permissions
		checkPermission()



		binding.videoSwitch.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				binding.videoSwitch.text ="SREAMING ON"
				binding.videoSwitch.setTextColor(Color.GREEN)
				showVideoWithOverlay = true
//                Log.d("videoSwitch", "onCreate:$showVideoWithOverlay ")
				binding.ivTop.visibility = View.VISIBLE
				binding.ivTopVideo.visibility = View.GONE
			}
			else{
				binding.videoSwitch.text ="SREAMING OFF"
				binding.videoSwitch.setTextColor(Color.RED)
				showVideoWithOverlay = false
				binding.ivTop.visibility = View.GONE
				binding.ivTopVideo.visibility = View.GONE
//                Log.d("videoSwitch", "onCreate:$showVideoWithOverlay ")
			}
			}

//        Botton to select image from APIS
		binding.ApiButton.setOnClickListener {
			if (showVideoWithOverlay) {
				binding.ivTop.visibility = View.VISIBLE
				binding.ivTopVideo.visibility = View.GONE
			} else {
				binding.ivTop.visibility = View.GONE
				binding.ivTopVideo.visibility = View.GONE
			}

			video_mode = 0
			// Inside your onClick or wherever you're calling fetchImage
			lifecycleScope.launch(Dispatchers.IO) {
				fetchImage()
			}


		}


		// Set up button to select image from gallery
		binding.buttonSelectImage.setOnClickListener {
			if(showVideoWithOverlay) {
				binding.ivTop.visibility = View.VISIBLE
				binding.ivTopVideo.visibility = View.GONE
			}
			else{
				binding.ivTop.visibility = View.GONE
				binding.ivTopVideo.visibility = View.GONE
			}
			video_mode = 0

			pickImageLauncher.launch("image/*")

		}
		// Set up button to select video from gallery
		binding.buttonSelectVideo.setOnClickListener {
			if(showVideoWithOverlay) {
				binding.ivTop.visibility = View.VISIBLE
			}
			else{
				binding.ivTop.visibility = View.GONE
			}
//            binding.ivTopVideo.visibility = View.VISIBLE
//            binding.previewView.visibility = View.VISIBLE


			video_mode = 1
			total_processed_frame =0
			pickVideoLauncher.launch("video/*")
		}


	}

	// Setup model selection spinner
	private fun setupModelSpinner() {

//		val modelList = assets.list("")?.filter { it.endsWith(".tflite") || it.endsWith(".pt") } ?: listOf()
		// Refresh model list and try selecting the model
		val modelList= File(filesDir, "models")
		all_model_list = modelList.list()?.toList() ?: emptyList()

//        val modelList = listOf("best_float16.tflite", "best_float32.tflite")
		all_model_list = modelList.list()?.toList() ?: emptyList()
		val adapter = ArrayAdapter(this, R.layout.simple_spinner_dropdown_item, all_model_list)
		binding.spinnerModels.adapter = adapter

		binding.spinnerModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
				selectedModel = all_model_list[position]
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



	private fun fetchImage(url:URL){
		CoroutineScope(Dispatchers.IO).launch {
			try {

				val url: URL = url
				val connection = url.openConnection() as HttpURLConnection
				connection.doInput = true
				connection.connect()
				// 🔹 Content-Disposition (may include filename if server sets it)
				val contentDisposition = connection.getHeaderField("Content-Disposition")
				if (contentDisposition != null && contentDisposition.contains("filename=")) {
					val parts = contentDisposition.split("filename=")
//					Log.d("check_file_name", "fetchImage: $parts ")
					if (parts.size > 1) {
						fileName = parts[1].replace("\"", "").trim()
					}
//					Log.d("check_file_name", "fetchImage: $parts  $fileName")
				}

				val input: InputStream = connection.inputStream
				selectedBaseBitmap = BitmapFactory.decodeStream(input)
//            runOnUiThread{binding.ivTop.setImageBitmap(selectedBaseBitmap)}
//            selectedBaseBitmap = BitmapFactory.decodeStream(input)
				selectedBaseBitmap?.let { bitmap -> processImage(bitmap) }

			} catch (E: Exception) {
				E.printStackTrace()
				Log.d("Fetch image", "fetchImage:${E.message} ")
				runOnUiThread {
					Toast.makeText(applicationContext, "Failed to load image", Toast.LENGTH_SHORT)
						.show()
				}
			}
		}
	}



	private fun fetchImage(){
		try{

			val url: URL = URL("http://192.168.1.5:8000/get-task/")
			val connection = url.openConnection() as HttpURLConnection
			connection.doInput = true
			connection.connect()
			// 🔹 Content-Disposition (may include filename if server sets it)
			val contentDisposition = connection.getHeaderField("Content-Disposition")
			if (contentDisposition != null && contentDisposition.contains("filename=")) {
				val parts = contentDisposition.split("filename=")
				if (parts.size > 1) {
					fileName = parts[1].replace("\"", "").trim()
				}
			}

			val input: InputStream = connection.inputStream
			selectedBaseBitmap = BitmapFactory.decodeStream(input)
//            runOnUiThread{binding.ivTop.setImageBitmap(selectedBaseBitmap)}
//            selectedBaseBitmap = BitmapFactory.decodeStream(input)
			selectedBaseBitmap?.let { bitmap -> processImage(bitmap) }

		}
		catch (E:Exception){
			E.printStackTrace()
			Log.d("Fetch image", "fetchImage:${E.message} ")
			runOnUiThread {
				Toast.makeText(applicationContext, "Failed to load image", Toast.LENGTH_SHORT).show()
			}
		}
	}

	// Process selected image
	private fun processImage(bitmap: Bitmap) {
		total_rbc = 0
		total_wbc = 0
		total_platelet = 0
		val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true) // Resize if needed
		if(showVideoWithOverlay)
			instanceSegmentation.invoke1(scaledBitmap)
		else
			instanceSegmentation.invoke(scaledBitmap)
	}

	override fun onError(error: String) {
		runOnUiThread {
			Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
			binding.ivTop.setImageResource(0)
		}
	}

	override fun onDetectWithoutImage(
		interfaceTime: Long,
//        results: List<SegmentationResult>,
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

//        get label
		val label = instanceSegmentation.getLabels()
		Log.d("MainActivity", "onDetect:clss name $classCounts ")
		val message_cell_count = "Rcb: $total_rbc Wbc: $total_wbc Platelet: $total_platelet"

		runOnUiThread {
			binding.tvPreprocess.text = preProcessTime.toString()
			binding.tvInference.text = interfaceTime.toString()
			binding.tvPostprocess.text = postProcessTime.toString()
			binding.resultModel.text = message_cell_count
		}
		postJsonToServer(image_url_temp, total_rbc, total_wbc, total_platelet)

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
		if(showVideoWithOverlay) {
			val overlayBitmap = drawImages.invoke(results)  // Output image from segmentation
			val finalImage =
				overlayImages(selectedBaseBitmap!!, overlayBitmap)  // Merge base and overlay images
			// Get screen width
			val screenWidth = resources.displayMetrics.widthPixels

			// Resize finalImage to screen width (both width and height)
			val resizedFinalImage =
				Bitmap.createScaledBitmap(finalImage, screenWidth, screenWidth, true)
            val tempImageFile = bitmapToTempFile(applicationContext, finalImage,image_url_temp)
            uploadImage(tempImageFile)

            runOnUiThread {
					binding.ivTop.setImageBitmap(resizedFinalImage)

			}


        }
		//  get label
		val label = instanceSegmentation.getLabels()
		Log.d("MainActivity", "onDetect:clss name $classCounts ")
		val message_cell_count = "Rcb: $total_rbc Wbc: $total_wbc Platelet: $total_platelet"

		runOnUiThread {
			binding.tvPreprocess.text = preProcessTime.toString()
			binding.tvInference.text = interfaceTime.toString()
			binding.tvPostprocess.text = postProcessTime.toString()
			binding.resultModel.text = message_cell_count
		}
		postJsonToServer(image_url_temp, total_rbc, total_wbc, total_platelet)

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
		val estimatedFrameCount = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
//        binding.totalFrameCount.text = estimatedFrameCount.toString()

		var frameIntervalMs = 70L // 1000L = 1 second interval
		total_processed_frame = 0

		total_rbc = 0
		total_wbc = 0
		total_platelet = 0

		Thread {
			var timeMs = 0L
			var total_frame = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()

			runOnUiThread{
				binding.totalFrameCount.text = total_frame.toString()
			}

			Log.d("duration", "processVideo: $duration ")
//            while (timeMs < duration) {
			while (total_processed_frame < total_frame!!) {
//                val frameBitmap =
//                    retriever.getFrameAtTime(timeMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
				val frameBitmapIndex  =retriever.getFrameAtIndex(total_processed_frame, MediaMetadataRetriever.BitmapParams())
//                frameBitmap?.let {
				frameBitmapIndex?.let {
					val scaledBitmap = Bitmap.createScaledBitmap(it, 256, 256, true)
					selectedBaseBitmap = scaledBitmap // for overlay
					if(showVideoWithOverlay) {
						frameIntervalMs = 230L
						runOnUiThread {
							instanceSegmentation.invoke1(scaledBitmap)
						}
					}
					else{
						frameIntervalMs = 70L
						runOnUiThread {
							instanceSegmentation.invoke(scaledBitmap)
						}
					}
						total_processed_frame+=1
					runOnUiThread {
//                        binding.totalFrameRead.text = total_processed_frame.toString()
						binding.totalFrameProcessed.text = total_processed_frame.toString()
						binding.videoStopTime.text = Duration.between(Start_time, LocalTime.now()).toMinutes().toString()
//                        binding.videoStartTime.text = Duration.between(Start_time, LocalTime.now()).seconds.toString()
					}
					Thread.sleep(frameIntervalMs) // wait before processing next frame
//                    binding.totalFrameRead.text = timeMs.toString()
				}
//                frame_index=frame_index+1
				timeMs = timeMs+33
			}
			retriever.release()
		}.start()

	}

	fun bitmapToTempFile(context: Context, bitmap: Bitmap, imageUrl: String): File {
		val imagesDir = File(context.cacheDir, "images")
		if (!imagesDir.exists()) {
			imagesDir.mkdirs()
		}

		// Extract the original filename from the URL
		val originalFilename = imageUrl.substringAfterLast("/")

		// Create file with exact name (no random suffix)
		val file = File(imagesDir, originalFilename)

		try {
			FileOutputStream(file).use { out ->
				bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
			}
		} catch (e: IOException) {
			e.printStackTrace()
		}

		return file
	}






	private fun uploadImage(imageFile: File) {
        val url = "http://192.168.1.5:8000/upload-image"

        val requestBody = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", imageFile.name, requestBody)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(multipartBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(applicationContext, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                Log.d("UPLOAD_IMAGE", "Response: $body")
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(applicationContext, "Upload success!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(applicationContext, "Server error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }



    private fun postJsonToServer(Filename: String,Rbc: Int, Wbc: Int, Platelet: Int) {
		val url = "http://192.168.1.5:8000/append-json"

		// Build your JSON object
		val json = JSONObject().apply {
			put("filename", Filename)
			put("rbc", Rbc)
			put("wbc", Wbc)
			put("platelet", Platelet)
			put("model_name", selectedModel)
			put("job_id",job_id_temp)
		}

		val mediaType = "application/json; charset=utf-8".toMediaType()
		val requestBody = json.toString().toRequestBody(mediaType)

		val request = Request.Builder()
			.url(url)
			.post(requestBody)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				e.printStackTrace()
				runOnUiThread {
					Toast.makeText(applicationContext, "Failed to send JSON", Toast.LENGTH_SHORT).show()
				}
			}

			override fun onResponse(call: Call, response: Response) {
				response.use {
					if (!response.isSuccessful) {
						runOnUiThread {
							Toast.makeText(applicationContext, "Server error: ${response.code}", Toast.LENGTH_SHORT).show()
						}
					} else {
						val responseBody = response.body?.string()
						Log.d("POST_JSON", "Response: $responseBody")
						runOnUiThread {
							Toast.makeText(applicationContext, "JSON sent successfully!", Toast.LENGTH_SHORT).show()
						}
					}
				}
			}
		})
	}


	private val requestPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()) { map ->
		if (map.all { it.value }) {
			Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
		} else {
			Toast.makeText(this, "Permission required", Toast.LENGTH_LONG).show()
		}
	}


	fun getOrCreateClientId(context: Context): String {
		val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
		var clientId = sharedPref.getString("client_id", null)

		if (clientId == null) {
			clientId = UUID.randomUUID().toString() // Generate unique ID
			sharedPref.edit().putString("client_id", clientId).apply()
		}

		return clientId
	}

	// 👇 Callback method from WebSocket
	override fun onTextMessage(message: String) {
		Log.d("main activity", "onTextMessage:$message ")
		runOnUiThread {
			binding.wsMessage.text = message

			val index = all_model_list.indexOfFirst { it.equals(message, ignoreCase = true) }
			if (index != -1) {
				binding.spinnerModels.setSelection(index)
				selectedModel = all_model_list[index]
				initializeSegmentationModel()

				Toast.makeText(this, "✅ Model '$message' selected", Toast.LENGTH_SHORT).show()
			} else {
				Toast.makeText(this, "❌ Model '$message' not found in list", Toast.LENGTH_SHORT).show()
			}
		}
	}

	override fun onAssignJob(
		jobId: String,
		modelName: String,
		modelUrl: String,
		modelHash: String,
		imageUrl: String,
		returnType: String,
		returnUrl: String
	) {
		Log.d("MainActivity", "🛠 Assigned Job: $jobId")
		Log.d("MainActivity", "modelName: $modelName")
		Log.d("MainActivity", "modelUrl: $modelUrl")
		Log.d("MainActivity", "modelHash: $modelHash")
		Log.d("MainActivity", "imageUrl: $imageUrl")
		job_id_temp = jobId
		image_url_temp= imageUrl
		val cleanModelName = modelName.trim().replace(" ", "")

		if (all_model_list.contains(cleanModelName)) {
			Log.d("model present", "✅ Model present: $cleanModelName")
			if (selectedModel != cleanModelName){
				selectedModel = cleanModelName
//				val adapter = binding.spinnerModels.adapter
//				if (adapter != null) {
//					val position = (0 until adapter.count).firstOrNull {
//						adapter.getItem(it).toString() == selectedModel
//					}
//
//					if (position != null) {
//						binding.spinnerModels.setSelection(position)
//					} else {
//						Log.e("Spinner", "selectedModel not found in spinner items")
//					}
//				}
				val modelfileLoaded = File(filesDir, "models/$cleanModelName")
				if(calculateSHA256(modelfileLoaded) != modelHash){
					Log.e("ModelIntegrity", "❌ Hash mismatch. Model may be corrupted or tampered.")
				}
				else
				{
					Log.d("ModelIntegrity", "✅ Model hash matches")
				}
				initializeSegmentationModel()
				binding.wsMessage.text = cleanModelName
				fetchImage(URL(imageUrl))
			}
			else{
				fetchImage(URL(imageUrl))
			}
//            Toast.makeText(this, "✅ Model '$modelName' selected", Toast.LENGTH_SHORT).show()
//            selectModelAndFetchImage(modelName, imageUrl)
		} else {
			Log.d("model present", "⬇️ Downloading model: $cleanModelName")

			downloadModelToLocal(this, modelUrl, cleanModelName) { modelFile ->
				runOnUiThread {
					if (modelFile != null) {
						Toast.makeText(
							this,
							"✅ Model downloaded to ${modelFile.absolutePath}",
							Toast.LENGTH_SHORT
						).show()
						Log.d("MainActivity", "📁 Model file path: ${modelFile.absolutePath}")
						selectedModel = cleanModelName

						//checking hash of model
						if(calculateSHA256(modelFile) != modelHash){
							Log.e("ModelIntegrity", "❌ Hash mismatch. Model may be corrupted or tampered.")
						}
						else
						{
							Log.d("ModelIntegrity", "✅ Model hash matches")
							binding.wsMessage.text = cleanModelName
						}


						initializeSegmentationModel()
						fetchImage(URL(imageUrl))

					} else {
						Toast.makeText(
							this,
							"❌ Failed to download model: $cleanModelName",
							Toast.LENGTH_SHORT
						).show()
					}

					// Refresh model list and try selecting the model
					val modelListDir = File(filesDir, "models")
					all_model_list = modelListDir.list()?.toList() ?: emptyList()
					Log.d("All model list", "📦 Models available: $all_model_list")

//                    selectModelAndFetchImage(modelName, imageUrl)
				}
			}
		}
	}


//    download model
fun downloadModelToLocal(context: Context, modelUrl: String, modelFileName: String, onComplete: (File?) -> Unit) {
	val client = OkHttpClient()
	val request = Request.Builder().url(modelUrl).build()

	client.newCall(request).enqueue(object : Callback {
		override fun onFailure(call: Call, e: IOException) {
			Log.e("ModelDownload", "❌ Failed to download model", e)
			onComplete(null)
		}

		override fun onResponse(call: Call, response: Response) {
			if (!response.isSuccessful) {
				Log.e("ModelDownload", "❌ HTTP Error: ${response.code}")
				onComplete(null)
				return
			}

			val modelDir = File(context.filesDir, "models")
			if (!modelDir.exists()) modelDir.mkdirs()

			val outFile = File(modelDir, modelFileName)
			val inputStream = response.body?.byteStream()

			try {
				inputStream?.use { input ->
					FileOutputStream(outFile).use { output ->
						input.copyTo(output)
					}
				}
				Log.d("ModelDownload", "✅ Model saved at: ${outFile.absolutePath}")
				onComplete(outFile)
			} catch (e: Exception) {
				Log.e("ModelDownload", "❌ Error saving model", e)
				onComplete(null)
			}
//			if (modelDir.exists() && modelDir.isDirectory) {
//				val files = modelDir.listFiles()
//				files?.forEach {
//					Log.d("ModelDirectory", "📁 File: ${it.name}")
//				}
//			} else {
//				Log.e("ModelDirectory", "❌ Model directory not found")
//			}
		}
	})
}


}

