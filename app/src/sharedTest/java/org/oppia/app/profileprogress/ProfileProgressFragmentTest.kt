package org.oppia.app.profileprogress

import android.app.Activity.RESULT_OK
import android.app.Application
import android.app.Instrumentation
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import dagger.Component
import dagger.Module
import dagger.Provides
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.oppia.app.R
import org.oppia.app.activity.ActivityComponent
import org.oppia.app.application.ActivityComponentFactory
import org.oppia.app.application.ApplicationComponent
import org.oppia.app.application.ApplicationInjector
import org.oppia.app.application.ApplicationInjectorProvider
import org.oppia.app.application.ApplicationModule
import org.oppia.app.application.ApplicationStartupListenerModule
import org.oppia.app.completedstorylist.CompletedStoryListActivity
import org.oppia.app.home.recentlyplayed.RecentlyPlayedActivity
import org.oppia.app.model.ProfileId
import org.oppia.app.ongoingtopiclist.OngoingTopicListActivity
import org.oppia.app.player.state.hintsandsolution.HintsAndSolutionConfigModule
import org.oppia.app.recyclerview.RecyclerViewMatcher.Companion.atPositionOnView
import org.oppia.app.shim.ViewBindingShimModule
import org.oppia.app.topic.TopicActivity
import org.oppia.app.utility.OrientationChangeAction.Companion.orientationLandscape
import org.oppia.domain.classify.InteractionsModule
import org.oppia.domain.classify.rules.continueinteraction.ContinueModule
import org.oppia.domain.classify.rules.dragAndDropSortInput.DragDropSortInputModule
import org.oppia.domain.classify.rules.fractioninput.FractionInputModule
import org.oppia.domain.classify.rules.imageClickInput.ImageClickInputModule
import org.oppia.domain.classify.rules.itemselectioninput.ItemSelectionInputModule
import org.oppia.domain.classify.rules.multiplechoiceinput.MultipleChoiceInputModule
import org.oppia.domain.classify.rules.numberwithunits.NumberWithUnitsRuleModule
import org.oppia.domain.classify.rules.numericinput.NumericInputRuleModule
import org.oppia.domain.classify.rules.ratioinput.RatioInputModule
import org.oppia.domain.classify.rules.textinput.TextInputRuleModule
import org.oppia.domain.onboarding.ExpirationMetaDataRetrieverModule
import org.oppia.domain.oppialogger.LogStorageModule
import org.oppia.domain.oppialogger.loguploader.LogUploadWorkerModule
import org.oppia.domain.oppialogger.loguploader.WorkManagerConfigurationModule
import org.oppia.domain.question.QuestionModule
import org.oppia.domain.topic.PrimeTopicAssetsControllerModule
import org.oppia.domain.topic.StoryProgressTestHelper
import org.oppia.domain.topic.TEST_STORY_ID_0
import org.oppia.domain.topic.TEST_TOPIC_ID_0
import org.oppia.testing.TestAccessibilityModule
import org.oppia.testing.TestCoroutineDispatchers
import org.oppia.testing.TestDispatcherModule
import org.oppia.testing.TestLogReportingModule
import org.oppia.testing.profile.ProfileTestHelper
import org.oppia.util.caching.testing.CachingTestModule
import org.oppia.util.gcsresource.GcsResourceModule
import org.oppia.util.logging.EnableConsoleLog
import org.oppia.util.logging.EnableFileLog
import org.oppia.util.logging.GlobalLogLevel
import org.oppia.util.logging.LogLevel
import org.oppia.util.logging.firebase.FirebaseLogUploaderModule
import org.oppia.util.parser.GlideImageLoaderModule
import org.oppia.util.parser.HtmlParserEntityTypeModule
import org.oppia.util.parser.ImageParsingModule
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/** Tests for [ProfileProgressFragment]. */
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(
  application = ProfileProgressFragmentTest.TestApplication::class,
  qualifiers = "port-xxhdpi"
)
class ProfileProgressFragmentTest {

  @Inject
  lateinit var profileTestHelper: ProfileTestHelper

  @Inject
  lateinit var storyProgressTestHelper: StoryProgressTestHelper

  @Inject
  lateinit var context: Context

  @Inject
  lateinit var testCoroutineDispatchers: TestCoroutineDispatchers

  private val internalProfileId = 0

