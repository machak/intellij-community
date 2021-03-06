/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.file;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PsiJavaDirectoryFactory extends PsiDirectoryFactory {
  private final PsiManagerImpl myManager;

  public PsiJavaDirectoryFactory(final PsiManagerImpl manager) {
    myManager = manager;
  }

  @Override
  public PsiDirectory createDirectory(@NotNull final VirtualFile file) {
    return new PsiJavaDirectoryImpl(myManager, file);
  }

  @Override
  @NotNull
  public String getQualifiedName(@NotNull final PsiDirectory directory, final boolean presentable) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage != null) {
      final String qualifiedName = aPackage.getQualifiedName();
      if (qualifiedName.length() > 0) return qualifiedName;
      if (presentable) {
        return PsiBundle.message("default.package.presentation") + " (" + directory.getVirtualFile().getPresentableUrl() + ")";
      }
      return "";
    }
    return presentable ? FileUtil.getLocationRelativeToUserHome(directory.getVirtualFile().getPresentableUrl()) : "";
  }

  @Nullable
  @Override
  public PsiDirectoryContainer getDirectoryContainer(@NotNull PsiDirectory directory) {
    return JavaDirectoryService.getInstance().getPackage(directory);
  }

  @Override
  public boolean isPackage(PsiDirectory directory) {
    return ProjectRootManager.getInstance(myManager.getProject()).getFileIndex().getPackageNameByDirectory(directory.getVirtualFile()) != null;
  }

  @Override
  public boolean isValidPackageName(String name) {
    return JavaPsiFacade.getInstance(myManager.getProject()).getNameHelper().isQualifiedName(name);
  }
}
