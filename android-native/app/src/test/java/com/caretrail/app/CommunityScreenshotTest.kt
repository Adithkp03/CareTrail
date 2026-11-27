package com.caretrail.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.CommunityMsg
import com.caretrail.app.ui.*
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h820dp-port")
class CommunityScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val labels by lazy { L("en", emptyMap(), AppViewModel(ApplicationProvider.getApplicationContext<Application>())) }

    private val sample = listOf(
        CommunityMsg(id = "m1", author_id = "p1", nickname = "Anu M", body = "Hello everyone, 20 weeks today and finally feeling less tired", created_at = "2026-11-26T09:40:00"),
        CommunityMsg(id = "m2", author_id = "me", nickname = "Rani", body = "Same here, the second trimester is kinder", created_at = "2026-11-26T09:42:00", own = true),
        CommunityMsg(id = "m3", author_id = "p3", nickname = "Blocked member", body = "", created_at = "2026-11-26T09:45:00", author_blocked = true),
    )

    @Test fun joinCardShowsGuidelinesAndNotice() {
        compose.setContent { CareTrailTheme { Page(title = "Community room", back = {}) { CommunityJoinContent(labels, "", {}, null, false) {} } } }
        compose.onNodeWithText("Before you join").assertExists()
        compose.onNodeWithText("Accept and join").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test fun chatShowsNoticeMessagesAndBlockedMasking() {
        compose.setContent {
            CareTrailTheme {
                Page(title = "Community room", back = {}) {
                    CommunityChatContent(labels, sample, suspended = false, input = "", onInput = {}, inputProblem = null,
                        sending = false, error = "", onSend = {}, onReport = {}, onBlock = {}, onDelete = {})
                }
            }
        }
        compose.onNodeWithText("Anu M").assertExists()
        compose.onNodeWithText("Blocked member").assertExists()
        compose.onNodeWithText("Send").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test fun reportSheetListsEveryReason() {
        compose.setContent {
            CareTrailTheme {
                Page(title = "Community room", back = {}) {
                    CommunityReportContent(labels, sample[0], selected = "medical-advice", onSelect = {}, busy = false, onSend = {}, onCancel = {})
                }
            }
        }
        compose.onNodeWithText("Report this message").assertExists()
        compose.onNodeWithText("Telling others what medical care to take or skip").assertExists()
        compose.onNodeWithText("Send report").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test fun suspendedUserCanReadButNotPost() {
        compose.setContent {
            CareTrailTheme {
                Page(title = "Community room", back = {}) {
                    CommunityChatContent(labels, sample.take(1), suspended = true, input = "", onInput = {}, inputProblem = null,
                        sending = false, error = "", onSend = {}, onReport = {}, onBlock = {}, onDelete = {})
                }
            }
        }
        compose.onNodeWithText("Posting is paused for your account after a moderation review. You can still read the room.").assertExists()
        compose.onNodeWithText("Send").assertDoesNotExist()
        compose.onRoot().captureRoboImage()
    }
}
