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
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.nio.charset.Charset;
import java.util.Collection;

/**
 * @author cdr
 */
public abstract class EncodingManager extends EncodingRegistry {
  static {
    EncodingRegistry.ourInstanceGetter = new Getter<EncodingRegistry>() {
      @Override
      public EncodingRegistry get() {
        return EncodingManager.getInstance();
      }
    };
  }

  @NonNls public static final String PROP_NATIVE2ASCII_SWITCH = "native2ascii";
  @NonNls public static final String PROP_PROPERTIES_FILES_ENCODING = "propertiesFilesEncoding";

  @NotNull
  public static EncodingManager getInstance() {
    return ServiceManager.getService(EncodingManager.class);
  }

  @NotNull
  public abstract Collection<Charset> getFavorites();

  public abstract void setUseUTFGuessing(VirtualFile virtualFile, boolean useUTFGuessing);

  public abstract boolean isNative2AsciiForPropertiesFiles();

  public abstract void setNative2AsciiForPropertiesFiles(VirtualFile virtualFile, boolean native2Ascii);

  @Nullable
  public String getDefaultCharsetName() {
    Charset charset = getDefaultCharset();
    return charset == null ? null : charset.displayName();
  }

  public void setDefaultCharsetName(String name) {
    throw new UnsupportedOperationException("Not implemented");
  }

  /**
   * @return null for system-default
   */
  @Nullable
  public abstract Charset getDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile);
  public abstract void setDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile, @Nullable Charset charset);

  /**
   * @deprecated use {@link EncodingManager#addPropertyChangeListener(java.beans.PropertyChangeListener, com.intellij.openapi.Disposable)} instead
   */
  public abstract void addPropertyChangeListener(@NotNull PropertyChangeListener listener);
  public abstract void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parentDisposable);

  public abstract void removePropertyChangeListener(@NotNull PropertyChangeListener listener);

  @Nullable
  public abstract Charset getCachedCharsetFromContent(@NotNull Document document);
}
