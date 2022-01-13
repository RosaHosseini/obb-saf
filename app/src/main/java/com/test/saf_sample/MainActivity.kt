package com.test.saf_sample

import android.app.Activity
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.axanor.saf_sample.R
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val EXTERNAL_STORAGE_PROVIDER_AUTHORITY =
            "com.android.externalstorage.documents"
        private const val OBB_DOC_ID = "primary:Android/obb"
        private const val PACKAGE_NAME = "dev"
    }

    private val mObbUri: Uri = DocumentsContract.buildTreeDocumentUri(
        EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
        OBB_DOC_ID
    )

    private val handleInstallIntentActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        openDocumentTree()
    }

    private val handleIOIntentActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            val directoryUri = it.data?.data ?: return@registerForActivityResult
            contentResolver.takePersistableUriPermission(
                directoryUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            openDocumentTree()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.test_txt).setOnClickListener {
            openDocumentTree()
        }
    }

    /** Get needed permission if not granted and then create a test doc in obb dir */
    private fun openDocumentTree() {
        when {
            !isInstallPermissionGranted() -> askInstallPermission()
            !areIOPermissionsGranted() -> askIOPermission()
            else -> makeDoc(PACKAGE_NAME)
        }
    }

    /** Return  directory  parentDocId/packageName and create it if it does not exist */
    private fun getDir(parentDocId: String, packageName: String): DocumentFile? {
        val parentUri: Uri = DocumentsContract.buildDocumentUriUsingTree(
            mObbUri,
            parentDocId
        )
        val parentDoc: DocumentFile? = DocumentFile.fromTreeUri(this, parentUri)
        return parentDoc?.findFile(packageName) ?: parentDoc?.createDirectory(packageName)
    }

    /** Create or write test.txt in Android/data/obb/packageName  **/
    private fun makeDoc(packageName: String) {
        val dir: DocumentFile? = getDir(OBB_DOC_ID, packageName)
        if (dir == null || dir.exists().not()) {
            //the folder was probably deleted
            //ask user to choose another folder
            showMessage("Folder deleted, please choose another!")
        } else {
            val file = dir.createFile("*/txt", "test.txt")
            if (file != null && file.canWrite()) {
                alterDocument(file.uri)
            } else {
                showMessage("Write error!")
            }
        }
    }

    /** Just a test function to write something into a file, from https://developer.android.com  */
    private fun alterDocument(uri: Uri) {
        try {
            contentResolver.openFileDescriptor(uri, "w")?.use { parcelFileDescriptor ->
                FileOutputStream(parcelFileDescriptor.fileDescriptor).use {
                    it.write(("String written at ${System.currentTimeMillis()}\n").toByteArray())
                    showMessage("File Write OK!")
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /** this will present the user with folder browser to select a folder for our data */
    private fun askIOPermission() {
        val uri = DocumentsContract.buildDocumentUri(
            EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
            OBB_DOC_ID
        )
        val intent = getPrimaryVolume().createOpenDocumentTreeIntent()
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
        handleIOIntentActivityResult.launch(intent)
    }

    private fun getPrimaryVolume(): StorageVolume {
        return (getSystemService(STORAGE_SERVICE) as StorageManager).primaryStorageVolume
    }

    /** Ask permission for installing apps */
    private fun askInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isInstallPermissionGranted()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse(String.format("package:%s", packageName))
                handleInstallIntentActivityResult.launch(intent)
            }
        }
    }

    /** Are permission for write and read in external storage Android directory granted */
    private fun areIOPermissionsGranted(): Boolean {
        val treeUri: Uri = DocumentsContract.buildTreeDocumentUri(
            EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
            OBB_DOC_ID
        )
        val uriPermission: UriPermission? = contentResolver.persistedUriPermissions.find {
            it.uri == treeUri && (it.isWritePermission or it.isReadPermission)
        }
        return uriPermission.isNotNull()
    }

    /** Is permission for installing apps granted */
    private fun isInstallPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Showing a Toast message */
    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

fun <T> T?.isNotNull(): Boolean {
    return this?.let { true } ?: false
}