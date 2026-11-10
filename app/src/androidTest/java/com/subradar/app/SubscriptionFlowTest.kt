package com.subradar.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createsSubscriptionAndOpensCalendar() {
        val activity = compose.activity
        val name = "Compose test ${System.currentTimeMillis()}"
        val add = hasContentDescription(activity.getString(R.string.add_subscription))
        compose.waitUntil(10_000) { compose.onAllNodes(add).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(add).performClick()
        compose.onNodeWithTag("subscription-name").performTextInput(name)
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("subscription-amount"))
        compose.onNodeWithTag("subscription-amount").performTextInput("4.99")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("save-subscription"))
        compose.onNodeWithTag("save-subscription").performClick()
        val list = hasText(activity.getString(R.string.subscriptions), substring = false)
        compose.waitUntil(10_000) { compose.onAllNodes(list).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(list).performClick()
        compose.onNodeWithText(name).assertIsDisplayed().performClick()
        compose.onNodeWithText(activity.getString(R.string.edit_subscription)).assertIsDisplayed()
        compose.onNodeWithContentDescription(activity.getString(R.string.back)).performClick()
        compose.onNodeWithText(activity.getString(R.string.calendar)).performClick()
        compose.onNodeWithContentDescription(activity.getString(R.string.next_month)).assertIsDisplayed()
    }
}
