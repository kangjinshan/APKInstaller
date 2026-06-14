package com.apkinstaller.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ApkDownloaderTest {

    @Test
    fun parseTvStoreVersionInfoBuildsDownloadUrlForLatestTvAppStoreVersion() {
        val response = """
            {
              "app": {
                "id": 6,
                "name": "TV App Store",
                "package_name": "com.jinshan.tvappstore",
                "versions": [
                  {
                    "id": 11,
                    "version_name": "1.0.0",
                    "version_code": 1,
                    "changelog": "新增 TV App Store 应用上架",
                    "file_size": 8178410,
                    "created_at": "2026-05-10 12:26:53"
                  }
                ]
              }
            }
        """.trimIndent()

        val versionInfo = ApkDownloader.parseTvStoreVersionInfo(response)

        assertNotNull(versionInfo)
        versionInfo!!
        assertEquals("1.0.0", versionInfo.version)
        assertEquals(1, versionInfo.versionCode)
        assertEquals("https://tvstore.jinshanweb.com/api/download/11", versionInfo.downloadUrl)
        assertEquals("tv-app-store-1.0.0.apk", versionInfo.fileName)
        assertEquals(8178410L, versionInfo.fileSize)
        assertEquals("新增 TV App Store 应用上架", versionInfo.releaseNotes)
    }

    @Test
    fun parseTvStoreVersionInfoReturnsNullWhenNoVersionsExist() {
        val response = """
            {
              "app": {
                "id": 6,
                "name": "TV App Store",
                "package_name": "com.jinshan.tvappstore",
                "versions": []
              }
            }
        """.trimIndent()

        assertNull(ApkDownloader.parseTvStoreVersionInfo(response))
    }
}
