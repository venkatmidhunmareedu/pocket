package app.pocket.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import app.pocket.data.Backup
import app.pocket.databinding.ActivityBackupBinding
import app.pocket.data.Prefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Backup to / restore from a user-chosen folder (Storage Access Framework, no storage permission needed). */
class BackupRestoreActivity : AppCompatActivity() {
    private lateinit var b: ActivityBackupBinding

    private val pickDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            Prefs.backupDir = uri.toString()
            refresh()
            Toast.makeText(this, "Pocket will create new backup files in your selected directory.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.toolbar.setNavigationOnClickListener { finish() }
        b.btnDir.setOnClickListener { pickDir.launch(null) }
        b.btnBackup.setOnClickListener { backup() }
        b.btnRestore.setOnClickListener { listBackups() }
        refresh()
    }

    private fun dir(): DocumentFile? =
        Prefs.backupDir?.let { DocumentFile.fromTreeUri(this, Uri.parse(it)) }?.takeIf { it.exists() && it.canWrite() }

    private fun refresh() {
        val d = dir()
        b.tvDir.text = if (d != null) "Backup directory: ${d.name}" else
            "Please choose a directory/folder, where Pocket can create new backup files, or search for older backup files for you to restore."
        b.btnBackup.isEnabled = d != null
        b.btnRestore.isEnabled = d != null
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun backup() {
        val d = dir() ?: return toast("Unable to access selected backup directory, make sure that it has not been renamed, moved or deleted. Or try selecting a different backup directory.")
        val name = "PocketBackup_" + SimpleDateFormat("dd_MM_yy_hmm", Locale.ENGLISH).format(Date()) + ".mbak"
        try {
            val f = d.createFile("application/octet-stream", name) ?: throw IOException()
            contentResolver.openOutputStream(f.uri, "wt")!!.use { it.write(Backup.toJson().toByteArray()) }
            toast("Back up successful")
        } catch (e: Exception) {
            toast("Unable to create backup file in selected directory, please try again after some time or select a different location.")
        }
    }

    private fun listBackups() {
        val d = dir() ?: return toast("Unable to access selected backup directory, make sure that it has not been renamed, moved or deleted. Or try selecting a different backup directory.")
        val files = d.listFiles().filter { it.isFile && it.name?.endsWith(".mbak") == true }.sortedByDescending { it.lastModified() }
        if (files.isEmpty()) return toast("No backup file(.mbak) found in this directory. Also make sure that the directory exists.")
        MaterialAlertDialogBuilder(this)
            .setTitle("Select the backup you want to restore:")
            .setItems(files.map { it.name }.toTypedArray()) { _, i ->
                MaterialAlertDialogBuilder(this).setTitle("Restore backup?")
                    .setMessage("The current records, categories, budgets and accounts will be deleted and the backup will be restored. Are you really sure?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore") { _, _ -> restore(files[i]) }.show()
            }.show()
    }

    private fun restore(f: DocumentFile) {
        try {
            val json = contentResolver.openInputStream(f.uri)!!.use { it.readBytes().decodeToString() }
            Backup.restore(json)
            toast("Restored successfully")
        } catch (e: Backup.Corrupt) {
            toast("The file is either corrupted, or modified by the user. Try with another backup file.")
        } catch (e: Exception) {
            toast("Unable to restore selected backup file. Make sure that both the selected file and backup directory exist.")
        }
    }
}
