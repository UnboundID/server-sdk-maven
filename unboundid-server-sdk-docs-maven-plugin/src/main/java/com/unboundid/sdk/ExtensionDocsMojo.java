/*
 * Copyright 2016 UnboundID Corp.
 *
 * All Rights Reserved.
 */
package com.unboundid.sdk;

import com.unboundid.directory.sdk.common.internal.UnboundIDExtension;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Goal that generates documentation for an UnboundID Server SDK extension bundle.
 *
 * @author jacobc
 */
@Mojo(name = "build-server-sdk-docs")
@Execute(phase = LifecyclePhase.GENERATE_SOURCES)
public class ExtensionDocsMojo extends AbstractMojo
{
  /**
   * The directory containing extension source code.
   */
  @Parameter(defaultValue = "${project.build.sourceDirectory}")
  private File srcDirectory;

  /**
   * The output directory for generated extension documentation.
   */
  @Parameter(defaultValue = "${project.build.directory}/velocity")
  private File outputDirectory;

  /**
   * The directory to search for Velocity templates.
   */
  @Parameter(defaultValue = "${project.basedir}/src/main/resources/velocity")
  private String velocityResourceLoaderPath;

  /**
   * The Velocity template for the generated documentation index.
   */
  @Parameter(defaultValue = "index.html.vm")
  private String indexTemplate;

  /**
   * The Velocity template for generated extension documentation.
   */
  @Parameter(defaultValue = "extension.html.vm")
  private String extensionTemplate;


  /** {@inheritDoc} */
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException
  {
    getLog().debug(String.format("srcDirectory=%s", srcDirectory.getAbsolutePath()));
    getLog().debug(String.format("outputDirectory=%s", outputDirectory.getAbsolutePath()));
    getLog().debug(String.format("velocityResourceLoaderPath=%s", velocityResourceLoaderPath));
    getLog().debug(String.format("indexTemplate=%s", indexTemplate));
    getLog().debug(String.format("extensionTemplate=%s", extensionTemplate));

    checkDirectory(srcDirectory);
    checkDirectory(outputDirectory);

    getLog().info("Loading UnboundID Server SDK extensions");
    final List<UnboundIDExtension> extensions =
        ExtensionUtils.findExtensions(srcDirectory);
    if (extensions.isEmpty())
    {
      throw new MojoExecutionException(String.format(
          "No UnboundID extensions found in the source directory '%s'",
          srcDirectory.getAbsolutePath()));
    }

    final TreeMap<String, UnboundIDExtension> extensionsMap = new TreeMap<>();
    for (final UnboundIDExtension extension : extensions)
    {
      getLog().debug(String.format(
          "Processing extension '%s'", extension.getClass().getName()));
      final String name = extension.getExtensionName();
      if (name == null)
      {
        throw new MojoExecutionException("Extension name cannot be null");
      }
      if (extensionsMap.containsKey(name))
      {
        throw new MojoExecutionException(String.format(
            "Multiple extensions found with a name of '%s' (%s and %s)",
            name, extensionsMap.get(name).getClass().getName(),
            extension.getClass().getName()));
      }
      extensionsMap.put(name, extension);
    }

    VelocityEngine velocityEngine = new VelocityEngine();
    Properties resourceLoaderPath = new Properties();
    resourceLoaderPath.setProperty("file.resource.loader.path", velocityResourceLoaderPath);
    velocityEngine.init(resourceLoaderPath);

    getLog().info("Processing index template");
    Context indexContext = new VelocityContext();
    indexContext.put("extensions", extensionsMap.values());
    indexContext.put("VelocityUtils", VelocityUtils.class);
    processTemplate(velocityEngine, indexTemplate, "index.html", indexContext);

    for (final UnboundIDExtension  extension : extensionsMap.values())
    {
      getLog().info(String.format("Processing template for extension '%s'",
                                  extension.getExtensionName()));
      Context extensionContext = new VelocityContext();
      extensionContext.put("extension", extension);
      extensionContext.put("VelocityUtils", VelocityUtils.class);
      processTemplate(velocityEngine, extensionTemplate, VelocityUtils.getExtensionFilename(extension), extensionContext);
    }
  }


  private void checkDirectory(File directory) throws MojoExecutionException
  {
    if (!directory.exists())
    {
      if (!directory.mkdirs())
      {
        throw new MojoExecutionException(
            String.format("Failed to create directory '%s'", directory));
      }
    }
  }


  private void processTemplate(VelocityEngine velocityEngine, String template,
                               String outputName, Context context) throws MojoExecutionException
  {
    File outputPath = new File(outputDirectory, outputName);

    Template velocityTemplate = velocityEngine.getTemplate(template);
    FileWriter fileWriter = null;
    try
    {
      fileWriter = new FileWriter(outputPath);
      velocityTemplate.merge(context, fileWriter);
    }
    catch (IOException e)
    {
      throw new MojoExecutionException(String.format(
          "Error creating file '%s'", outputPath.getAbsolutePath()));
    }
    finally
    {
      if (fileWriter != null)
      {
        try
        {
          fileWriter.close();
        }
        catch (IOException e)
        {
          // Oh well
        }
      }
    }
  }
}
