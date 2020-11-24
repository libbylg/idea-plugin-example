/*
 * Copyright 2020 Nazmul Idris. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package actions

import TestFile
import TestUtils.Companion.computeBasePath
import actions.EditorReplaceLink.RunningState.*
import color_console_log.ColorConsoleContext.Companion.colorConsole
import color_console_log.Colors.Blue
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import printDebugHeader
import shortSleep
import urlshortenservice.ShortenUrlService
import java.awt.datatransfer.DataFlavor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val mockShortenUrlService = object : ShortenUrlService {
  override fun shorten(longUrl: String) = "http://shorturl.com"
}

class EditorReplaceLinkTest : BasePlatformTestCase() {

  @Before
  public override fun setUp() {
    super.setUp()
    assertThat(testDataPath).isNotNull
  }

  override fun getTestDataPath(): String = computeBasePath

  @Test
  fun testUnderlyingFunctionUsedByTestEditorReplaceLinkAction() {
    colorConsole {
      printDebugHeader()
    }

    val psiFile = myFixture.configureByFile(TestFile.Input(getTestName(false)))
    val project = myFixture.project
    val editor = myFixture.editor

    val action = EditorReplaceLink(mockShortenUrlService)
    val result = action.doWorkInBackground(editor, psiFile, project)
    assertThat(result).isTrue

    val presentation = myFixture.testAction(action)
    assertThat(presentation.isEnabledAndVisible).isTrue

    myFixture.checkResultByFile(TestFile.Output(getTestName(false)))
  }

  /**
   * [Further reading on Future and Executor](https://www.callicoder.com/java-callable-and-future-tutorial/)
   */
  @Test
  fun testTheActionByConnectingWithTinyUrlServiceLive() {
    colorConsole {
      printDebugHeader()
    }

    myFixture.configureByFile(TestFile.Input(getTestName(false)))

    val action = EditorReplaceLink()

    val executor = Executors.newSingleThreadExecutor()
    val future = executor.submit {
      while (true) {
        colorConsole {
          printLine {
            span(Blue, "executor: isRunning: ${action.isRunning()}, isCancelled: ${action.isCanceled()}")
          }
        }
        if (action.isRunning() == NOT_STARTED) {
          shortSleep()
          continue
        }
        if (action.isRunning() == IS_CANCELLED || action.isRunning() == HAS_STOPPED) {
          executor.shutdown()
          break
        }
        else shortSleep()
      }
    }

    val presentation = myFixture.testAction(action)
    assertThat(presentation.isEnabledAndVisible).isTrue()

    val textInClipboard = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
    assertThat(textInClipboard).isEqualTo("https://tinyurl.com/mbq3m")

    myFixture.checkResultByFile(TestFile.Output(getTestName(false)))

    colorConsole {
      printLine {
        span(Blue, "executor: future.isDone: ${future.isDone}")
      }
    }
    executor.awaitTermination(30, TimeUnit.SECONDS)

    colorConsole {
      printLine {
        span(Blue, "executor: future.isDone: ${future.isDone}")
      }
    }
    executor.shutdown()
  }

  @Test
  fun testEditorReplaceLink() {
    colorConsole {
      printDebugHeader()
    }

    myFixture.configureByFile(TestFile.Input(getTestName(false)))

    val action = EditorReplaceLink(mockShortenUrlService)

    val presentation = myFixture.testAction(action)
    assertThat(presentation.isEnabledAndVisible).isTrue()

    val textInClipboard = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
    assertThat(textInClipboard).isSameAs(mockShortenUrlService.shorten())

    myFixture.checkResultByFile(TestFile.Output(getTestName(false)))
  }
}