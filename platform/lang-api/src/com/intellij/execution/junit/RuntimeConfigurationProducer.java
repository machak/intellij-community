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

package com.intellij.execution.junit;

import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public abstract class RuntimeConfigurationProducer implements Comparable, Cloneable {
  public static final ExtensionPointName<RuntimeConfigurationProducer> RUNTIME_CONFIGURATION_PRODUCER = ExtensionPointName.create("com.intellij.configurationProducer"); 

  public static final Comparator<RuntimeConfigurationProducer> COMPARATOR = new ProducerComparator();
  protected static final int PREFERED = -1;
  private final ConfigurationFactory myConfigurationFactory;
  private RunnerAndConfigurationSettings myConfiguration;
  protected boolean isClone;

  public RuntimeConfigurationProducer(final ConfigurationType configurationType) {
    this(configurationType.getConfigurationFactories()[0]);
  }

  protected RuntimeConfigurationProducer(ConfigurationFactory configurationFactory) {
    myConfigurationFactory = configurationFactory;
  }

  public RuntimeConfigurationProducer createProducer(final Location location, final ConfigurationContext context) {
    final RuntimeConfigurationProducer result = clone();
    result.myConfiguration = location != null ? result.createConfigurationByElement(location, context) : null;

    if (result.myConfiguration != null) {
      final PsiElement psiElement = result.getSourceElement();
      final Location<PsiElement> _location = PsiLocation.fromPsiElement(psiElement, location != null ? location.getModule() : null);
      if (_location != null) {
        // replace with existing configuration if any
        final RunManager runManager = RunManager.getInstance(context.getProject());
        final ConfigurationType type = result.myConfiguration.getType();
        final RunnerAndConfigurationSettings[] configurations = runManager.getConfigurationSettings(type);
        final RunnerAndConfigurationSettings configuration = result.findExistingByElement(_location, configurations, context);
        if (configuration != null) {
          result.myConfiguration = configuration;
        }
      }
    }

    return result;
  }

  @Nullable
  public RunnerAndConfigurationSettings findExistingConfiguration(@NotNull Location location, ConfigurationContext context) {
    assert isClone;
    final RunManager runManager = RunManager.getInstance(location.getProject());
    final RunnerAndConfigurationSettings[] configurations = runManager.getConfigurationSettings(getConfigurationType());
    return findExistingByElement(location, configurations, context);
  }

  public abstract PsiElement getSourceElement();

  public RunnerAndConfigurationSettings getConfiguration() {
    assert isClone;
    return myConfiguration;
  }

  @Nullable
  protected abstract RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context);

  @Nullable
  protected RunnerAndConfigurationSettings findExistingByElement(final Location location,
                                                                 @NotNull final RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    assert isClone;
    return null;
  }

  public RuntimeConfigurationProducer clone() {
    assert !isClone;
    try {
      RuntimeConfigurationProducer clone = (RuntimeConfigurationProducer)super.clone();
      clone.isClone = true;
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  protected RunnerAndConfigurationSettings cloneTemplateConfiguration(final Project project, @Nullable final ConfigurationContext context) {
    if (context != null) {
      final RuntimeConfiguration original = context.getOriginalConfiguration(myConfigurationFactory.getType());
      if (original != null) {
        final RunConfiguration c = original instanceof DelegatingRuntimeConfiguration? ((DelegatingRuntimeConfiguration)original).getPeer() : original;
        return RunManager.getInstance(project).createConfiguration(c.clone(), myConfigurationFactory);
      }
    }
    return RunManager.getInstance(project).createRunConfiguration("", myConfigurationFactory);
  }

  protected ConfigurationFactory getConfigurationFactory() {
    return myConfigurationFactory;
  }

  public ConfigurationType getConfigurationType() {
    return myConfigurationFactory.getType();
  }

  public static <T extends RuntimeConfigurationProducer> T getInstance(final Class<T> aClass) {
    final RuntimeConfigurationProducer[] configurationProducers = Extensions.getExtensions(RUNTIME_CONFIGURATION_PRODUCER);
    for (RuntimeConfigurationProducer configurationProducer : configurationProducers) {
      if (configurationProducer.getClass() == aClass) {
        //noinspection unchecked
        return (T) configurationProducer;
      }
    }
    return null;
  }

  private static class ProducerComparator implements Comparator<RuntimeConfigurationProducer> {
    public int compare(final RuntimeConfigurationProducer producer1, final RuntimeConfigurationProducer producer2) {
      final PsiElement psiElement1 = producer1.getSourceElement();
      final PsiElement psiElement2 = producer2.getSourceElement();
      if (doesContain(psiElement1, psiElement2)) return -PREFERED;
      if (doesContain(psiElement2, psiElement1)) return PREFERED;
      return producer1.compareTo(producer2);
    }

    private static boolean doesContain(final PsiElement container, PsiElement element) {
      while ((element = element.getParent()) != null) {
        if (container.equals(element)) return true;
      }
      return false;
    }
  }

  public static class DelegatingRuntimeConfiguration<T extends RunConfigurationBase & LocatableConfiguration>
    extends RuntimeConfiguration {
    private final T myConfig;

    public DelegatingRuntimeConfiguration(T config) {
      super(config.getName(), config.getProject(), config.getFactory());
      myConfig = config;
    }

    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      return myConfig.getConfigurationEditor();
    }

    @SuppressWarnings({"CloneDoesntCallSuperClone"})
    @Override
    public RuntimeConfiguration clone() {
      return new DelegatingRuntimeConfiguration<T>((T)myConfig.clone());
    }

    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
      return myConfig.getState(executor, env);
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
      myConfig.checkConfiguration();
    }

    @Override
    public boolean isGeneratedName() {
      return myConfig.isGeneratedName();
    }

    @Override
    public String suggestedName() {
      return myConfig.suggestedName();
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      myConfig.readExternal(element);
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
      myConfig.writeExternal(element);
    }

    public T getPeer() {
      return myConfig;
    }
  }
}