  private lateinit var profileId: ProfileId

  @Before
  fun setUp() {
    Intents.init()
    setUpTestApplicationComponent()
    testCoroutineDispatchers.registerIdlingResource()
    profileTestHelper.initializeProfiles()
    profileId = ProfileId.newBuilder().setInternalId(internalProfileId).build()
    FirebaseApp.initializeApp(context)
  }

  @After
  fun tearDown() {
    testCoroutineDispatchers.unregisterIdlingResource()
    Intents.release()
  }

  private fun createProfileProgressActivityIntent(profileId: Int): Intent {
    return ProfileProgressActivity.createProfileProgressActivityIntent(
      ApplicationProvider.getApplicationContext(),
      profileId
    )
  }

  @Test
  fun testProfileProgressFragment_checkProfileName_profileNameIsCorrect() {
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("Admin"))
      onView(
        atPositionOnView(R.id.profile_progress_list, 0, R.id.profile_name_text_view)
      ).check(
        matches(withText("Admin"))
      )
    }
  }

  @Test
  fun testProfileProgressFragment_configurationChange_checkProfileName_profileNameIsCorrect() {
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      onView(isRoot()).perform(orientationLandscape())
      waitForTheView(withText("Admin"))
      onView(
        atPositionOnView(R.id.profile_progress_list, 0, R.id.profile_name_text_view)
      ).check(
        matches(withText("Admin"))
      )
    }
  }

  @Test
  fun testProfileProgressFragment_openProfilePictureEditDialog() {
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("Admin"))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0,
          R.id.profile_edit_image
        )
      ).perform(click())
      testCoroutineDispatchers.runCurrent()
      onView(withText(R.string.profile_progress_edit_dialog_title)).inRoot(isDialog())
        .check(matches(isDisplayed()))
    }
  }

  @Test
  fun testProfileProgressFragment_openProfilePictureEditDialog_configurationChange_dialogIsStillOpen() { // ktlint-disable max-line-length
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("Admin"))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0,
          R.id.profile_edit_image
        )
      ).perform(click())
      testCoroutineDispatchers.runCurrent()
      onView(withText(R.string.profile_progress_edit_dialog_title)).inRoot(isDialog())
        .check(matches(isDisplayed()))
      onView(isRoot()).perform(orientationLandscape())
      onView(withText(R.string.profile_progress_edit_dialog_title)).check(matches(isDisplayed()))
    }
  }

  @Test
  fun testProfileProgressFragment_imageSelectAvatar_checkGalleryIntent() {
    val expectedIntent: Matcher<Intent> = allOf(
      hasAction(Intent.ACTION_PICK),
      hasData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    )
    val activityResult = createGalleryPickActivityResultStub()
    intending(expectedIntent).respondWith(activityResult)
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("Admin"))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0,
          R.id.profile_edit_image
        )
      ).perform(click())
      testCoroutineDispatchers.runCurrent()
      onView(withText(R.string.profile_progress_edit_dialog_title)).inRoot(isDialog())
        .check(matches(isDisplayed()))
      onView(withText(R.string.profile_picture_edit_alert_dialog_choose_from_library))
        .perform(click())
      intended(expectedIntent)
    }
  }

  @Test
  fun testProfileProgressFragment_imageSelectAvatar_changeOrientation_checkGalleryIntent() {
    val expectedIntent: Matcher<Intent> = allOf(
      hasAction(Intent.ACTION_PICK),
      hasData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    )
    val activityResult = createGalleryPickActivityResultStub()
    intending(expectedIntent).respondWith(activityResult)
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("Admin"))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0,
          R.id.profile_edit_image
        )
      ).perform(click())
      testCoroutineDispatchers.runCurrent()
      onView(withText(R.string.profile_progress_edit_dialog_title)).inRoot(isDialog())
        .check(matches(isDisplayed()))
      onView(withText(R.string.profile_picture_edit_alert_dialog_choose_from_library))
        .perform(click())
      testCoroutineDispatchers.runCurrent()
      intended(expectedIntent)
      onView(isRoot()).perform(orientationLandscape())
      testCoroutineDispatchers.runCurrent()
      intended(expectedIntent)
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0,
          R.id.profile_edit_image
        )
      ).perform(click())
      testCoroutineDispatchers.runCurrent()
      // The dialog should still be open after a configuration change.
      onView(withText(R.string.profile_progress_edit_dialog_title)).inRoot(isDialog())
        .check(matches(isDisplayed()))
    }
  }

  @Test
  fun testProfileProgressFragmentNoProgress_recyclerViewItem0_checkOngoingTopicsCount_countIsZero() { // ktlint-disable max-line-length
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("0"))
      onView(
        atPositionOnView(R.id.profile_progress_list, 0, R.id.ongoing_topics_count)
      ).check(
        matches(withText("0"))
      )
    }
  }

  @Test
  fun testProfileProgressFragmentWithProgress_recyclerViewItem0_checkOngoingTopicsCount_countIsTwo() { // ktlint-disable max-line-length */
    storyProgressTestHelper.markPartialTopicProgressForFractions(
      profileId,
      timestampOlderThanAWeek = false
    )
    storyProgressTestHelper.markTwoPartialStoryProgressForRatios(
      profileId,
      timestampOlderThanAWeek = false
    )
    testCoroutineDispatchers.runCurrent()
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("2"))
      onView(
        atPositionOnView(R.id.profile_progress_list, 0, R.id.ongoing_topics_count)
      ).check(
        matches(withText("2"))
      )
    }
  }

  @Test
  fun testProfileProgressFragmentWithProgress_change_configuration_recyclerViewItem0_checkOngoingTopicsCount_countIsTwo() { // ktlint-disable max-line-length
    storyProgressTestHelper.markPartialTopicProgressForFractions(
      profileId,
      timestampOlderThanAWeek = false
    )
    storyProgressTestHelper.markTwoPartialStoryProgressForRatios(
      profileId,
      timestampOlderThanAWeek = false
    )
    testCoroutineDispatchers.runCurrent()
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      onView(isRoot()).perform(orientationLandscape())
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("2"))
      onView(
        atPositionOnView(R.id.profile_progress_list, 0, R.id.ongoing_topics_count)
      ).check(
        matches(withText("2"))
      )
    }
  }

  @Test
  fun testProfileProgressFragmentNoProgress_recyclerViewItem0_checkOngoingTopicsString_descriptionIsCorrect() { // ktlint-disable max-line-length
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText(R.string.topics_in_progress))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0, R.id.ongoing_topics_description_text_view
        )
      ).check(
        matches(withText(R.string.topics_in_progress))
      )
    }
  }

  @Test
  fun testProfileProgressFragmentWithProgress_recyclerViewItem0_checkOngoingTopicsString_descriptionIsCorrect() { // ktlint-disable max-line-length
    storyProgressTestHelper.markPartialTopicProgressForFractions(
      profileId,
      timestampOlderThanAWeek = false
    )
    storyProgressTestHelper.markTwoPartialStoryProgressForRatios(
      profileId,
      timestampOlderThanAWeek = false
    )
    testCoroutineDispatchers.runCurrent()
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText(R.string.topics_in_progress))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0, R.id.ongoing_topics_description_text_view
        )
      ).check(
        matches(withText(R.string.topics_in_progress))
      )
    }
  }

  @Test
  fun testProfileProgressFragmentWithProgress_changeConfiguration_recyclerViewItem0_checkOngoingTopicsString_descriptionIsCorrect() { // ktlint-disable max-line-length
    storyProgressTestHelper.markPartialTopicProgressForFractions(
      profileId,
      timestampOlderThanAWeek = false
    )
    storyProgressTestHelper.markTwoPartialStoryProgressForRatios(
      profileId,
      timestampOlderThanAWeek = false
    )
    testCoroutineDispatchers.runCurrent()
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      onView(isRoot()).perform(orientationLandscape())
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText(R.string.topics_in_progress))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0, R.id.ongoing_topics_description_text_view
        )
      ).check(
        matches(withText(R.string.topics_in_progress))
      )
    }
  }

  @Test
  fun testProfileProgressFragmentNoProgress_recyclerViewItem0_checkCompletedStoriesCount_countIsZero() { // ktlint-disable max-line-length
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("0"))
      onView(
        atPositionOnView(R.id.profile_progress_list, 0, R.id.completed_stories_count)
      ).check(
        matches(withText("0"))
      )
    }
  }

  @Test
  fun testProfileProgressFragmentWithProgress_recyclerViewItem0_checkCompletedStoriesCount_countIsTwo() { // ktlint-disable max-line-length
    storyProgressTestHelper.markFullStoryPartialTopicProgressForRatios(
      profileId,
      timestampOlderThanAWeek = false
    )
    storyProgressTestHelper.markFullStoryProgressForFractions(
      profileId,
      timestampOlderThanAWeek = false
    )
    testCoroutineDispatchers.runCurrent()
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("2"))
      onView(
        atPositionOnView(R.id.profile_progress_list, 0, R.id.completed_stories_count)
      ).check(
        matches(withText("2"))
      )
    }
  }

  @Test
  fun testProfileProgressFragmentNoProgress_recyclerViewItem0_checkCompletedStoriesString_descriptionIsCorrect() { // ktlint-disable max-line-length
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText(R.string.stories_completed))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0,
          R.id.completed_stories_description_text_view
        )
      ).check(
        matches(withText(R.string.stories_completed))
      )
    }
  }

  @Test
  fun testProfileProgressFragmentWithProgress_recyclerViewItem0_checkCompletedStoriesString_descriptionIsCorrect() { // ktlint-disable max-line-length
    storyProgressTestHelper.markFullStoryPartialTopicProgressForRatios(
      profileId,
      timestampOlderThanAWeek = false
    )
    storyProgressTestHelper.markFullStoryProgressForFractions(
      profileId,
      timestampOlderThanAWeek = false
    )
    testCoroutineDispatchers.runCurrent()
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText(R.string.stories_completed))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0,
          R.id.completed_stories_description_text_view
        )
      ).check(
        matches(withText(R.string.stories_completed))
      )
    }
  }

  @Test
  fun testProfileProgressActivity_changeConfiguration_recyclerViewItem1_storyNameIsCorrect() {
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      onView(isRoot()).perform(orientationLandscape())
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.profile_progress_list))
        .perform(scrollToPosition<RecyclerView.ViewHolder>(1))
      waitForTheView(withText("First Story"))
      onView(
        atPositionOnView(R.id.profile_progress_list, 1, R.id.story_name_text_view)
      ).check(
        matches(withText(containsString("First Story")))
      )
    }
  }

  @Test
  fun testProfileProgressActivity_recyclerViewItem1_storyNameIsCorrect() {
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(0)).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.profile_progress_list)).perform(
        scrollToPosition<RecyclerView.ViewHolder>(
          1
        )
      )
      waitForTheView(withText("First Story"))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          1, R.id.story_name_text_view
        )
      ).check(
        matches(withText(containsString("First Story")))
      )
    }
  }

  @Test
  fun testProfileProgressActivity_recyclerViewItem1_topicNameIsCorrect() {
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.profile_progress_list)).perform(
        scrollToPosition<RecyclerView.ViewHolder>(
          1
        )
      )
      waitForTheView(withText("FIRST TEST TOPIC"))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          1, R.id.topic_name_text_view
        )
      ).check(
        matches(withText(containsString("FIRST TEST TOPIC")))
      )
    }
  }

  @Test
  fun testProfileProgressActivity_clickRecyclerViewItem1_intentIsCorrect() {
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.profile_progress_list)).perform(
        scrollToPosition<RecyclerView.ViewHolder>(
          1
        )
      )
      waitForTheView(withText("FIRST TEST TOPIC"))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          1, R.id.topic_name_text_view
        )
      ).perform(click())
      testCoroutineDispatchers.runCurrent()
      intended(hasComponent(TopicActivity::class.java.name))
      intended(hasExtra(TopicActivity.getProfileIdKey(), internalProfileId))
      intended(hasExtra(TopicActivity.getTopicIdKey(), TEST_TOPIC_ID_0))
      intended(hasExtra(TopicActivity.getStoryIdKey(), TEST_STORY_ID_0))
    }
  }

  @Test
  fun testProfileProgressActivity_recyclerViewIndex0_clickViewAll_opensRecentlyPlayedActivity() {
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText("Admin"))
      onView(atPositionOnView(R.id.profile_progress_list, 0, R.id.view_all_text_view))
        .check(
          matches(withText("View All"))
        )
        .perform(click())
      testCoroutineDispatchers.runCurrent()
      intended(hasComponent(RecentlyPlayedActivity::class.java.name))
      intended(
        hasExtra(
          RecentlyPlayedActivity.RECENTLY_PLAYED_ACTIVITY_INTERNAL_PROFILE_ID_KEY,
          internalProfileId
        )
      )
    }
  }

  @Test
  fun testProfileProgressActivityNoProgress_recyclerViewIndex0_clickTopicCount_isNotClickable() {
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText(R.string.topics_in_progress))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0, R.id.ongoing_topics_container
        )
      ).check(
        matches(not(isClickable()))
      )
    }
  }

  @Test
  fun testProfileProgressActivityNoProgress_recyclerViewIndex0_clickStoryCount_isNotClickable() {
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText(R.string.stories_completed))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0, R.id.completed_stories_container
        )
      ).check(
        matches(not(isClickable()))
      )
    }
  }

  @Test
  fun testProfileProgressActivityNoProgress_recyclerViewIndex0_changeConfiguration_clickStoryCount_isNotClickable() { // ktlint-disable max-line-length
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      onView(isRoot()).perform(orientationLandscape())
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText(R.string.stories_completed))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0, R.id.completed_stories_container
        )
      ).check(
        matches(not(isClickable()))
      )
    }
  }

  @Test
  fun testProfileProgressActivityWithProgress_recyclerViewIndex0_clickTopicCount_opensOngoingTopicListActivity() { // ktlint-disable max-line-length
    storyProgressTestHelper.markPartialTopicProgressForFractions(
      profileId,
      timestampOlderThanAWeek = false
    )
    storyProgressTestHelper.markTwoPartialStoryProgressForRatios(
      profileId,
      timestampOlderThanAWeek = false
    )
    testCoroutineDispatchers.runCurrent()
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText(R.string.topics_in_progress))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0,
          R.id.ongoing_topics_container
        )
      ).perform(click())
      testCoroutineDispatchers.runCurrent()
      intended(hasComponent(OngoingTopicListActivity::class.java.name))
      intended(
        hasExtra(
          OngoingTopicListActivity.ONGOING_TOPIC_LIST_ACTIVITY_PROFILE_ID_KEY,
          internalProfileId
        )
      )
    }
  }

  @Test
  fun testProfileProgressActivityWithProgress_recyclerViewIndex0_clickStoryCount_opensCompletedStoryListActivity() { // ktlint-disable max-line-length
    storyProgressTestHelper.markFullStoryPartialTopicProgressForRatios(
      profileId,
      timestampOlderThanAWeek = false
    )
    storyProgressTestHelper.markFullStoryProgressForFractions(
      profileId,
      timestampOlderThanAWeek = false
    )
    testCoroutineDispatchers.runCurrent()
    launch<ProfileProgressActivity>(createProfileProgressActivityIntent(internalProfileId)).use {
      testCoroutineDispatchers.runCurrent()
      waitForTheView(withText(R.string.stories_completed))
      onView(
        atPositionOnView(
          R.id.profile_progress_list,
          0,
          R.id.completed_stories_container
        )
      ).perform(click())
      testCoroutineDispatchers.runCurrent()
      intended(hasComponent(CompletedStoryListActivity::class.java.name))
      intended(
        hasExtra(
          CompletedStoryListActivity.COMPLETED_STORY_LIST_ACTIVITY_PROFILE_ID_KEY,
          internalProfileId
        )
      )
    }
  }

  private fun createGalleryPickActivityResultStub(): Instrumentation.ActivityResult {
    val resources: Resources = context.resources
    val imageUri = Uri.parse(
      ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
        resources.getResourcePackageName(R.mipmap.ic_launcher) + '/' +
        resources.getResourceTypeName(R.mipmap.ic_launcher) + '/' +
        resources.getResourceEntryName(R.mipmap.ic_launcher)
    )
    val resultIntent = Intent()
    resultIntent.data = imageUri
    return Instrumentation.ActivityResult(RESULT_OK, resultIntent)
  }

  private fun waitForTheView(viewMatcher: Matcher<View>): ViewInteraction {
    return onView(isRoot()).perform(waitForMatch(viewMatcher, 30000))
  }

  // TODO(#59): Remove these waits once we can ensure that the production executors are not depended on in tests.
  //  Sleeping is really bad practice in Espresso tests, and can lead to test flakiness. It shouldn't be necessary if we
  //  use a test executor service with a counting idle resource, but right now Gradle mixes dependencies such that both
  //  the test and production blocking executors are being used. The latter cannot be updated to notify Espresso of any
  //  active coroutines, so the test attempts to assert state before it's ready. This artificial delay in the Espresso
  //  thread helps to counter that.
  /**
   * Perform action of waiting for a specific matcher to finish. Adapted from:
   * https://stackoverflow.com/a/22563297/3689782.
   */
  private fun waitForMatch(viewMatcher: Matcher<View>, millis: Long): ViewAction {
    return object : ViewAction {
      override fun getDescription(): String {
        return "wait for a specific view with matcher <$viewMatcher> during $millis millis."
      }

      override fun getConstraints(): Matcher<View> {
        return isRoot()
      }

      override fun perform(uiController: UiController?, view: View?) {
        checkNotNull(uiController)
        uiController.loopMainThreadUntilIdle()
        val startTime = System.currentTimeMillis()
        val endTime = startTime + millis

        do {
          if (TreeIterables.breadthFirstViewTraversal(view).any { viewMatcher.matches(it) }) {
            return
          }
          uiController.loopMainThreadForAtLeast(50)
        } while (System.currentTimeMillis() < endTime)

        // Couldn't match in time.
        throw PerformException.Builder()
          .withActionDescription(description)
          .withViewDescription(HumanReadables.describe(view))
          .withCause(TimeoutException())
          .build()
      }
    }
  }

  @Module
  class TestModule {
    // TODO(#59): Either isolate these to their own shared test module, or use the real logging
    // module in tests to avoid needing to specify these settings for tests.
    @EnableConsoleLog
    @Provides
    fun provideEnableConsoleLog(): Boolean = true

    @EnableFileLog
    @Provides
    fun provideEnableFileLog(): Boolean = false

    @GlobalLogLevel
    @Provides
    fun provideGlobalLogLevel(): LogLevel = LogLevel.VERBOSE
  }

  private fun setUpTestApplicationComponent() {
    ApplicationProvider.getApplicationContext<TestApplication>().inject(this)
  }

  // TODO(#59): Figure out a way to reuse modules instead of needing to re-declare them.
  // TODO(#1675): Add NetworkModule once data module is migrated off of Moshi.
  @Singleton
  @Component(
    modules = [
      TestModule::class, TestDispatcherModule::class, ApplicationModule::class,
      ContinueModule::class, FractionInputModule::class, ItemSelectionInputModule::class,
      MultipleChoiceInputModule::class, NumberWithUnitsRuleModule::class,
      NumericInputRuleModule::class, TextInputRuleModule::class, DragDropSortInputModule::class,
      ImageClickInputModule::class, InteractionsModule::class, GcsResourceModule::class,
      GlideImageLoaderModule::class, ImageParsingModule::class, HtmlParserEntityTypeModule::class,
      QuestionModule::class, TestLogReportingModule::class, TestAccessibilityModule::class,
      LogStorageModule::class, CachingTestModule::class, PrimeTopicAssetsControllerModule::class,
      ExpirationMetaDataRetrieverModule::class, ViewBindingShimModule::class,
      RatioInputModule::class, ApplicationStartupListenerModule::class,
      LogUploadWorkerModule::class, WorkManagerConfigurationModule::class,
      HintsAndSolutionConfigModule::class, FirebaseLogUploaderModule::class
    ]
  )
  interface TestApplicationComponent : ApplicationComponent, ApplicationInjector {
    @Component.Builder
    interface Builder : ApplicationComponent.Builder

    fun inject(profileProgressFragmentTest: ProfileProgressFragmentTest)
  }

  class TestApplication : Application(), ActivityComponentFactory, ApplicationInjectorProvider {
    private val component: TestApplicationComponent by lazy {
      DaggerProfileProgressFragmentTest_TestApplicationComponent.builder()
        .setApplication(this)
        .build() as TestApplicationComponent
    }

    fun inject(profileProgressFragmentTest: ProfileProgressFragmentTest) {
      component.inject(profileProgressFragmentTest)
    }

    override fun createActivityComponent(activity: AppCompatActivity): ActivityComponent {
      return component.getActivityComponentBuilderProvider().get().setActivity(activity).build()
    }

    override fun getApplicationInjector(): ApplicationInjector = component
  }
}
