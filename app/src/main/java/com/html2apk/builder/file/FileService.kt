package com.html2apk.builder.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

/** SAF 文件/文件夹选择后的拷贝服务 */
object FileService {

    private const val TAG = "HTML2APK.File"

    /** 拷贝单个文件到构建输入目录；返回目标路径（null=失败） */
    fun copySingleToBuildDir(ctx: Context, uri: Uri): String? {
        return try {
            val inputDir = File(ctx.filesDir, "work/input")
            inputDir.mkdirs()
            // 清理旧输入
            inputDir.listFiles()?.forEach { it.deleteRecursively() }

            val name = queryDisplayName(ctx, uri) ?: "index.html"
            val dest = File(inputDir, name)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Log.i(TAG, "copied single -> $dest")
            dest.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "copy single failed", e)
            null
        }
    }

    /** 拷贝整个文档树（文件夹）到构建输入目录；返回目录路径 */
    fun copyTreeToBuildDir(ctx: Context, treeUri: Uri): String? {
        return try {
            val inputDir = File(ctx.filesDir, "work/input")
            inputDir.mkdirs()
            inputDir.listFiles()?.forEach { it.deleteRecursively() }

            copyTreeRecursive(ctx, treeUri, inputDir)
            Log.i(TAG, "copied tree -> $inputDir")
            inputDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "copy tree failed", e)
            null
        }
    }

    private fun copyTreeRecursive(ctx: Context, uri: Uri, destDir: File) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        ctx.contentResolver.query(childrenUri, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val subDir = File(destDir, name)
                    subDir.mkdirs()
                    copyTreeRecursive(ctx, docUri, subDir)
                } else {
                    ctx.contentResolver.openInputStream(docUri)?.use { input ->
                        File(destDir, name).outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    /** 拷贝封面图标到构建目录（固定名 icon.png，覆盖旧图）；返回路径（null=失败） */
    fun copyIconToBuildDir(ctx: Context, uri: Uri): String? {
        return try {
            val workDir = File(ctx.filesDir, "work")
            workDir.mkdirs()
            val dest = File(workDir, "icon.png")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Log.i(TAG, "copied icon -> $dest")
            dest.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "copy icon failed", e)
            null
        }
    }

    private fun queryDisplayName(ctx: Context, uri: Uri): String? {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                return c.getString(0)
            }
        }
        return null
    }
}