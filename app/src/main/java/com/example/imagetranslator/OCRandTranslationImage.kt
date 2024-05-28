package com.example.imagetranslator

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.imagetranslator.Models.ModelLanguage
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

class OCRandTranslationImage : AppCompatActivity() {

    //UI View
    private lateinit var chooseImageBtn: Button
    private lateinit var recognizeTextBtn: Button
    private lateinit var translateTextBtn: Button
    private lateinit var imageIv: ShapeableImageView
    private lateinit var recognizedTextEt: EditText
    private lateinit var translatedTextEt: EditText
    private lateinit var sourceLanguageChooseBtn: MaterialButton
    private lateinit var targetLanguageChooseBtn: MaterialButton

    private companion object {
        //Uri of the image that we will take from Camera/Gallery private Uri imageUri = null;
        //to handle the result of Camera/Gallery intent
        private val CAMERA_REQUEST_CODE = 100
        private val STORAGE_REQUEST_CODE = 101

        //for printing logs
        private const val TAG = "MAIN_TAG"
    }

    //Uri of the image that we will take from Camera/Gallery
    private var imageUri: Uri? = null

    //arrays of permission required to pick image from Camera, Gallery
    private lateinit var cameraPermissions: Array<String>
    private lateinit var storagePermissions: Array<String>

    //progress dialog
    private lateinit var progressDialog: ProgressDialog

    //TextRecognizer
    private lateinit var textRecognizer: TextRecognizer

    //will contain list with language code and title
    private var languageArrayList: ArrayList<ModelLanguage>? = null

    //default/selected language code and language title
    private var sourceLanguageCode = "en"
    private var sourceLanguageTitle = "English"
    private var targetLanguageCode = "uk"
    private var targetLanguageTitle = "Ukrainian"
    //default output
    private var sourceLanguageText = ""

    //Translator options to set source and destination languages e.g. English -> Ukrainian
    private lateinit var translatorOptions: TranslatorOptions

