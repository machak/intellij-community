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
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenId;

import java.util.Collections;
import java.util.Set;

public class MavenArtifactCoordinatesVersionConverter extends MavenArtifactCoordinatesConverter {
  @Override
  protected boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
    if (StringUtil.isEmpty(id.getGroupId())
        || StringUtil.isEmpty(id.getArtifactId())
        || StringUtil.isEmpty(id.getVersion())) {
      return false;
    }
    if (isMagicVersion(id)) return true; // todo handle ranges more sensibly
    return manager.hasVersion(id.getGroupId(), id.getArtifactId(), id.getVersion());
  }

  private boolean isMagicVersion(MavenId id) {
    String version = id.getVersion().trim();
    return version.equals("LATEST") || version.equals("RELEASE") || version.startsWith("(") || version.startsWith("[");
  }

  @Override
  protected Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager) {
    if (StringUtil.isEmpty(id.getGroupId()) || StringUtil.isEmpty(id.getArtifactId())) return Collections.emptySet();
    return manager.getVersions(id.getGroupId(), id.getArtifactId());
  }
}