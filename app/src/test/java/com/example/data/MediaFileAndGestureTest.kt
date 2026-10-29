package com.example.data

import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.MediaFileDao
import com.example.data.model.MediaFile
import com.example.helpers.HapticPulseType
import com.example.helpers.LockedGestureMode
import com.example.helpers.PlayerGestureHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaFileAndGestureTest {

    private lateinit var database: AppDatabase
    private lateinit var mediaFileDao: MediaFileDao
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mediaFileDao = database.mediaFileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun mediaFileDao_insertDeleteAndTogglePrivateStatus_worksCorrectly() = runBlocking {
        val file = MediaFile(
            id = "media_101",
            title = "8K Studio Demo Reel",
            path = "/storage/emulated/0/Movies/demo_8k.mp4",
            duration = 185_000L,
            isPrivate = false,
            timestamp = 1700000000L
        )

        mediaFileDao.insert(file)

        val fetched = mediaFileDao.getMediaFileById("media_101")
        assertEquals("8K Studio Demo Reel", fetched?.title)
        assertFalse(fetched?.isPrivate ?: true)

        // Toggle private status ON
        val toggledToPrivate = mediaFileDao.toggleAndGetPrivateStatus("media_101")
        assertTrue(toggledToPrivate)
        assertEquals(1, mediaFileDao.getPrivateMediaFiles().first().size)
        assertEquals(0, mediaFileDao.getPublicMediaFiles().first().size)

        // Toggle private status OFF
        val toggledToPublic = mediaFileDao.toggleAndGetPrivateStatus("media_101")
        assertFalse(toggledToPublic)
        assertEquals(0, mediaFileDao.getPrivateMediaFiles().first().size)
        assertEquals(1, mediaFileDao.getPublicMediaFiles().first().size)

        // Delete media file
        mediaFileDao.delete(fetched!!)
        assertNull(mediaFileDao.getMediaFileById("media_101"))
    }

    @Test
    fun playerGestureHelper_handlesMotionEventsAndHapticFeedbackConstants() {
        var brightness = 48
        var volume = 95
        var positionMs = 20_000L
        val durationMs = 120_000L

        val helper = PlayerGestureHelper(
            context = context,
            hostView = null,
            getBrightnessPercent = { brightness },
            getVolumePercent = { volume },
            getCurrentPositionMs = { positionMs },
            getDurationMs = { durationMs },
            onBrightnessChanged = { newPct, _ -> brightness = newPct },
            onVolumeChanged = { newPct, _ -> volume = newPct },
            onSeekChanged = { newPos, _ -> positionMs = newPos }
        )

        val downTime = SystemClock.uptimeMillis()

        // 1. Left-side vertical swipe UP -> Brightness increases across 50% threshold
        val downLeft = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 800f, 0)
        val moveLeftUp = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 202f, 650f, 0)
        val upLeft = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 202f, 650f, 0)

        helper.onTouchEvent(downLeft, viewportWidth = 1080f, viewportHeight = 1920f)
        helper.onTouchEvent(moveLeftUp, viewportWidth = 1080f, viewportHeight = 1920f)
        assertEquals(LockedGestureMode.VERTICAL_BRIGHTNESS, helper.currentGestureMode)
        assertTrue(brightness > 50)
        helper.onTouchEvent(upLeft, viewportWidth = 1080f, viewportHeight = 1920f)

        downLeft.recycle()
        moveLeftUp.recycle()
        upLeft.recycle()

        // 2. Right-side vertical swipe UP -> Volume increases into >100% Boost zone
        val downRight = MotionEvent.obtain(downTime, downTime + 200, MotionEvent.ACTION_DOWN, 900f, 800f, 0)
        val moveRightUp = MotionEvent.obtain(downTime, downTime + 250, MotionEvent.ACTION_MOVE, 900f, 620f, 0)
        val upRight = MotionEvent.obtain(downTime, downTime + 300, MotionEvent.ACTION_UP, 900f, 620f, 0)

        helper.onTouchEvent(downRight, viewportWidth = 1080f, viewportHeight = 1920f)
        helper.onTouchEvent(moveRightUp, viewportWidth = 1080f, viewportHeight = 1920f)
        assertEquals(LockedGestureMode.VERTICAL_VOLUME, helper.currentGestureMode)
        assertTrue(volume > 100)
        helper.onTouchEvent(upRight, viewportWidth = 1080f, viewportHeight = 1920f)

        downRight.recycle()
        moveRightUp.recycle()
        upRight.recycle()

        // 3. Horizontal swipe RIGHT -> Seeks forward
        val downSeek = MotionEvent.obtain(downTime, downTime + 400, MotionEvent.ACTION_DOWN, 300f, 500f, 0)
        val moveSeekRight = MotionEvent.obtain(downTime, downTime + 450, MotionEvent.ACTION_MOVE, 650f, 505f, 0)
        val upSeek = MotionEvent.obtain(downTime, downTime + 500, MotionEvent.ACTION_UP, 650f, 505f, 0)

        helper.onTouchEvent(downSeek, viewportWidth = 1080f, viewportHeight = 1920f)
        helper.onTouchEvent(moveSeekRight, viewportWidth = 1080f, viewportHeight = 1920f)
        assertEquals(LockedGestureMode.HORIZONTAL_SEEK, helper.currentGestureMode)
        assertTrue(positionMs > 20_000L)
        helper.onTouchEvent(upSeek, viewportWidth = 1080f, viewportHeight = 1920f)

        downSeek.recycle()
        moveSeekRight.recycle()
        upSeek.recycle()

        // 4. Verify HapticFeedbackConstants mapping
        assertEquals(
            HapticFeedbackConstants.CLOCK_TICK,
            PlayerGestureHelper.mapPulseToHapticConstant(HapticPulseType.STEP_TICK)
        )
        assertEquals(
            HapticFeedbackConstants.VIRTUAL_KEY,
            PlayerGestureHelper.mapPulseToHapticConstant(HapticPulseType.MIDPOINT_TICK)
        )
    }
}
