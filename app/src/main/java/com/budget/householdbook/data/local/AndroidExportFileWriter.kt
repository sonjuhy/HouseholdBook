package com.budget.householdbook.data.local

import android.content.Context
import android.net.Uri
import com.budget.householdbook.domain.repository.ExportFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CSV 내보내기: 사용자가 파일 저장 창(ACTION_CREATE_DOCUMENT)에서 고른 위치에 UTF-8로 저장
 * - 저장소 권한 없이 사용자가 고른 그 파일에만 씀
 */
class AndroidExportFileWriter(private val context: Context) : ExportFileWriter {
    override suspend fun writeText(uri: String, text: String) = withContext(Dispatchers.IO) {
        // "wt": 같은 파일을 다시 고르면 이전 내용을 지우고 새로 씀
        val stream = context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
            ?: error("파일을 열지 못했습니다.")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
