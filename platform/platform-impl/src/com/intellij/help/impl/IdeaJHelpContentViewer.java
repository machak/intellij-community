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
package com.intellij.help.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.help.JHelpContentViewer;
import javax.help.TextHelpModel;
import javax.swing.*;
import java.awt.*;

/**
 * It a dirty patch! Help system is so ugly that it hangs when it open some "external" links.
 * To prevent this we open "external" links in nornal WEB browser.
 *
 * @author Vladimir Kondratyev
 */
class IdeaJHelpContentViewer extends JHelpContentViewer{
  /**
   * Creates a JHelp with an specific TextHelpModel as its data model.
   *
   * @param model The TextHelpModel. A null model is valid.
   */
  public IdeaJHelpContentViewer(TextHelpModel model){
    super(model);
  }

  /**
   * PATCHED VERSION OF SUPER METHDO.
   * Replaces the UI with the latest version from the default
   * UIFactory.
   */
  public void updateUI(){
    setUI(new IdeaHelpContentViewUI(this));
    invalidate();
  }

  @Override
  public void paint(Graphics g) {
    final JEditorPane editorPane = UIUtil.findComponentOfType(this, JEditorPane.class);
    if (editorPane != null) {
      editorPane.putClientProperty(SwingUtilities2.AA_TEXT_PROPERTY_KEY, SwingUtilities2.AATextInfo.getAATextInfo(true));
    }
    UISettings.setupAntialiasing(g);
    super.paint(g);
  }
}