    //Translator object, for configuration it with the source and target languages:
    private lateinit var translator: Translator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocrand_translation_image)

        //Init UI View
        chooseImageBtn = findViewById(R.id.chooseImageBtn)
        recognizeTextBtn = findViewById(R.id.recognizeTextBtn)
        translateTextBtn = findViewById(R.id.translateTextBtn)
        imageIv = findViewById(R.id.imageIv)
        recognizedTextEt = findViewById(R.id.recognizedTextEt)
        translatedTextEt = findViewById(R.id.translatedTextEt)
        sourceLanguageChooseBtn = findViewById(R.id.sourceLanguageChooseBtn)
        targetLanguageChooseBtn = findViewById(R.id.targetLanguageChooseBtn)

        //init arrays of permission required to pick image from Camera, Gallery
        cameraPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        storagePermissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        //init setup the progress dialog, show while text from image is being recognized
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please wait")
        progressDialog.setCanceledOnTouchOutside (false)

        //init TextRecognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        loadAvailableLanguages()

        //handle Choose Image Btn click, show input image dialog
        chooseImageBtn.setOnClickListener {
            showInputImageDialog()
        }

        //handle Text Recognition Btn click
        recognizeTextBtn.setOnClickListener {
            //check if image is picked or not, picked if imageUri is not null
            if (imageUri == null) {
                //imageUri is null, which means we haven't picked image yet, can't recognize text
                showToast("Pick Image First...")
            } else {
                //imageUri is not null, which means we have picked image, we can recognize text
                recognizeTextFromImage()
            }
        }

        //handle source Language ChooseBtn click, choose source language (from list) which you want to translate
        sourceLanguageChooseBtn.setOnClickListener {
            sourceLanguageChoose()
        }

        //handle targetLanguageChooseBtn click, choose target language (from list) to which you want to translate
        targetLanguageChooseBtn.setOnClickListener {
            targetLanguageChoose()
        }

        //handle translateBtn click, translate text to desired language
        translateTextBtn.setOnClickListener {
            validateData()
        }
    }

    private fun recognizeTextFromImage() {
        //set message and show progress dialog
        progressDialog.setMessage("Preparing Image...")
        progressDialog.show()

        try {
            //Prepare InputImage from image uri
            val inputImage = InputImage.fromFilePath(this, imageUri!!)
            //image prepared, we are about to start text recognition process, change progress message
            progressDialog.setMessage("Recognizing text...")
            //start text recognition process from image
            val textTaskResult = textRecognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    //process completed, dismiss dialog
                    progressDialog.dismiss()
                    //get the recognized text
                    val recognizedText = text.text
                    //set the recognized text to edit text
                    recognizedTextEt.setText(recognizedText)
                }
                .addOnFailureListener { e ->
                    //failed recognizing text from image, dismiss dialog, show reason in Toast
                    progressDialog.dismiss()
                    showToast("Failed to recognize text due to ${e.message}")
                }
        } catch(e: Exception) {
            //Exception occurred while preparing InputImage, dismiss dialog, show reason in Toast
            progressDialog.dismiss()
            showToast("Failed to prepare image due to ${e.message}")
        }
    }

    private fun showInputImageDialog() {
        //init PopupMenu param 1 is context, param 2 is UI View where you want to show PopupMenu
        val popupMenu = PopupMenu(this, chooseImageBtn)
        //Add items Camera, Gallery to PopupMenu, param 2 is menu id, param 3 is position of this menu item in menu items list, param 4 is title of the menu
        popupMenu.menu.add(Menu.NONE, 1, 1, "CAMERA")
        popupMenu.menu.add(Menu.NONE, 2, 2, "GALLERY")
        //Show PopupMenu
        popupMenu.show()

        //handle PopupMenu item clicks
        popupMenu.setOnMenuItemClickListener { menuItem ->
            //get item id that is clicked from PopupMenu
            val id = menuItem.itemId
            if (id == 1){
                //Camera is clicked, check if camera permissions are granted or not
                if (checkCameraPermissions()){
                    //camera permissions granted, we can launch camera intent
                    pickImageCamera()
                }
                else{
                    //camera permissions not granted, request the camera permissions
                    requestCameraPermissions()
                }
            } else if(id == 2) {
                //Gallery is clicked, check if storage permission is granted or not
                if (checkStoragePermission()) {
                    //storage permission granted, we can launch the gallery intent
                    pickImageGallery()
                }
                else{
                    //storage permission not granted, request the storage permission
                    requestStoragePermission()
                }
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun pickImageGallery() {
        //intent to pick image from gallery, will show all resources from where we can pick image
        val intent = Intent(Intent.ACTION_PICK)
        //set type of file we want to pick i.e. image
        intent.type = "image/*"
        galleryActivityResultLauncher.launch(intent)
    }

    private val galleryActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result->
            //here we will receive the image, if picked
            if (result.resultCode == Activity.RESULT_OK) {
                //image picked
                val data = result.data
                imageUri = data!!.data
                //set to imageView i.e. imageIv
                imageIv.setImageURI(imageUri)
            }
            else{
                showToast("Cancelled...!")
            }
        }

    private fun pickImageCamera() {
        //get ready the image data to store in MediaStore
        val values = ContentValues()
        values.put(MediaStore.Images.Media. TITLE, "Sample Title")
        values.put(MediaStore.Images.Media.DESCRIPTION, "Sample Description")

        //image Uri
        imageUri = contentResolver.insert(MediaStore. Images.Media. EXTERNAL_CONTENT_URI, values)

        //intent to launch Camera
        val intent = Intent (MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra (MediaStore.EXTRA_OUTPUT, imageUri)
        cameraActivityResultLauncher.launch(intent)
    }

    private val cameraActivityResultLauncher =
        registerForActivityResult (ActivityResultContracts.StartActivityForResult()) {result ->
            //here we will receive the image, if taken from camera
            if (result.resultCode == Activity. RESULT_OK) {
                //image is taken from camera
                //we already have the image in imageUri using function pickImageCamera
                imageIv.setImageURI(imageUri)
            }
            else{
                //cancelled
                showToast("Cancelled...")
            }
        }

    private fun loadAvailableLanguages(){
        //init language array list before starting adding data into it
        languageArrayList = ArrayList()

        //get list of all language codes e.g. en, ur, ar
        val languageCodeList = TranslateLanguage.getAllLanguages()

        //to make list containing both the language code e.g. en and language title e.g. English
        for (languageCode in languageCodeList){
            //Get language title from language code e.g. en -> English
            val languageTitle = Locale(languageCode).displayLanguage //e.g. en -> English
            //print language code and title in logs
            Log.d(TAG, "loadAvailableLanguages: languageCode: $languageCode")
            Log.d(TAG, "loadAvailableLanguages: languageTitle: $languageTitle")

            //prepare language model
            val modelLanguage = ModelLanguage (languageCode, languageTitle)
            //add prepared language model in list
            languageArrayList!!.add(modelLanguage)
        }
    }

    private fun sourceLanguageChoose() {
        //init PopupMenu param 1 is context, param 2 is the ui view around which we want to show the popup menu, to choose source language from list
        val popupMenu = PopupMenu(this, sourceLanguageChooseBtn)
        
        //from languageArrayList we will display language titles
        for (i in languageArrayList!!.indices) {
            //keep adding titles in popup menu item: param 1 is groupId, param 2 is itemId, param 3 is order, param 4 is title
            popupMenu.menu.add(Menu.NONE, i, i, languageArrayList!![i].languageTitle)
        }

        //show popup menu
        popupMenu.show()

        //handle popup menu item click,
        popupMenu.setOnMenuItemClickListener { menuItem ->
            //get clicked item id which is position/index from the list
            val position = menuItem.itemId

            //get code and title of the language selected
            sourceLanguageCode = languageArrayList!![position].languageCode
            sourceLanguageTitle = languageArrayList!![position].languageTitle

            //set the selected language to sourceLanguageChooseBtn as text
            sourceLanguageChooseBtn.text = sourceLanguageTitle

            //show in logs
            Log.d(TAG, "sourceLanguageChoose: sourceLanguageCode: $sourceLanguageCode")
            Log.d(TAG, "sourceLanguageChoose: sourceLanguageTitle: $sourceLanguageTitle")

            false
        }
    }

    private fun targetLanguageChoose() {
        //init PopupMenu param 1 is context, param 2 is the ui view around which we want to show the popup menu, to choose source language from list
        val popupMenu = PopupMenu(this, targetLanguageChooseBtn)

        //from languageArrayList we will display language titles
        for (i in languageArrayList!!.indices) {
            //keep adding titles in popup menu item: param 1 is groupId, param 2 is itemId, param 3 is order, param 4 is title
            popupMenu.menu.add(Menu.NONE, i, i, languageArrayList!![i].languageTitle)
        }

        //show popup menu
        popupMenu.show()

        //handle popup menu item click,
        popupMenu.setOnMenuItemClickListener { menuItem ->
            //get clicked item id which is position/index from the list
            val position = menuItem.itemId

            //get code and title of the language selected
            targetLanguageCode = languageArrayList!![position].languageCode
            targetLanguageTitle = languageArrayList!![position].languageTitle

            //set the selected language to targetLanguageChooseBtn as text
            targetLanguageChooseBtn.text = targetLanguageTitle

            //show in logs
            Log.d(TAG, "targetLanguageChoose: targetLanguageCode: $targetLanguageCode")
            Log.d(TAG, "targetLanguageChoose: targetLanguageTitle: $targetLanguageTitle")

            false
        }
    }

    private fun validateData() {
        //input text to be translated
        sourceLanguageText = recognizedTextEt.text.toString().trim()
        //print in logs
        Log.d(TAG, "validateData: sourceLanguageText: $sourceLanguageText")
        //validate data if empty show error message, otherwise start translation
        if (sourceLanguageText.isEmpty()){
            showToast("Enter text to translate...")
        }
        else{
            startTranslation()
        }
    }

    private fun startTranslation() {
        //set progress message and show
        progressDialog.setMessage("Processing language model...")
        progressDialog.show()

        //init TranslatorOptions with source and target languages e.g. en and ur
        translatorOptions = TranslatorOptions.Builder()
            .setSourceLanguage (sourceLanguageCode)
            .setTargetLanguage (targetLanguageCode)
            .build()
        translator = Translation.getClient(translatorOptions)

        //init DownloadConditions with option to requireWifi (Optional)
        val downloadConditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        //start downloading translation model if required (will download 1st time)
        translator.downloadModelIfNeeded(downloadConditions)
            .addOnSuccessListener {
                //translation model ready to be translated, lets translate
                Log.d(TAG, "startTranslation: model ready, start translation...")

                //change progress message to Translating...
                progressDialog.setMessage("Translating...")

                //start translation process
                translator.translate(sourceLanguageText)
                    .addOnSuccessListener { translatedText ->
                        //successfully translated
                        Log.d(TAG, "startTranslation: translatedText: $translatedText")
                        //dismiss dialog since translation is done
                        progressDialog.dismiss()
                        //set the translated text to translatedTextEt
                        translatedTextEt.setText(translatedText)
                    }
                    .addOnFailureListener { e->
                        //failed to translate, dismiss dialog, show exception
                        progressDialog.dismiss()
                        Log.e(TAG, "startTranslation: ", e)
                        //show exception message in toast
                        showToast("Failed to translate due to ${e.message}")
                    }
            }
            .addOnFailureListener { e->
                //failed to to ready translation model, can't proceed to translation
                progressDialog.dismiss()
                Log.e(TAG, "startTranslation: ", e)
                //show exception message in toast
                showToast("Failed due to ${e.message}")
            }
    }

    private fun checkStoragePermission(): Boolean{
        /*check if storage permissions are allowed or not
        return true if allowed, false if not allowed*/
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            //For Android 9 (API 28) and below you need to ask for permission to access the site
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            //For Android 10 (API 29) and higher permissions are not required
            true
        }
    }

    private fun checkCameraPermissions(): Boolean {
        /*check if camera & storage permissions are allowed or not
        return true if allowed, false if not allowed*/
        val cameraResult = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val storageResult = checkStoragePermission()

        return cameraResult && storageResult
    }

    private fun requestStoragePermission(){
        //request storage permission (for gallery image pick)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            //Asked for access to the Convent for Android 9 and lower
            ActivityCompat.requestPermissions(this, storagePermissions, STORAGE_REQUEST_CODE)
        }
    }

    private fun requestCameraPermissions(){
        //request camera permission (for camera intent)
        ActivityCompat.requestPermissions(this, cameraPermissions, CAMERA_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //handle permission(s) results
        when(requestCode) {
            CAMERA_REQUEST_CODE ->{
                //Check if some action from permission dialog performed or not Allow/Deny
                if(grantResults.isNotEmpty()) {
                    //Check if both permissions are granted or not
                    if(checkCameraPermissions()) {
                        //both permissions (Camera & Gallery) are granted, we can launch camera intent
                        pickImageCamera()
                    }
                    else {
                        //one or both permissions are denied, can't launch camera intent
                        showToast("Camera & Storage permission are required...")
                    }
                }
            }
            STORAGE_REQUEST_CODE ->{
                //Check if some action from permission dialog performed or not Allow/Deny
                if(grantResults.isNotEmpty()) {
                    //Check if storage permission is granted or not
                    if(checkStoragePermission()) {
                        //storage permission granted, we can launch gallery intent
                        pickImageGallery()
                    } else {
                        //storage permission denied, can't launch gallery intent
                        showToast("Storage permission is required...")
                    }
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}