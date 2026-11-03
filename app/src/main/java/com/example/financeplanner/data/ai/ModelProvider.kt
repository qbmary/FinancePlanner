package com.example.financeplanner.data.ai

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.example.financeplanner.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Отвечает за поиск и подготовку GGUF-файла модели.
 *
 * Приложение принципиально работает офлайн, поэтому модель никогда не скачивается
 * из сети. Файл попадает на устройство одним из трёх способов:
 *  1. пользователь выбирает его в системном диалоге (SAF, OpenDocument) — основной путь;
 *  2. файл заранее положен в приватную папку приложения files/models/ (adb push);
 *  3. файл упакован в APK в assets/models/ при сборке с -PbundleModel=true.
 */
class ModelProvider(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    private val modelsDir: File
        get() = File(appContext.filesDir, MODELS_DIR).apply { mkdirs() }

    /**
     * Ищет уже готовый к использованию файл, ничего не копируя.
     * Порядок: выбранный пользователем URI → files/models/ → (null).
     */
    suspend fun findReadyModel(): ModelFile? = withContext(Dispatchers.IO) {
        findPickedModel() ?: findModelInAppStorage()
    }

    /**
     * Ищет модель, при необходимости копируя её из assets (сборка с моделью внутри APK).
     * [onProgress] вызывается с 0..100 во время копирования.
     */
    suspend fun findOrImportModel(
        onProgress: (Int) -> Unit = {},
    ): ModelFile? = withContext(Dispatchers.IO) {
        findReadyModel() ?: importBundledModel(onProgress)
    }

    /**
     * Запоминает выбранный пользователем файл: валидирует GGUF и берёт
     * persistable-разрешение, чтобы файл был доступен после перезапуска.
     */
    suspend fun rememberPickedModel(uri: Uri): ModelFile = withContext(Dispatchers.IO) {
        val model = validate(uri, origin = ModelOrigin.PICKED)

        // Разрешение переживает перезапуск приложения (но не переустановку).
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Log.w(TAG, "Не удалось закрепить доступ к URI", it) }

        settingsRepository.setModelUri(uri.toString())
        model
    }

    /** Забывает ранее выбранный файл. */
    suspend fun forgetPickedModel() {
        withContext(Dispatchers.IO) { settingsRepository.setModelUri(null) }
    }

    /** Есть ли готовая модель внутри APK (каталог assets/models). */
    fun hasBundledModel(): Boolean = bundledAssetName() != null

    private suspend fun findPickedModel(): ModelFile? {
        val saved = settingsRepository.modelUri.first() ?: return null
        val uri = Uri.parse(saved)
        return runCatching { validate(uri, ModelOrigin.PICKED) }
            .onFailure { Log.w(TAG, "Сохранённый URI модели недоступен: $saved", it) }
            .getOrNull()
    }

    private fun findModelInAppStorage(): ModelFile? {
        val file = modelsDir
            .listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }
            ?.filter { it.length() > MIN_MODEL_BYTES }
            ?.maxByOrNull { it.length() }
            ?: return null

        return ModelFile(
            uri = contentUriFor(file).toString(),
            displayName = file.name,
            sizeBytes = file.length(),
            origin = ModelOrigin.APP_STORAGE,
        )
    }

    private fun importBundledModel(onProgress: (Int) -> Unit): ModelFile? {
        val assetName = bundledAssetName() ?: return null
        val assetPath = "$MODELS_DIR/$assetName"
        val target = File(modelsDir, assetName)
        val temp = File(modelsDir, "$assetName.part")

        if (target.length() > MIN_MODEL_BYTES) {
            onProgress(100)
            return ModelFile(
                uri = contentUriFor(target).toString(),
                displayName = target.name,
                sizeBytes = target.length(),
                origin = ModelOrigin.BUNDLED,
            )
        }

        val totalBytes = assetSize(assetPath)
        temp.delete()

        appContext.assets.open(assetPath).use { input ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(1024 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (totalBytes > 0) {
                        onProgress(((copied * 100) / totalBytes).toInt().coerceIn(0, 100))
                    }
                }
                output.flush()
                output.fd.sync()
            }
        }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }

        val size = target.length()
        if (size <= MIN_MODEL_BYTES) {
            target.delete()
            return null
        }

        onProgress(100)
        return ModelFile(
            uri = contentUriFor(target).toString(),
            displayName = target.name,
            sizeBytes = size,
            origin = ModelOrigin.BUNDLED,
        )
    }

    /**
     * llama.cpp в llamacpp-kotlin открывает модель только через
     * ContentResolver.openFileDescriptor(), поэтому даже локальный файл
     * отдаём как content:// через собственный FileProvider (см. res/xml/file_paths.xml).
     */
    private fun contentUriFor(file: File): Uri = FileProvider.getUriForFile(
        appContext,
        "${appContext.packageName}.fileprovider",
        file,
    )

    private fun bundledAssetName(): String? = runCatching {
        appContext.assets.list(MODELS_DIR)
            ?.firstOrNull { it.endsWith(".gguf", ignoreCase = true) }
    }.getOrNull()

    private fun assetSize(assetPath: String): Long = runCatching {
        appContext.assets.openFd(assetPath).use { it.length }
    }.getOrDefault(0L)

    private fun validate(uri: Uri, origin: ModelOrigin): ModelFile {
        val name = displayName(uri)
        val size = sizeOf(uri) ?: 0L

        if (size in 1..MIN_MODEL_BYTES) {
            throw IllegalArgumentException(
                "Файл «$name» слишком мал (${size / (1024 * 1024)} МБ). " +
                    "Похоже, это не GGUF-модель Qwen."
            )
        }

        // Первые 4 байта любого GGUF-файла — "GGUF".
        val header = ByteArray(4)
        val read = contentResolver.openInputStream(uri)?.use { it.read(header) } ?: -1
        if (read < 4 || String(header, Charsets.US_ASCII) != "GGUF") {
            throw IllegalArgumentException(
                "Файл «$name» не похож на GGUF-модель (нет заголовка GGUF). " +
                    "Выберите файл *.gguf, например qwen2.5-1.5b-instruct-q4_k_m.gguf."
            )
        }

        return ModelFile(
            uri = uri.toString(),
            displayName = name,
            sizeBytes = size,
            origin = origin,
        )
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.lastPathSegment?.substringAfterLast('/') ?: DEFAULT_NAME
        }

        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        cursor.getString(index)?.let { return it }
                    }
                }
            }

        return uri.lastPathSegment?.substringAfterLast('/') ?: DEFAULT_NAME
    }

    private fun sizeOf(uri: Uri): Long? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let { File(it).length() }
        }

        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) {
                        return cursor.getLong(index)
                    }
                }
            }

        // Некоторые провайдеры не отдают размер — пробуем через fd.
        return runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "ModelProvider"

        const val MODELS_DIR = "models"
        const val DEFAULT_MODEL_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        private const val DEFAULT_NAME = "model.gguf"

        /** Qwen-1.5B Q4_K_M ≈ 1.05 ГБ; всё, что меньше 300 МБ, — точно не она. */
        private const val MIN_MODEL_BYTES = 300L * 1024L * 1024L
    }
}
