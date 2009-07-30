/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Chunk;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.CompilerMessage;
import org.jetbrains.groovy.compiler.rt.GroovycRunner;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyFacet;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author peter
 */
public abstract class GroovyCompilerBase implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.compiler.GroovyCompilerBase");
  private static final HashSet<String> required = new HashSet<String>(Arrays.asList("groovy", "asm", "antlr", "junit", "jline", "ant", "commons"));
  protected final Project myProject;

  public GroovyCompilerBase(Project project) {
    myProject = project;
  }

  protected void runGroovycCompiler(CompileContext compileContext, Set<OutputItem> successfullyCompiled, Set<VirtualFile> toRecompile, final Module module,
                         final List<VirtualFile> toCompile, boolean forStubs, VirtualFile outputDir) {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    assert sdk != null; //verified before
    SdkType sdkType = sdk.getSdkType();
    assert sdkType instanceof JavaSdkType;
    commandLine.setExePath(((JavaSdkType)sdkType).getVMExecutablePath(sdk));

    final PathsList classPathBuilder = new PathsList();

    classPathBuilder.add(PathUtil.getJarPathForClass(GroovycRunner.class));
    classPathBuilder.addAllFiles(GroovyUtils.getFilesInDirectoryByPattern(LibrariesUtil.getGroovyHomePath(module) + "/lib", ".*(groovy|asm|antlr|junit|jline|ant|commons).*\\.jar"));

    final ModuleChunk chunk = createChunk(module, compileContext);
    final List<String> patchers = new SmartList<String>();
    for (final GroovyCompilerExtension extension : GroovyCompilerExtension.EP_NAME.getExtensions()) {
      extension.enhanceCompilerClassPath(chunk, classPathBuilder);
      patchers.addAll(extension.getCompilationUnitPatchers(chunk));
    }

    if ("true".equals(System.getProperty("profile.groovy.compiler"))) {
      commandLine.addParameter("-Djava.library.path=" + PathManager.getBinPath());
      commandLine.addParameter("-Dprofile.groovy.compiler=true");
      commandLine.addParameter("-agentlib:yjpagent=disablej2ee,disablecounts,disablealloc,sessionname=GroovyCompiler");
      classPathBuilder.add(PathManager.getLibPath() + File.separator + "yjp-controller-api-redist.jar");
    }

    commandLine.addParameter("-cp");
    commandLine.addParameter(classPathBuilder.getPathsString());

    commandLine.addParameter("-Xmx" + System.getProperty("groovy.compiler.Xmx", "400m"));
    commandLine.addParameter("-XX:+HeapDumpOnOutOfMemoryError");

    //debug
    //commandLine.addParameter("-Xdebug"); commandLine.addParameter("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");

    // Setting up process encoding according to locale
    final ArrayList<String> list = new ArrayList<String>();
    CompilerUtil.addLocaleOptions(list, false);
    commandLine.addParameters(list);

    commandLine.addParameter(GroovycRunner.class.getName());

    try {
      File fileWithParameters = File.createTempFile("toCompile", "");
      fillFileWithGroovycParameters(module, toCompile, fileWithParameters, compileContext, outputDir, patchers);

      commandLine.addParameter(forStubs ? "stubs" : "groovyc");
      commandLine.addParameter(fileWithParameters.getPath());
    }
    catch (IOException e) {
      LOG.error(e);
    }

    GroovycOSProcessHandler processHandler;

    try {
      processHandler = new GroovycOSProcessHandler(compileContext, commandLine.createProcess(), commandLine.getCommandLineString());

      processHandler.startNotify();
      processHandler.waitFor();

      Set<File> toRecompileFiles = processHandler.getToRecompileFiles();
      for (File toRecompileFile : toRecompileFiles) {
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(toRecompileFile);
        LOG.assertTrue(vFile != null);
        toRecompile.add(vFile);
      }

      for (CompilerMessage compilerMessage : processHandler.getCompilerMessages()) {
        final CompilerMessageCategory category;
        category = getMessageCategory(compilerMessage);

        final String url = compilerMessage.getUrl();

        compileContext.addMessage(category, compilerMessage.getMessage(), VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(url)), compilerMessage.getLineNum(),
                                  compilerMessage.getColumnNum());
      }

      StringBuffer unparsedBuffer = processHandler.getUnparsedOutput();
      if (unparsedBuffer.length() != 0) compileContext.addMessage(CompilerMessageCategory.ERROR, unparsedBuffer.toString(), null, -1, -1);

      successfullyCompiled.addAll(processHandler.getSuccessfullyCompiled());
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }

  private static CompilerMessageCategory getMessageCategory(CompilerMessage compilerMessage) {
    String category;
    category = compilerMessage.getCategory();

    if (CompilerMessage.ERROR.equals(category)) return CompilerMessageCategory.ERROR;
    if (CompilerMessage.INFORMATION.equals(category)) return CompilerMessageCategory.INFORMATION;
    if (CompilerMessage.STATISTICS.equals(category)) return CompilerMessageCategory.STATISTICS;
    if (CompilerMessage.WARNING.equals(category)) return CompilerMessageCategory.WARNING;

    return CompilerMessageCategory.ERROR;
  }

  private void fillFileWithGroovycParameters(Module module, List<VirtualFile> virtualFiles, File f, CompileContext context,
                                             VirtualFile outputDir, final List<String> patchers) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Running groovyc on: " + virtualFiles.toString());
    }

    FileOutputStream stream;
    try {
      stream = new FileOutputStream(f);
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
      return;
    }

    final PrintStream printer = new PrintStream(stream);

    for (final VirtualFile item : virtualFiles) {
      printer.println(GroovycRunner.SRC_FILE);
      printer.println(item.getPath());
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final PsiFile file = PsiManager.getInstance(myProject).findFile(item);
          if (file instanceof GroovyFileBase) {
            for (PsiClass psiClass : ((GroovyFileBase)file).getClasses()) {
              printer.println(psiClass.getQualifiedName());
            }
          }
        }
      });
      printer.println(GroovycRunner.END);
    }

    printer.println(GroovycRunner.CLASSPATH);
    final ModuleChunk chunk = createChunk(module, context);
    printer.println(chunk.getCompilationClasspath() + File.pathSeparator + CompilerPaths.getModuleOutputPath(module, false) + File.pathSeparator + CompilerPaths.getModuleOutputPath(module, true));

    if (!patchers.isEmpty()) {
      printer.println(GroovycRunner.PATCHERS);
      for (final String patcher : patchers) {
        printer.println(patcher);
      }
      printer.println(GroovycRunner.END);
    }

    final Charset ideCharset = EncodingProjectManager.getInstance(myProject).getDefaultCharset();
    if (!Comparing.equal(CharsetToolkit.getDefaultSystemCharset(), ideCharset)) {
      printer.println(GroovycRunner.ENCODING);
      printer.println(ideCharset.name());
    }

    //production output
    printer.println(GroovycRunner.OUTPUTPATH);
    printer.println(PathUtil.getLocalPath(outputDir));
    printer.close();
  }

  private static ModuleChunk createChunk(Module module, CompileContext context) {
    return new ModuleChunk((CompileContextEx)context, new Chunk<Module>(module), Collections.<Module, List<VirtualFile>>emptyMap());
  }

  @Nullable
  public ExitStatus compile(final CompileContext compileContext, final VirtualFile[] virtualFiles) {

    Set<OutputItem> successfullyCompiled = new HashSet<OutputItem>();
    Set<VirtualFile> toRecompileCollector = new HashSet<VirtualFile>();

    Map<Module, List<VirtualFile>> mapModulesToVirtualFiles = CompilerUtil.buildModuleToFilesMap(compileContext, virtualFiles);
    final List<Chunk<Module>> chunks =
      ModuleCompilerUtil.getSortedModuleChunks(myProject, new ArrayList<Module>(mapModulesToVirtualFiles.keySet()));
    for (final Chunk<Module> chunk : chunks) {
      for (final Module module : chunk.getNodes()) {
        final List<VirtualFile> moduleFiles = mapModulesToVirtualFiles.get(module);
        if (moduleFiles == null) {
          continue;
        }

        if (!toRecompileCollector.isEmpty()) {
          toRecompileCollector.addAll(moduleFiles);
          continue;
        }

        final ModuleFileIndex index = ModuleRootManager.getInstance(module).getFileIndex();
        final GroovyFacet facet = GroovyFacet.getInstance(module);
        final List<VirtualFile> toCompile = new ArrayList<VirtualFile>();
        final List<VirtualFile> toCompileTests = new ArrayList<VirtualFile>();
        final List<VirtualFile> toCopy = new ArrayList<VirtualFile>();
        final CompilerConfiguration configuration = CompilerConfiguration.getInstance(myProject);

        if (module.getModuleType() instanceof JavaModuleType) {
          final boolean compileGroovyFiles = facet != null && facet.getConfiguration().isCompileGroovyFiles();
          for (final VirtualFile file : moduleFiles) {
            final boolean shouldCompile = module.getModuleType() instanceof JavaModuleType &&
                                          (file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE && compileGroovyFiles ||
                                           file.getFileType() == StdFileTypes.JAVA);
            (shouldCompile ? (index.isInTestSourceContent(file) ? toCompileTests : toCompile) : toCopy).add(file);
          }
        }

        if (!toCompile.isEmpty()) {
          compileFiles(compileContext, successfullyCompiled, toRecompileCollector, module, toCompile, compileContext.getModuleOutputDirectory(module));
        }
        if (!toCompileTests.isEmpty()) {
          compileFiles(compileContext, successfullyCompiled, toRecompileCollector, module, toCompileTests, compileContext.getModuleOutputDirectoryForTests(module));
        }

        if (!toCopy.isEmpty()) {
          copyFiles(compileContext, successfullyCompiled, toRecompileCollector, toCopy, configuration);
        }

      }
    }


    final Set<OutputItem> compiledItems = successfullyCompiled;
    final VirtualFile[] toRecompile = toRecompileCollector.toArray(new VirtualFile[toRecompileCollector.size()]);
    return new ExitStatus() {
      private final OutputItem[] myCompiledItems = compiledItems.toArray(new OutputItem[compiledItems.size()]);
      private final VirtualFile[] myToRecompile = toRecompile;

      public OutputItem[] getSuccessfullyCompiled() {
        return myCompiledItems;
      }

      public VirtualFile[] getFilesToRecompile() {
        return myToRecompile;
      }
    };
  }

  protected abstract void copyFiles(CompileContext compileContext, Set<OutputItem> successfullyCompiled, Set<VirtualFile> toRecompileCollector, List<VirtualFile> toCopy,
                         CompilerConfiguration configuration);

  protected abstract void compileFiles(CompileContext compileContext, Set<OutputItem> successfullyCompiled, Set<VirtualFile> toRecompileCollector,
                                       Module module,
                                       List<VirtualFile> toCompile,
                                       VirtualFile outputDir);

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    final boolean result = GroovyFileType.GROOVY_FILE_TYPE.equals(file.getFileType());
    if (result && LOG.isDebugEnabled()) {
      LOG.debug("compilable file: " + file.getPath());
    }
    return result;
  }
}
