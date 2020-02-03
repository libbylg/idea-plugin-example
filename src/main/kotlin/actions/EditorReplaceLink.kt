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

import Colors.ANSI_GREEN
import actions.EditorBaseAction.mustHaveProjectAndEditor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import printDebugHeader
import printlnAndLog

class EditorReplaceLink : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    printDebugHeader()

    val editor = e.getRequiredData(CommonDataKeys.EDITOR)
    val psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE)
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val progressTitle = "Doing heavy PSI mutation"

    object : Task.Backgroundable(project, progressTitle) {
      override fun run(indicator: ProgressIndicator) =
          doWorkInBackgroundWithWriteLock(editor, psiFile, project, indicator)
    }.queue()
  }

  private fun doWorkInBackgroundWithWriteLock(editor: Editor,
                                              psiFile: PsiFile,
                                              project: Project,
                                              indicator: ProgressIndicator
  ) {
    printDebugHeader()

    // The write command action enables undo.
    WriteCommandAction.runWriteCommandAction(project) {
      if (!psiFile.isValid) return@runWriteCommandAction
      val document = editor.document
      replaceLink(editor, psiFile)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }


  /**
   * This function tries to find the first element which is a link, by walking up the tree starting w/ the element that
   * is currently under the caret.
   *
   * To simplify, something like `PsiUtilCore.getElementType(element) == INLINE_LINK` is evaluated for each element
   * starting from the element under the caret, then visiting its parents, and their parents, etc, until a node of type
   * `INLINE_LINK` is found, actually, a type contained in [MarkdownTokenTypeSets.LINKS].
   *
   * The tree might look something like the following, which is a snippet of this
   * [README.md](https://tinyurl.com/rdowe6q) file).
   *
   * ```
   * MarkdownParagraphImpl(Markdown:PARAGRAPH)(1201,1498)
   *   PsiElement(Markdown:Markdown:TEXT)('The main goal of this plugin is to show')(1201,1240)
   *   PsiElement(Markdown:WHITE_SPACE)(' ')(1240,1241)
   *   ASTWrapperPsiElement(Markdown:Markdown:INLINE_LINK)(1241,1274)  <============[🔥 WE WANT THIS PARENT 🔥]=========
   *     ASTWrapperPsiElement(Markdown:Markdown:LINK_TEXT)(1241,1252)
   *       PsiElement(Markdown:Markdown:[)('[')(1241,1242)
   *       PsiElement(Markdown:Markdown:TEXT)('SonarQube')(1242,1251)  <============[🔥 EDITOR CARET IS HERE 🔥]========
   *       PsiElement(Markdown:Markdown:])(']')(1251,1252)
   *     PsiElement(Markdown:Markdown:()('(')(1252,1253)
   *     MarkdownLinkDestinationImpl(Markdown:Markdown:LINK_DESTINATION)(1253,1273)
   *       PsiElement(Markdown:Markdown:GFM_AUTOLINK)('http://sonarqube.org')(1253,1273)
   *     PsiElement(Markdown:Markdown:))(')')(1273,1274)
   *   PsiElement(Markdown:WHITE_SPACE)(' ')(1274,1275)
   *   PsiElement(Markdown:Markdown:TEXT)('issues directly within your IntelliJ IDE.')(1275,1316)
   *   PsiElement(Markdown:Markdown:EOL)('\n')(1316,1317)
   *   PsiElement(Markdown:Markdown:TEXT)('Currently the plugin is build to work in IntelliJ IDEA,')(1317,1372)
   *   PsiElement(Markdown:WHITE_SPACE)(' ')(1372,1373)
   *   PsiElement(Markdown:Markdown:TEXT)('RubyMine,')(1373,1382)
   *   PsiElement(Markdown:WHITE_SPACE)(' ')(1382,1383)
   *   PsiElement(Markdown:Markdown:TEXT)('WebStorm,')(1383,1392)
   *   PsiElement(Markdown:WHITE_SPACE)(' ')(1392,1393)
   *   PsiElement(Markdown:Markdown:TEXT)('PhpStorm,')(1393,1402)
   *   PsiElement(Markdown:WHITE_SPACE)(' ')(1402,1403)
   *   PsiElement(Markdown:Markdown:TEXT)('PyCharm,')(1403,1411)
   *   PsiElement(Markdown:WHITE_SPACE)(' ')(1411,1412)
   *   PsiElement(Markdown:Markdown:TEXT)('AppCode and Android Studio with any programming ... SonarQube.')(1412,1498)
   * PsiElement(Markdown:Markdown:EOL)('\n')(1498,1499)
   * ```
   */
  private fun replaceLink(editor: Editor, psiFile: PsiFile) {
    printDebugHeader()

    val offset = editor.caretModel.offset
    val element: PsiElement? = psiFile.findElementAt(offset)

    // Find the first parent of the element at the caret, which is a link.
    element?.apply {
      val parentLinkElement = PsiTreeUtil.findFirstParent(element, false) {
        val node = it.node
        node != null && MarkdownTokenTypeSets.LINKS.contains(node.elementType)
      }
      parentLinkElement?.apply {
        ANSI_GREEN("Top level element of type contained in MarkdownTokenTypeSets.LINKS found! 🎉").printlnAndLog()
      }

      "debugger".printlnAndLog()

      // TODO mutate a portion of this element


    }

  }

  /**
   * This is a slightly different implementation of the first part of  [replaceLink] using more utility classes from the
   * platform.
   *
   * @see [MarkdownActionUtil]
   * @see [MarkdownIntroduceLinkReferenceAction.java](https://tinyurl.com/ufw3kll)
   */
  private fun findFirstParentOfElementOfTypeLink(editor: Editor, psiFile: PsiFile): PsiElement? {
    val caret = editor.caretModel.currentCaret
    val elements = MarkdownActionUtil.getElementsUnderCaretOrSelection(psiFile, caret)
    return if (elements != null) MarkdownActionUtil
        .getCommonTopmostParentOfTypes(elements.getFirst(), elements.getSecond(), MarkdownTokenTypeSets.LINKS)
    else null
  }

  override fun update(e: AnActionEvent) = mustHaveProjectAndEditor(e)
}