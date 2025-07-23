/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.meta.loader;

import com.axelor.common.FileUtils;
import com.axelor.data.csv.CSVImporter;
import com.axelor.data.xml.XMLImporter;
import com.axelor.meta.MetaScanner;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.LineReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Singleton
class DataLoader extends AbstractLoader {

  private static final Logger LOG = LoggerFactory.getLogger(DataLoader.class);

  private static final String DATA_DIR_NAME = "data-init";

  private static final String INPUT_DIR_NAME = "input";
  private static final String INPUT_CONFIG_NAME = "input-config.xml";

  private static Pattern patCsv = Pattern.compile("^\\<\\s*csv-inputs");
  private static Pattern patXml = Pattern.compile("^\\<\\s*xml-inputs");

  @Override
  protected void doLoad(Module module, boolean update) {

    List<File> directoriosTemporales = sortDataInitByPriority(extract(module));

    for (File tmp : directoriosTemporales) {

      try {
        File config = FileUtils.getFile(tmp, getDirName(), INPUT_CONFIG_NAME);
        if (isConfig(config, patCsv)) {
          importCsv(config);
        } else if (isConfig(config, patXml)) {
          importXml(config);
        }
      } catch (IOException e) {
        LOG.error(e.getMessage(), e);
        throw new RuntimeException(e);
      } finally {
        clean(tmp);
      }

    }
  }

  private void importCsv(File config) {
    File data = FileUtils.getFile(config.getParentFile(), INPUT_DIR_NAME);
    CSVImporter importer = new CSVImporter(config.getAbsolutePath(), data.getAbsolutePath(), null);
    importer.run();
  }

  private void importXml(File config) throws IOException {
    File data = FileUtils.getFile(config.getParentFile(), INPUT_DIR_NAME);
    XMLImporter importer = new XMLImporter(config.getAbsolutePath(), data.getAbsolutePath());
    importer.run();
  }

  private boolean isConfig(File file, Pattern pattern) {
    try {
      Reader reader = new FileReader(file);
      LineReader lines = new LineReader(reader);
      String line = null;
      while ((line = lines.readLine()) != null) {
        if (pattern.matcher(line).find()) {
          return true;
        }
      }
      reader.close();
    } catch (IOException e) {
      LOG.error(e.getMessage(), e);
    }
    return false;
  }

  protected String getDirName() {
    return DATA_DIR_NAME;
  }

  private List<File> extract(Module module) {
    try {
      final String dirName = this.getDirName();

      final List<URL> allDirectories = MetaScanner.findAll(module.getName(), dirName, "(.+?)");
      Set<String> directorios = new HashSet<>();
      for (URL file : allDirectories) {
          String name = (new URI(file.toExternalForm())).normalize().toURL().toExternalForm();
          name = name.substring(0,name.lastIndexOf(dirName)+ dirName.length());
          directorios.add(name);
      }

      final List<File> directoriosTemporales = new ArrayList<>();

      for (String directorio : directorios) {
        final List<URL> files = MetaScanner.findAll(module.getName(), dirName, "(.+?)");

        final File tmp = Files.createTempDir();
        directoriosTemporales.add(tmp);

        for (URL file : files) {
          String fileNormalized = (new URI(file.toExternalForm())).normalize().toURL().toExternalForm();

          if (fileNormalized.startsWith(directorio)) {


            String name = file.toString();
            name = name.substring(name.lastIndexOf(dirName));


            try (final InputStream is = file.openStream()) {
              copy(is, tmp, name);
            } catch (IOException e) {
              LOG.error(e.getMessage(), e);
              throw new RuntimeException(e);
            }
          }
        }
      }

      return directoriosTemporales;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void copy(InputStream in, File toDir, String name) throws IOException {
    File dst = FileUtils.getFile(toDir, name);
    Files.createParentDirs(dst);
    OutputStream out = new FileOutputStream(dst);
    try {
      ByteStreams.copy(in, out);
    } finally {
      out.close();
    }
  }

  private void clean(File file) {
    if (file.isDirectory()) {
      for (File child : file.listFiles()) {
        clean(child);
      }
      file.delete();
    } else if (file.exists()) {
      file.delete();
    }
  }

    /**
     * Sorts directories based on the presence of XML and CSV configurations.
     * XML files are prioritized over CSV files, and within XML files, they are sorted by attribute priority.
     *
     * @param directorios List of directories to sort
     * @return Sorted list of directories
     */
  public List<File> sortDataInitByPriority(List<File> directorios) {
    return directorios.stream()
            .map(dir -> {
              File config = FileUtils.getFile(dir, getDirName(), INPUT_CONFIG_NAME);
              boolean isCsv = isConfig(config, patCsv);
              boolean isXml = isConfig(config, patXml);
              int priority = isXml ? getPriority(config) : -1;
              return new DataInitDirectoryDefinition(dir, isXml, isCsv, priority);
            })
            .sorted((dataInitDirectoryDefinition1, dataInitDirectoryDefinition2) -> {
              // 1. XML antes que CSV
              if (dataInitDirectoryDefinition1.isXml && !dataInitDirectoryDefinition2.isXml) return -1;
              if (!dataInitDirectoryDefinition1.isXml && dataInitDirectoryDefinition2.isXml) return 1;

              // 2. Entre XML, ordenar por priority descendente
              if (dataInitDirectoryDefinition1.isXml && dataInitDirectoryDefinition2.isXml) {
                return Integer.compare(dataInitDirectoryDefinition2.priority, dataInitDirectoryDefinition1.priority);
              }

              // 3. Ambos CSV o ambos no válidos, orden indefinido
              return 0;
            })
            .map(dataInitDirectoryDefinition -> dataInitDirectoryDefinition.dir)
            .collect(Collectors.toList());
  }

  public static int getPriority(File xmlFile) {
    try {
      System.out.println("Calculado prioridad de :" + xmlFile.getAbsolutePath());
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
      Element root = doc.getDocumentElement();
      String priorityAttr = root.getAttribute("priority");
      if (priorityAttr != null && !priorityAttr.trim().isEmpty()) {
        return Integer.parseInt(priorityAttr);
      } else {
        return 0;
      }
    } catch (Exception e) {
      System.out.println("Fallo calcular la prioridad de :" + xmlFile.getAbsolutePath());
      e.printStackTrace();
      return 0;
    }

  }

  private static class DataInitDirectoryDefinition {
    File dir;
    boolean isXml;
    boolean isCsv;
    int priority;

    DataInitDirectoryDefinition(File dir, boolean isXml, boolean isCsv, int priority) {
      this.dir = dir;
      this.isXml = isXml;
      this.isCsv = isCsv;
      this.priority = priority;
    }
  }
}


