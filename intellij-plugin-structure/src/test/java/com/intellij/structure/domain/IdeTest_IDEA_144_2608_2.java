package com.intellij.structure.domain;

import com.intellij.structure.resolvers.Resolver;
import com.intellij.structure.utils.TestUtils;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Sergey Patrikeev
 */
public class IdeTest_IDEA_144_2608_2 {


  private static final String IDEA_144_LOAD_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/144.2608.2/ideaIU-144.2608.2.zip";
  private static Ide ide;
  private static Jdk runtime;

  @Before
  public void setUp() throws Exception {
    if (ide != null) return;

    File fileForDownload = TestUtils.getFileForDownload("Idea144.2608.2.zip");
    TestUtils.downloadFile(IDEA_144_LOAD_URL, fileForDownload);


    File ideDir = new File(TestUtils.getTempRoot(), "Idea144.2608.2");
    if (!ideDir.isDirectory()) {
      //noinspection ResultOfMethodCallIgnored
      ideDir.mkdirs();

      ZipUnArchiver archiver = new ZipUnArchiver(fileForDownload);
      archiver.enableLogging(new ConsoleLogger(Logger.LEVEL_WARN, ""));
      archiver.setDestDirectory(ideDir);
      archiver.extract();
    }

    String jdkPath = getJdkPath();


    runtime = Jdk.createJdk(new File(jdkPath));
    ide = IdeManager.getInstance().createIde(ideDir);
  }

  @NotNull
  private String getJdkPath() {
    String jdkPath = System.getenv("JAVA_HOME");
    if (jdkPath == null) {
      jdkPath = "/usr/lib/jvm/java-6-oracle";
    }
    return jdkPath;
  }

  @Test
  public void testRuntime() throws Exception {
    Resolver resolver = runtime.getResolver();
    assertTrue(!resolver.getAllClasses().isEmpty());
  }

  @Test
  public void getVersion() throws Exception {
    assertEquals("IU-144.2608", ide.getVersion().asString());
  }

  @Test
  public void getBundledPlugins() throws Exception {
    //TODO: how to process duplicates plugin (it's without plugin.xml at all)

    assertEquals(125, ide.getBundledPlugins().size());
  }

  @Test
  public void getPluginById() throws Exception {
    List<Plugin> bundledPlugins = ide.getBundledPlugins();
    assertFalse(bundledPlugins.isEmpty());

    for (Plugin bundledPlugin : bundledPlugins) {
      Plugin pluginById = ide.getPluginById(bundledPlugin.getPluginId());
      assertNotNull(pluginById);

    }

  }

  @Test
  public void addCustomPlugin() throws Exception {
    //Download ruby and add as custom plugin to idea
    File rubyPluginFile = TestUtils.downloadPlugin(TestUtils.RUBY_URL, "ruby-plugin.zip");
    Plugin rubyPlugin = PluginManager.getInstance().createPlugin(rubyPluginFile);

    Set<String> definedModules = rubyPlugin.getDefinedModules();
    assertTrue(definedModules.size() == 1);
    String module = definedModules.iterator().next();
    assertEquals("com.intellij.modules.ruby", module);

    assertTrue(ide.getCustomPlugins().isEmpty());

    ide = ide.expandedIde(rubyPlugin);
    assertTrue(!ide.getCustomPlugins().isEmpty());
    assertTrue(ide.getCustomPlugins().get(0) == rubyPlugin);


    Plugin pluginByModule = ide.getPluginByModule(module);
    assertTrue(pluginByModule == rubyPlugin);


  }

  @Test
  public void getResolver() throws Exception {
    Resolver resolver = ide.getResolver();
    Collection<String> allClasses = resolver.getAllClasses();

    for (String aClass : allClasses) {
      ClassNode classFile = resolver.findClass(aClass);
      assertNotNull(classFile);
      assertEquals(aClass, classFile.name);
    }

  }
}