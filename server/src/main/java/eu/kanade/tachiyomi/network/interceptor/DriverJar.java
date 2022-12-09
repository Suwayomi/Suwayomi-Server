/*
 * Copyright (c) Microsoft Corporation.
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

package eu.kanade.tachiyomi.network.interceptor;

import com.microsoft.playwright.impl.driver.Driver;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/*
  exact copy of https://github.com/microsoft/playwright-java/blob/4d278c391e3c50738ddea6c3e324a4bbbf719d86/driver-bundle/src/main/java/com/microsoft/playwright/impl/driver/jar/DriverJar.java
  with diff:
  108a109,116
  >
  >   private FileSystem initFileSystem(URI uri) throws IOException {
  >     try {
  >       return FileSystems.newFileSystem(uri, Collections.emptyMap());
  >     } catch (FileSystemAlreadyExistsException e) {
  >       return null;
  >     }
  >   }
  116c124
  <     try (FileSystem fileSystem = "jar".equals(uri.getScheme()) ? FileSystems.newFileSystem(uri, Collections.emptyMap()) : null) {
  ---
  >     try (FileSystem fileSystem = "jar".equals(uri.getScheme()) ? initFileSystem(uri) : null) {
 */
public class DriverJar extends Driver {
  private static final String PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD";
  private static final String SELENIUM_REMOTE_URL = "SELENIUM_REMOTE_URL";
  static final String PLAYWRIGHT_NODEJS_PATH = "PLAYWRIGHT_NODEJS_PATH";
  private final Path driverTempDir;
  private Path preinstalledNodePath;

  public DriverJar() throws IOException {
    // Allow specifying custom path for the driver installation
    // See https://github.com/microsoft/playwright-java/issues/728
    String alternativeTmpdir = System.getProperty("playwright.driver.tmpdir");
    String prefix = "playwright-java-";
    driverTempDir = alternativeTmpdir == null
      ? Files.createTempDirectory(prefix)
      : Files.createTempDirectory(Paths.get(alternativeTmpdir), prefix);
    driverTempDir.toFile().deleteOnExit();
    String nodePath = System.getProperty("playwright.nodejs.path");
    if (nodePath != null) {
      preinstalledNodePath = Paths.get(nodePath);
      if (!Files.exists(preinstalledNodePath)) {
        throw new RuntimeException("Invalid Node.js path specified: " + nodePath);
      }
    }
    logMessage("created DriverJar: " + driverTempDir);
  }

  @Override
  protected void initialize(Boolean installBrowsers) throws Exception {
    if (preinstalledNodePath == null && env.containsKey(PLAYWRIGHT_NODEJS_PATH)) {
      preinstalledNodePath = Paths.get(env.get(PLAYWRIGHT_NODEJS_PATH));
      if (!Files.exists(preinstalledNodePath)) {
        throw new RuntimeException("Invalid Node.js path specified: " + preinstalledNodePath);
      }
    } else if (preinstalledNodePath != null) {
      // Pass the env variable to the driver process.
      env.put(PLAYWRIGHT_NODEJS_PATH, preinstalledNodePath.toString());
    }
    extractDriverToTempDir();
    logMessage("extracted driver from jar to " + driverPath());
    if (installBrowsers)
      installBrowsers(env);
  }

  private void installBrowsers(Map<String, String> env) throws IOException, InterruptedException {
    String skip = env.get(PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD);
    if (skip == null) {
      skip = System.getenv(PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD);
    }
    if (skip != null && !"0".equals(skip) && !"false".equals(skip)) {
      System.out.println("Skipping browsers download because `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` env variable is set");
      return;
    }
    if (env.get(SELENIUM_REMOTE_URL) != null || System.getenv(SELENIUM_REMOTE_URL) != null) {
      logMessage("Skipping browsers download because `SELENIUM_REMOTE_URL` env variable is set");
      return;
    }
    Path driver = driverPath();
    if (!Files.exists(driver)) {
      throw new RuntimeException("Failed to find driver: " + driver);
    }
    ProcessBuilder pb = createProcessBuilder();
    pb.command().add("install");
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    Process p = pb.start();
    boolean result = p.waitFor(10, TimeUnit.MINUTES);
    if (!result) {
      p.destroy();
      throw new RuntimeException("Timed out waiting for browsers to install");
    }
    if (p.exitValue() != 0) {
      throw new RuntimeException("Failed to install browsers, exit code: " + p.exitValue());
    }
  }

