package com.application.drprescription

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.application.drprescription.databinding.ActivityMainBinding
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.font.FontProvider
import com.itextpdf.layout.font.FontSet
import com.itextpdf.layout.property.HorizontalAlignment
import com.itextpdf.layout.property.Property
import com.itextpdf.layout.property.TextAlignment
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val LOCALE_CODE = "LOCALE_CODE"
    private var isReadPermissionGranted = false
    private var isWritePermissionGranted = false
    private var isManagePermissionGranted = false
    private val PERMISSION_REQUEST_CODE = 17

    private val mPermissionResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.READ_EXTERNAL_STORAGE] != null) {
            isReadPermissionGranted =
                java.lang.Boolean.TRUE == result[Manifest.permission.READ_EXTERNAL_STORAGE]
        }
        if (result[Manifest.permission.WRITE_EXTERNAL_STORAGE] != null) {
            isWritePermissionGranted =
                java.lang.Boolean.TRUE == result[Manifest.permission.WRITE_EXTERNAL_STORAGE]
        }
        if (result[Manifest.permission.MANAGE_EXTERNAL_STORAGE] != null) {
            isWritePermissionGranted =
                java.lang.Boolean.TRUE == result[if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE
                } else {
                }]
        }
    }
    private val nameLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == 100 || result.data != null) {
                val res = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)!!
                binding.etName.setText(res[0])
            }
        }
    private val symptomsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == 100 || result.data != null) {
                val res = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)!!
                binding.etSymptoms.setText(res[0])
            }
        }
    private val diagnoseLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == 100 || result.data != null) {
                val res = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)!!
                binding.etDiagnose.setText(res[0])
            }
        }
    private val medicineLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == 100 || result.data != null) {
                val res = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)!!
                binding.etMedicine.setText(res[0])
