/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testIntegration;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class GotoTestRelatedProvider extends GotoRelatedProvider {
  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final PsiFile file = LangDataKeys.PSI_FILE.getData(context);
    List<PsiElement> result;
    if (TestFinderHelper.isTest(file)) {
      result = TestFinderHelper.findClassesForTest(file);
    } else {
      result = TestFinderHelper.findTestsForClass(file);
    }
    if (!result.isEmpty()) {
      final List<GotoRelatedItem> items = new ArrayList<GotoRelatedItem>();
      for (PsiElement element : result) {
        items.add(new GotoRelatedItem(element));
      }
      return items;
    }
    return super.getItems(context);
  }
}
