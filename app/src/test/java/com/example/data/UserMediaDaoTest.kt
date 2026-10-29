package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.UserMediaDao
import com.example.data.model.UserMediaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserMediaDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var userMediaDao: UserMediaDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userMediaDao = database.userMediaDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndQueryMediaMetadata() = runBlocking {
        val media = UserMediaEntity(
            id = "media_001",
            title = "Vacation Memories.mp4",
            path = "/storage/emulated/0/DCIM/Camera/Vacation.mp4",
            isPrivate = false,
            artist = "Personal Video",
            duration = 124000L,
            size = 45000000L,
            isVideo = true,
            folderName = "Camera"
        )

        userMediaDao.insertMedia(media)

        val retrieved = userMediaDao.getMediaById("media_001")
        assertNotNull(retrieved)
        assertEquals("Vacation Memories.mp4", retrieved?.title)
        assertEquals("/storage/emulated/0/DCIM/Camera/Vacation.mp4", retrieved?.path)
        assertEquals(false, retrieved?.isPrivate)
        assertEquals(124000L, retrieved?.duration)
        assertEquals("Camera", retrieved?.folderName)
    }

    @Test
    fun verifyFolderProtectionWithIsPrivateFlag() = runBlocking {
        val publicMedia = UserMediaEntity(
            id = "pub_01",
            title = "Podcast Episode 1.mp3",
            path = "/storage/Music/Podcast.mp3",
            isPrivate = false,
            isVideo = false,
            folderName = "Podcasts"
        )

        val privateMedia = UserMediaEntity(
            id = "priv_01",
            title = "Personal ID Scan.mp4",
            path = "/storage/Documents/Personal_ID.mp4",
            isPrivate = true,
            isVideo = true,
            folderName = "Confidential"
        )

        userMediaDao.insertAll(listOf(publicMedia, privateMedia))

        // Query public media
        val publicList = userMediaDao.getPublicMedia().first()
        assertEquals(1, publicList.size)
        assertEquals("pub_01", publicList[0].id)
        assertFalse(publicList[0].isPrivate)

        // Query private media for folder protection
        val privateList = userMediaDao.getPrivateMedia().first()
        assertEquals(1, privateList.size)
        assertEquals("priv_01", privateList[0].id)
        assertTrue(privateList[0].isPrivate)
    }

    @Test
    fun setPrivateStatusLocksAndUnlocksFolderFile() = runBlocking {
        val media = UserMediaEntity(
            id = "file_123",
            title = "Secret Project Plan.mp4",
            path = "/storage/Movies/Secret.mp4",
            isPrivate = false
        )
        userMediaDao.insertMedia(media)

        // Initially public
        assertEquals(false, userMediaDao.isMediaPrivate("file_123"))

        // Lock into private safe folder
        userMediaDao.setPrivateStatus("file_123", true)
        assertEquals(true, userMediaDao.isMediaPrivate("file_123"))
        assertTrue(userMediaDao.isItemInPrivateVault("file_123"))

        // Unlock back to public
        userMediaDao.setPrivateStatus("file_123", false)
        assertEquals(false, userMediaDao.isMediaPrivate("file_123"))
        assertFalse(userMediaDao.isItemInPrivateVault("file_123"))
    }

    @Test
    fun togglePrivateStatusSwitchesFlag() = runBlocking {
        val media = UserMediaEntity(
            id = "toggle_item",
            title = "Audio Diary.mp3",
            path = "/storage/Recordings/Diary.mp3",
            isPrivate = false
        )
        userMediaDao.insertMedia(media)

        // Toggle from false to true
        userMediaDao.togglePrivateStatus("toggle_item")
        assertEquals(true, userMediaDao.isMediaPrivate("toggle_item"))

        // Toggle from true to false
        userMediaDao.togglePrivateStatus("toggle_item")
        assertEquals(false, userMediaDao.isMediaPrivate("toggle_item"))
    }

    @Test
    fun batchPrivateStatusProtectsMultipleFiles() = runBlocking {
        val items = (1..5).map { i ->
            UserMediaEntity(
                id = "item_$i",
                title = "Video $i.mp4",
                path = "/storage/Videos/Video$i.mp4",
                isPrivate = false
            )
        }
        userMediaDao.insertAll(items)

        // Batch protect items 1, 3, and 5
        userMediaDao.setPrivateStatusBatch(listOf("item_1", "item_3", "item_5"), true)

        val privateList = userMediaDao.getPrivateMedia().first()
        assertEquals(3, privateList.size)
        val privateIds = privateList.map { it.id }.toSet()
        assertTrue(privateIds.containsAll(listOf("item_1", "item_3", "item_5")))

        val publicList = userMediaDao.getPublicMedia().first()
        assertEquals(2, publicList.size)
    }

    @Test
    fun folderAndSearchQueries() = runBlocking {
        val media1 = UserMediaEntity(
            id = "m1",
            title = "Rock Anthem",
            artist = "Starlight Band",
            path = "/storage/Music/Rock/anthem.mp3",
            folderName = "Rock",
            isPrivate = false
        )
        val media2 = UserMediaEntity(
            id = "m2",
            title = "Rock Ballad",
            artist = "Moonlight Duo",
            path = "/storage/Music/Rock/ballad.mp3",
            folderName = "Rock",
            isPrivate = false
        )
        val media3 = UserMediaEntity(
            id = "m3",
            title = "Jazz Solo",
            artist = "Miles Duo",
            path = "/storage/Music/Jazz/solo.mp3",
            folderName = "Jazz",
            isPrivate = false
        )

        userMediaDao.insertAll(listOf(media1, media2, media3))

        val folders = userMediaDao.getFoldersFlow(isPrivate = false).first()
        assertEquals(listOf("Jazz", "Rock"), folders)

        val rockMedia = userMediaDao.getMediaByFolderFlow("Rock", isPrivate = false).first()
        assertEquals(2, rockMedia.size)

        val searchResults = userMediaDao.searchMediaFlow("Ballad", isPrivate = false).first()
        assertEquals(1, searchResults.size)
        assertEquals("Rock Ballad", searchResults[0].title)
    }
}