//            for (i in 0 until rowNumber) {
//                val row = TableRow(this@MainActivity)
//                for (j in 0 until columnNumber) {
//                    val value: Int = random.nextInt(100) + 1
//                    val tv = TextView(this@MainActivity)
//                    tv.text = value.toString()
//                    row.addView(tv)
//                }
//                table.addView(row)
//            }
            }
        }
    private val noteLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == 100 || result.data != null) {
                val res = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)!!
                binding.etNote.setText(res[0])
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (!checkPermission()) {
            requestPermission()
        }
        binding.voiceName.setOnClickListener {
            speechToText("name")
        }
        binding.voiceSymptoms.setOnClickListener {
            speechToText("symptoms")
        }
        binding.voiceDiagnose.setOnClickListener {
            speechToText("diagnose")
        }
        binding.voiceMedicine.setOnClickListener {
            speechToText("medicine")
        }
        binding.voiceNote.setOnClickListener {
            speechToText("note")
        }
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val formatted = current.format(formatter)
        binding.etDate.setText(formatted)
        binding.btnCreate.setOnClickListener {
            try {
                val name = binding.etName.text.toString()
                val symptoms = binding.etSymptoms.text.toString()
                val diagnose = binding.etDiagnose.text.toString()
                val medicine = binding.etMedicine.text.toString()
                val note = binding.etNote.text.toString()
                val date = binding.etDate.text.toString()

                if (name.isEmpty() || symptoms.isEmpty() || diagnose.isEmpty() || medicine.isEmpty() || note.isEmpty() || date.isEmpty()) {
                    Toast.makeText(this, "Please fill all the fields", Toast.LENGTH_SHORT).show()
                } else {
                    createPdf(
                        name, symptoms, diagnose, medicine, note, date
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        binding.btnShare.setOnClickListener {
            try {
                val pdfPath =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .toString() + "/prescription.pdf"
                startFileShareIntent(pdfPath, this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createPdf(
        name: String,
        symptoms: String,
        diagnose: String,
        medicine: String,
        note: String,
        date: String
    ) {
        try {
            val pdfPath =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .toString()
            val file = File(pdfPath, "prescription.pdf")
            val outputStream: OutputStream = FileOutputStream(file)
            val writer = PdfWriter(outputStream)
            val pdfDocument = PdfDocument(writer)
            val document = Document(pdfDocument)

            //val fontPath = "res/font/noto_sans_devanagari.ttf"
//            val fontPath = "res/font/mangal.ttf"
//            val hndFont = PdfFontFactory.createFont(fontPath, PdfEncodings.IDENTITY_H)
            val fontSet = FontSet()
            fontSet.addFont("res/font/noto_sans_devanagari.ttf")
            document.fontProvider = FontProvider(fontSet)
            document.setProperty(Property.FONT, arrayOf("NotoSans"))

            pdfDocument.defaultPageSize = PageSize.A4
            document.setMargins(0F, 0F, 0F, 0F)

            val title =
                Paragraph("E -Prescription").setBold()
                    .setHorizontalAlignment(HorizontalAlignment.CENTER)
                    .setTextAlignment(TextAlignment.CENTER).setFontSize(18F)
            val namePara =
                Paragraph("Name : $name").setHorizontalAlignment(HorizontalAlignment.LEFT)
                    .setMarginLeft(10F).setTextAlignment(TextAlignment.LEFT).setFontSize(12F)
            val symptomsPara =
                Paragraph("Symptoms : $symptoms").setHorizontalAlignment(HorizontalAlignment.LEFT)
                    .setMarginLeft(10F).setTextAlignment(TextAlignment.LEFT).setFontSize(12F)
            val diagnosePara =
                Paragraph("Diagnose : $diagnose").setHorizontalAlignment(HorizontalAlignment.LEFT)
                    .setMarginLeft(10F).setTextAlignment(TextAlignment.LEFT).setFontSize(12F)
            val medicinePara =
                Paragraph("Medicine : $medicine").setHorizontalAlignment(HorizontalAlignment.LEFT)
                    .setMarginLeft(10F).setTextAlignment(TextAlignment.LEFT).setFontSize(12F)
            val notePara =
                Paragraph("Note : $note").setHorizontalAlignment(HorizontalAlignment.LEFT)
                    .setMarginLeft(10F).setTextAlignment(TextAlignment.LEFT).setFontSize(12F)
            val datePara =
                Paragraph("Date : $date").setHorizontalAlignment(HorizontalAlignment.LEFT)
                    .setMarginLeft(10F).setTextAlignment(TextAlignment.LEFT).setFontSize(12F)

//        val width = floatArrayOf(100F, 100F, 100F)
//        val table = Table(width)
//        table.setMargin(10F)
//        table.setHorizontalAlignment(HorizontalAlignment.CENTER)
//
//        table.addCell(Cell().add(Paragraph("Medicine Name")))
//        table.addCell(Cell().add(Paragraph("Morning")))
//        table.addCell(Cell().add(Paragraph("Night")))
//
//        table.addCell(Cell().add(Paragraph("Arbitel")))
//        table.addCell(Cell().add(Paragraph("Yes")))
//        table.addCell(Cell().add(Paragraph("No")))
//
//        table.addCell(Cell().add(Paragraph("Cipla")))
//        table.addCell(Cell().add(Paragraph("No")))
//        table.addCell(Cell().add(Paragraph("Yes")))
//
//        table.addCell(Cell().add(Paragraph("Paracetamol")))
//        table.addCell(Cell().add(Paragraph("Yes")))
//        table.addCell(Cell().add(Paragraph("Yes")))

            document.add(title)
            document.add(namePara)
            document.add(symptomsPara)
            document.add(diagnosePara)
            document.add(medicinePara)
            document.add(notePara)
            document.add(datePara)
            //document.add(table)
            document.close()
            Toast.makeText(this, "Prescription created", Toast.LENGTH_SHORT).show()
//            binding.btnCreate.visibility = View.GONE
//            binding.btnShare.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("PRIYANSHU", "createPdf: ${e.message}")
            Toast.makeText(this, "${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startFileShareIntent(
        filePath: String,
        context: Context
    ) { // pass the file path where the actual file is located.
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type =
                "application/pdf"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Sharing file from the App"
            )
            putExtra(
                Intent.EXTRA_TEXT,
                "Sharing file from the App with some description"
            )
            val fileURI = FileProvider.getUriForFile(
                context, context.packageName + ".provider",
                File(filePath)
            )
            putExtra(Intent.EXTRA_STREAM, fileURI)
        }
        startActivity(shareIntent)
    }

    private fun speechToText(buttonId: String) {
        val sharedPreferences = getSharedPreferences("PRESCRIPTION", Context.MODE_PRIVATE)
        val localeCode = sharedPreferences.getString(LOCALE_CODE, "en");
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
//        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeCode);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeCode);
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, localeCode);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak")
        when (buttonId) {
            "name" -> nameLauncher.launch(intent)
            "symptoms" -> symptomsLauncher.launch(intent)
            "diagnose" -> diagnoseLauncher.launch(intent)
            "medicine" -> medicineLauncher.launch(intent)
            "note" -> noteLauncher.launch(intent)
            else -> Toast.makeText(this, "Invalid button", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val sharedPreferences = getSharedPreferences("PRESCRIPTION", Context.MODE_PRIVATE)
        when (item.itemId) {
            R.id.english -> {
                sharedPreferences.edit().putString(LOCALE_CODE, "en").apply()
                Toast.makeText(this, "Set voice input to English", Toast.LENGTH_SHORT).show()
            }
            R.id.hindi -> {
                sharedPreferences.edit().putString(LOCALE_CODE, "hi").apply()
                Toast.makeText(this, "Set voice input to Hindi", Toast.LENGTH_SHORT).show()
            }
            R.id.marathi -> {
                sharedPreferences.edit().putString(LOCALE_CODE, "mr").apply()
                Toast.makeText(this, "Set voice input to Marathi", Toast.LENGTH_SHORT).show()
            }
            R.id.erase -> {
                binding.etName.setText("")
                binding.etSymptoms.setText("")
                binding.etDiagnose.setText("")
                binding.etMedicine.setText("")
                binding.etNote.setText("")
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val result1 =
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val result2 =
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            result1 == PackageManager.PERMISSION_GRANTED && result2 == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> if (grantResults.isNotEmpty()) {
                val READ_EXTERNAL_STORAGE = grantResults[0] == PackageManager.PERMISSION_GRANTED
                val WRITE_EXTERNAL_STORAGE = grantResults[1] == PackageManager.PERMISSION_GRANTED
                if (READ_EXTERNAL_STORAGE && WRITE_EXTERNAL_STORAGE) {
                    // perform action when allow permission success
                } else {
                    Toast.makeText(this, "Allow permission for storage access!", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun requestPermission() {
        val minSDK = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        isReadPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        isWritePermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isManagePermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        isManagePermissionGranted = isManagePermissionGranted || minSDK
        isWritePermissionGranted = isWritePermissionGranted || minSDK

        val permissionRequest: MutableList<String> = ArrayList()
        if (!isReadPermissionGranted) {
            permissionRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (!isWritePermissionGranted) {
            permissionRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissionRequest.isNotEmpty()) {
            mPermissionResultLauncher.launch(permissionRequest.toTypedArray())
        }
    }
}

