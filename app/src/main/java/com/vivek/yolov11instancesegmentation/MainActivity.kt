
package com.vivek.yolov11instancesegmentation

import android.Manifest
import android.R
import android.R.color
import android.R.color.holo_green_light
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import calculateSHA256
import com.vivek.yolov11instancesegmentation.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalTime
import java.util.UUID

class MainActivity : AppCompatActivity(),InstanceSegmentation.InstanceSegmentationListener {
	private lateinit var binding: ActivityMainBinding
	private var instanceSegmentation: InstanceSegmentation? =null
	private lateinit var drawImages: DrawImages
	private var selectedModel ="best_float16.tflite"
	private val VIDEO_PICK_CODE = 2001
	private var video_mode = 0
	private var total_processed_frame =0
	private  var Start_time = LocalTime.now()
	private var showVideoWithOverlay = true
	private val client = OkHttpClient()
	var fileName = "Unknown"
	var all_model_list: List<String> = mutableListOf()
	var job_id_temp = ""
	var image_url_temp = ""
	var temp_task_id = ""
	var result_of_inference = ""
	var task_status = "not_started"
	var poling_number = 0
	var model_hash_online = ""
	// Declare sharedPref at the class level
	private lateinit var sharedPref: SharedPreferences

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		sharedPref = getSharedPreferences("MyPrefs", MODE_PRIVATE)
		val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)
		val user_email = sharedPref.getString("email", null)
		Log.d("check_email", "onCreate: $user_email")
		if (!isLoggedIn) {
			startActivity(Intent(this, LoginAndSignUp::class.java))
			finish()
			return
		} else {

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		enableEdgeToEdge()


			val sharedPref = getSharedPreferences("MyPrefs", MODE_PRIVATE)
			val clientId = sharedPref.getString("deviceUUID", getOrCreateClientId(this))!!
			binding.userEmail.text = sharedPref.getString("email", null)
			binding.userClientId.text = clientId


			getUserName(sharedPref.getString("email", null)!!)
			getPoint(clientId)
			setClientStatus(1)
			Log.d("CLIENT_ID", "Generated or fetched client ID: $clientId")



		// Initialize DrawImages
		drawImages = DrawImages(applicationContext)

		// Request permissions
		checkPermission()


////        Botton to select image from APIS
//		binding.ApiButton.setOnClickListener {
//
//
//			video_mode = 0
//			// Inside your onClick or wherever you're calling fetchImage
//			lifecycleScope.launch(Dispatchers.IO) {
//				getNextTask()
//			}
//		}
			startPollingTasksLoop()
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



//	private fun fetchImage(){
//		try{
//
//			val url: URL = URL("${Constants.BASE_URL}/get-task/")
//			val connection = url.openConnection() as HttpURLConnection
//			connection.doInput = true
//			connection.connect()
//			// 🔹 Content-Disposition (may include filename if server sets it)
//			val contentDisposition = connection.getHeaderField("Content-Disposition")
//			if (contentDisposition != null && contentDisposition.contains("filename=")) {
//				val parts = contentDisposition.split("filename=")
//				if (parts.size > 1) {
//					fileName = parts[1].replace("\"", "").trim()
//				}
//			}
//
//			val input: InputStream = connection.inputStream
//			selectedBaseBitmap = BitmapFactory.decodeStream(input)
////            runOnUiThread{binding.ivTop.setImageBitmap(selectedBaseBitmap)}
////            selectedBaseBitmap = BitmapFactory.decodeStream(input)
//			selectedBaseBitmap?.let { bitmap -> processImage(bitmap) }
//
//		}
//		catch (E:Exception){
//			E.printStackTrace()
//			Log.d("Fetch image", "fetchImage:${E.message} ")
//			runOnUiThread {
//				Toast.makeText(applicationContext, "Failed to load image", Toast.LENGTH_SHORT).show()
//			}
//		}
//	}



	// Process selected image
	private fun processImage(bitmap: Bitmap) {
		val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true) // Resize if needed
		if(showVideoWithOverlay)
			instanceSegmentation?.invoke1(scaledBitmap)
		else
			instanceSegmentation?.invoke(scaledBitmap)
	}

	override fun onError(error: String) {
		runOnUiThread {
			Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
		}
	}

	override fun onDetectWithoutImage(
		interfaceTime: Long,
		preProcessTime: Long,
		postProcessTime: Long,
		classCounts: Map<String, Int>
	) {
		result_of_inference = classCounts.toString()
//        get label
		val label = instanceSegmentation?.getLabels()
		Log.d("MainActivity", "onDetect:clss name $classCounts ")

		postJsonToServer(image_url_temp,result_of_inference)

	}

	override fun onDetect(
		interfaceTime: Long,
		results: List<SegmentationResult>,
		preProcessTime: Long,
		postProcessTime: Long,
		classCounts: Map<String, Int>
	) {
		result_of_inference = classCounts.toString()

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
            uploadImage(tempImageFile,job_id_temp)
        }
		//  get label
		val label = instanceSegmentation?.getLabels()
		Log.d("MainActivity", "onDetect:clss name $classCounts ")


		postJsonToServer(image_url_temp,result_of_inference)

	}

	override fun onEmpty() {

	}

	override fun onDestroy() {
		super.onDestroy()
		Log.d("MainActivity", "onDestroy called ")
		instanceSegmentation?.close()
		setClientStatus(0)

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



	private fun uploadImage(imageFile: File, folderName: String) {
        val url = "${Constants.BASE_URL}/task/submit-result"
		Log.d("UPLOAD_IMAGE", "URL: $url")
		Log.d("randomId", "randomId: $temp_task_id")
		Log.d("UPLOAD_IMAGE", "Folder Name: $folderName")
//
//        val url = "${Constants.BASE_URL}/upload-image"
        val requestBody = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
		val multipartBody = MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("result_image", imageFile.name, requestBody)
			.addFormDataPart("folder_path", folderName) // 🔄 corrected from "foldername"
			.addFormDataPart("device_id", getOrCreateClientId(this))
			.addFormDataPart("random_id", temp_task_id)
			.addFormDataPart("result", result_of_inference)
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
					Log.d("UPLOAD_IMAGE", "Upload failed: ${e.message}")
                }
            }

			override fun onResponse(call: Call, response: Response) {
				val body = response.body?.string()
				Log.d("UPLOAD_IMAGE", "Response: $body")
				runOnUiThread {
					if (response.isSuccessful) {
						if (body != null && body.contains("Task result submitted successfully")) {
							task_status = "task result submitted successfully"
							Log.d("TASK", "Task submitted, ready for next one")
						}
					} else {
						Toast.makeText(applicationContext, "Server error: ${response.code}", Toast.LENGTH_SHORT).show()
					}
				}
			}

		})
    }

	private fun startPollingTasksLoop() {
		lifecycleScope.launch(Dispatchers.IO) {
			while (true) {
				if (task_status == "task result submitted successfully") {
					Log.d("TASK_LOOP", "Fetching next task...")
					getNextTask()
					task_status = "not_started" // Reset to avoid multiple fetches
					poling_number = 1
					runOnUiThread {
						binding.inferenceStatus.text = "inferencing...."
						binding.inferenceStatus.setTextColor(Color.parseColor("#009688"))
					}

//					binding.ApiButton.setBackgroundColor(Color.parseColor("#009688"))

				}
				else{
					poling_number = poling_number+1
					if(poling_number%10 == 0)
					{
						Log.d("TASK_LOOP", "Fetching next task...")
						getNextTask()
						task_status = "not_started" // Reset to avoid multiple fetches
//						poling_number = 1
					}
					runOnUiThread {
						binding.inferenceStatus.text = "Waiting for task"
						binding.inferenceStatus.setTextColor(Color.parseColor("#E53935"))
					}
//					binding.ApiButton.setBackgroundColor(Color.parseColor("#E53935"))
				}
				delay(500)  // Wait 3 seconds before next check
				Log.d("TASK_LOOP", "startPollingTasksLoop: checking For task $task_status")
			}
		}
	}


    private fun postJsonToServer(Filename: String,InfrenceResult: String) {
		val url = "${Constants.BASE_URL}/append-json?foldername=${job_id_temp}"

		// Build your JSON object
		val json = JSONObject().apply {
			put("filename", Filename)
			put("result", InfrenceResult)
			put("model_name", selectedModel)
			put("job_id",job_id_temp)
			put("clientId",getOrCreateClientId(this@MainActivity))
			put("task_id",temp_task_id)
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
//							Toast.makeText(applicationContext, "JSON sent successfully!", Toast.LENGTH_SHORT).show()
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


	fun getNextTask() {
		val client = OkHttpClient()

		val json = JSONObject()
		json.put("device_id",sharedPref.getString("deviceUUID", getOrCreateClientId(this))!! )

		val mediaType = "application/json; charset=utf-8".toMediaType()
		val body = RequestBody.create(mediaType, json.toString())

		val request = Request.Builder()
			.url("http://${Constants.BASE_IP}:8000/task/get-next-task/")
			.post(body)
			.addHeader("accept", "application/json")
			.addHeader("Content-Type", "application/json")
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("Failed: ${e.message}")
			}

			override fun onResponse(call: Call, response: Response) {
				val responseData = response.body?.string()
				println("Response: $responseData")
				Log.d("get next task", "onResponse: $responseData")

				if (response.isSuccessful && responseData != null) {
					try {
						val jsonObject = JSONObject(responseData)

						val patientId = jsonObject.getString("patient_id")
						val imageUrl = Constants.BASE_URL + jsonObject.getString("image_url")

						var modelUrl = jsonObject.getString("model_url")
						val status = jsonObject.getString("status")
						val taskId = jsonObject.getString("random_id")
						val deviceId = jsonObject.getString("device_id_which_did")
						val timestampCreated = jsonObject.getString("timestamp_created")
						val timestampSent = jsonObject.getString("timestamp_sent")
						val cleanModelName =modelUrl.trim().replace(" ","").replace("/classification_models/","")
						modelUrl = Constants.BASE_URL + modelUrl
						job_id_temp = patientId
						image_url_temp = imageUrl
						temp_task_id = taskId

						// Print individual values
						Log.d("NextTask", "Patient ID: $patientId")
						Log.d("NextTask", "Image URL: $imageUrl")
						Log.d("NextTask", "Model URL: $modelUrl")
						Log.d("NextTask", "Status: $status")
						Log.d("NextTask", "Task ID: $taskId")
						Log.d("NextTask", "Device ID: $deviceId")
						Log.d("NextTask", "Created At: $timestampCreated")
						Log.d("NextTask", "Sent At: $timestampSent")
						Log.d("NextTask", "Model Name: $cleanModelName")
						// You can now pass these to your UI or processing logic

						if (all_model_list.contains(cleanModelName)) {
							Log.d("model present", "✅ Model present: $cleanModelName")
							if (selectedModel != cleanModelName){
								selectedModel = cleanModelName

								val modelfileLoaded = File(filesDir, "models/$cleanModelName")
								if(calculateSHA256(modelfileLoaded) != model_hash_online){
									Log.e("ModelIntegrity", "❌ Hash mismatch. Model may be corrupted or tampered.")
								}
								else
								{
									Log.d("ModelIntegrity", "✅ Model hash matches")
								}
								initializeSegmentationModel()
								fetchImage(URL(imageUrl))
							}
							else{
								fetchImage(URL(imageUrl))
							}
							//    Toast.makeText(this, "✅ Model '$modelName' selected", Toast.LENGTH_SHORT).show()
							//    selectModelAndFetchImage(modelName, imageUrl)
						}
						else {
							Log.d("model present", "⬇️ Downloading model: $cleanModelName")

							downloadModelToLocal(this@MainActivity, modelUrl, cleanModelName) { modelFile ->
								runOnUiThread {
									if (modelFile != null) {
										Toast.makeText(
											this@MainActivity,
											"✅ Model downloaded to ${modelFile.absolutePath}",
											Toast.LENGTH_SHORT
										).show()
										Log.d("MainActivity", "📁 Model file path: ${modelFile.absolutePath}")
										selectedModel = cleanModelName

										//checking hash of model
										if(calculateSHA256(modelFile) != model_hash_online){
											Log.e("ModelIntegrity", "❌ Hash mismatch. Model may be corrupted or tampered.")
										}
										else
										{
											Log.d("ModelIntegrity", "✅ Model hash matches")

										}



										initializeSegmentationModel()
										fetchImage(URL(imageUrl))

									} else {
										Toast.makeText(
											this@MainActivity,
											"❌ Failed to download model: $cleanModelName",
											Toast.LENGTH_SHORT
										).show()
									}

									// Refresh model list and try selecting the model
									val modelListDir = File(filesDir, "models")
									all_model_list = modelListDir.list()?.toList() ?: emptyList()
									Log.d("All model list", "📦 Models available: $all_model_list")

									//    selectModelAndFetchImage(modelName, imageUrl)
								}
							}
						}



					} catch (e: Exception) {
						Log.e("get next task", "JSON parsing error: ${e.message}")
					}
				} else {
					Log.e("get next task", "Request failed or response was null")
				}
			}

		})
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
		}
	})
}
	private fun setClientStatus(status: Int) {
		val deviceUuid = getOrCreateClientId(this)

		val client = OkHttpClient()

		val urlBuilder = "${Constants.BASE_URL}/client/set_client_status".toHttpUrlOrNull()
			?.newBuilder()
			?.addQueryParameter("device_uuid", deviceUuid)
			?.addQueryParameter("status", status.toString())


		if (urlBuilder == null) {
			Log.e("StatusUpdate", "Invalid URL")
			return
		}

		val request = Request.Builder()
			.url(urlBuilder.build())
			.get()
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				Log.e("StatusUpdate", "Failed to update status: ${e.message}")
			}

			override fun onResponse(call: Call, response: Response) {
				if (response.isSuccessful) {
					Log.d("StatusUpdate", "Successfully updated client status!")
				} else {
					Log.e("StatusUpdate", "Failed to update client status! Code: ${response.code}")
				}
			}
		})
	}
	private fun getUserName(userEmail: String) {
		val client = OkHttpClient()

		val urlBuilder = "${Constants.BASE_URL}/users/user_name".toHttpUrlOrNull()
			?.newBuilder()
			?.addQueryParameter("user_gmail", userEmail)

		if (urlBuilder == null) {
			Log.e("StatusUpdate", "Invalid URL")
			return
		}

		val request = Request.Builder()
			.url(urlBuilder.build())
			.get()
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				Log.e("StatusUpdate", "Failed to fetch user name: ${e.message}")
			}

			override fun onResponse(call: Call, response: Response) {
				if (response.isSuccessful) {
					val responseData = response.body?.string()
					Log.d("StatusUpdate", "Response: $responseData")

					responseData?.let {
						try {
							val jsonObject = JSONObject(it)
							val userName = jsonObject.getString("user_name")

							Log.d("StatusUpdate", "Extracted user name: $userName")

							// Optionally store to a variable or UI element
							runOnUiThread {
								binding.userName.text = userName // Example
							}

						} catch (e: JSONException) {
							Log.e("StatusUpdate", "JSON parsing error: ${e.message}")
						}
					}
				} else {
					Log.e("StatusUpdate", "Failed to fetch user name. Code: ${response.code}")
				}
			}

		})
	}