  private static boolean isExecutable(Path filePath) {
    String name = filePath.getFileName().toString();
    return name.endsWith(".sh") || name.endsWith(".exe") || !name.contains(".");
  }


  private FileSystem initFileSystem(URI uri) throws IOException {
    try {
      return FileSystems.newFileSystem(uri, Collections.emptyMap());
    } catch (FileSystemAlreadyExistsException e) {
      return null;
    }
  }
  void extractDriverToTempDir() throws URISyntaxException, IOException {
    ClassLoader classloader = Thread.currentThread().getContextClassLoader();
    URI originalUri = classloader.getResource(
      "driver/" + platformDir()).toURI();
    URI uri = maybeExtractNestedJar(originalUri);

    // Create zip filesystem if loading from jar.
    try (FileSystem fileSystem = "jar".equals(uri.getScheme()) ? initFileSystem(uri) : null) {
      Path srcRoot = Paths.get(uri);
      // jar file system's .relativize gives wrong results when used with
      // spring-boot-maven-plugin, convert to the default filesystem to
      // have predictable results.
      // See https://github.com/microsoft/playwright-java/issues/306
      Path srcRootDefaultFs = Paths.get(srcRoot.toString());
      Files.walk(srcRoot).forEach(fromPath -> {
        if (preinstalledNodePath != null) {
          String fileName = fromPath.getFileName().toString();
          if ("node.exe".equals(fileName) || "node".equals(fileName)) {
            return;
          }
        }
        Path relative = srcRootDefaultFs.relativize(Paths.get(fromPath.toString()));
        Path toPath = driverTempDir.resolve(relative.toString());
        try {
          if (Files.isDirectory(fromPath)) {
            Files.createDirectories(toPath);
          } else {
            Files.copy(fromPath, toPath);
            if (isExecutable(toPath)) {
              toPath.toFile().setExecutable(true, true);
            }
          }
          toPath.toFile().deleteOnExit();
        } catch (IOException e) {
          throw new RuntimeException("Failed to extract driver from " + uri + ", full uri: " + originalUri, e);
        }
      });
    }
  }

  private URI maybeExtractNestedJar(final URI uri) throws URISyntaxException {
    if (!"jar".equals(uri.getScheme())) {
      return uri;
    }
    final String JAR_URL_SEPARATOR = "!/";
    String[] parts = uri.toString().split("!/");
    if (parts.length != 3) {
      return uri;
    }
    String innerJar = String.join(JAR_URL_SEPARATOR, parts[0], parts[1]);
    URI jarUri = new URI(innerJar);
    try (FileSystem fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap())) {
      Path fromPath = Paths.get(jarUri);
      Path toPath = driverTempDir.resolve(fromPath.getFileName().toString());
      Files.copy(fromPath, toPath);
      toPath.toFile().deleteOnExit();
      return new URI("jar:" + toPath.toUri() + JAR_URL_SEPARATOR + parts[2]);
    } catch (IOException e) {
      throw new RuntimeException("Failed to extract driver's nested .jar from " + jarUri + "; full uri: " + uri, e);
    }
  }

  private static String platformDir() {
    String name = System.getProperty("os.name").toLowerCase();
    String arch = System.getProperty("os.arch").toLowerCase();

    if (name.contains("windows")) {
      return "win32_x64";
    }
    if (name.contains("linux")) {
      if (arch.equals("aarch64")) {
        return "linux-arm64";
      } else {
        return "linux";
      }
    }
    if (name.contains("mac os x")) {
      return "mac";
    }
    throw new RuntimeException("Unexpected os.name value: " + name);
  }

  @Override
  protected Path driverDir() {
    return driverTempDir;
  }
}
