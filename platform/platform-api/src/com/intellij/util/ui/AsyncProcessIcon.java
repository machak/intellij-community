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

package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

public class AsyncProcessIcon extends AnimatedIcon {
  public static final int COUNT = 12;
  public static final int CYCLE_LENGTH = 800;

  private static final Icon[] SMALL_ICONS = findIcons("/process/step_", "/process/step_mask.png");
  private static final Icon SMALL_PASSIVE_ICON = IconLoader.getIcon("/process/step_passive.png");
  private boolean myUseMask;

  public AsyncProcessIcon(@NonNls String name) {
    this(name, SMALL_ICONS, SMALL_PASSIVE_ICON);
  }

  private AsyncProcessIcon(@NonNls String name, Icon[] icons, Icon passive) {
    super(name);

    init(icons, passive, CYCLE_LENGTH);

    setUseMask(false);
  }

  public AsyncProcessIcon setUseMask(boolean useMask) {
    myUseMask = useMask;
    return this;
  }

  @Override
  protected void paintIcon(Graphics g, Icon icon, int x, int y) {
    if (icon instanceof ProcessIcon) {
      ((ProcessIcon)icon).setLayerEnabled(0, myUseMask);
    }
    super.paintIcon(g, icon, x, y);

    if (icon instanceof ProcessIcon) {
      ((ProcessIcon)icon).setLayerEnabled(0, false);
    }
  }

  private static Icon[] findIcons(String prefix, String maskIconPath) {
    Icon maskIcon = maskIconPath != null ? IconLoader.getIcon(maskIconPath) : null;
    Icon[] icons = new Icon[COUNT];
    for (int i = 0; i <= COUNT - 1; i++) {
      Icon eachIcon = IconLoader.getIcon(prefix + (i + 1) + ".png");
      if (maskIcon != null) {
        icons[i] = new ProcessIcon(maskIcon, eachIcon);
      } else {
        icons[i] = eachIcon;
      }
    }
    return icons;
  }
  
  public void updateLocation(JComponent container) {
    final Rectangle rec = container.getVisibleRect();

    final Dimension iconSize = getPreferredSize();

    final Rectangle newBounds = new Rectangle(rec.x + rec.width - iconSize.width, rec.y, iconSize.width, iconSize.height);
    if (!newBounds.equals(getBounds())) {
      setBounds(newBounds);
      container.repaint();
    }
  }

  private static class ProcessIcon extends LayeredIcon {
    private ProcessIcon(Icon mask, Icon stepIcon) {
      super(mask, stepIcon);
    }
  }

  public static class Big extends AsyncProcessIcon {
    private static final Icon[] BIG_ICONS = findIcons("/process/big/step_", null);
    private static final Icon BIG_PASSIVE_ICON = IconLoader.getIcon("/process/big/step_passive.png");

    public Big(@NonNls final String name) {
      super(name, BIG_ICONS, BIG_PASSIVE_ICON);
    }
  }

  public boolean isDisposed() {
    return myAnimator.isDisposed();
  }
}