private fun getPoint(device_uuid: String) {
		val client = OkHttpClient()

		val urlBuilder = "${Constants.BASE_URL}/score/client_score".toHttpUrlOrNull()
			?.newBuilder()
			?.addQueryParameter("device_uuid", device_uuid)

		if (urlBuilder == null) {
			Log.e("StatusUpdate", "Invalid URL")
			return
		}

		val request = Request.Builder()
			.url(urlBuilder.build())
			.get()
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				Log.e("StatusUpdate", "Failed to fetch user name: ${e.message}")
			}

			override fun onResponse(call: Call, response: Response) {
				if (response.isSuccessful) {
					val responseData = response.body?.string()
					Log.d("StatusUpdate", "Response: $responseData")

					responseData?.let {
						try {
							val jsonObject = JSONObject(it)
							val total_done_jobs = jsonObject.getString("total_done_jobs")

							Log.d("StatusUpdate", "Extracted total_done_jobs: $total_done_jobs")

							// Optionally store to a variable or UI element
							runOnUiThread {
								binding.earningValue.text = total_done_jobs // Example
							}

						} catch (e: JSONException) {
							Log.e("StatusUpdate", "JSON parsing error: ${e.message}")
						}
					}
				} else {
					Log.e("StatusUpdate", "Failed to fetch user name. Code: ${response.code}")
				}
			}

		})
	}
	private fun getModelHash(model_name: String) {
		val client = OkHttpClient()

		val urlBuilder = "${Constants.BASE_URL}/classification-models/classification-models".toHttpUrlOrNull()
			?.newBuilder()
			?.addQueryParameter("model_name", model_name)

		if (urlBuilder == null) {
			Log.e("StatusUpdate", "Invalid URL")
			return
		}

		val request = Request.Builder()
			.url(urlBuilder.build())
			.get()
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				Log.e("StatusUpdate", "Failed to fetch model info: ${e.message}")
			}

			override fun onResponse(call: Call, response: Response) {
				if (response.isSuccessful) {
					val responseData = response.body?.string()
					Log.d("StatusUpdate", "Response: $responseData")

					responseData?.let {
						try {
							val jsonObject = JSONObject(it)
							model_hash_online = jsonObject.getString("model_hash")

//							Log.d("StatusUpdate", "Extracted total_done_jobs: $total_done_jobs")


						} catch (e: JSONException) {
							Log.e("StatusUpdate", "JSON parsing error: ${e.message}")
						}
					}
				} else {
					Log.e("StatusUpdate", "Failed to fetch user name. Code: ${response.code}")
				}
			}

		})
	}



}

