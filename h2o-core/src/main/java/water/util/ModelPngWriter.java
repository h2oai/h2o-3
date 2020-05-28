package water.util;

import hex.ModelWithVisualization;
import water.api.StreamWriter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ModelPngWriter implements StreamWriter {

    private final ModelWithVisualization model;

    public ModelPngWriter(ModelWithVisualization model) {
        this.model = model;
    }

    @Override
    public void writeTo(OutputStream os) {
        try {
            Path pngtemp = Files.createTempFile("", "visualization.png");
            model.visualize(pngtemp.toAbsolutePath().toString());

            try (InputStream is = new FileInputStream(writeToZip(pngtemp).toFile())) {
                byte[] buf = new byte[8192];
                int c;
                while ((c = is.read(buf, 0, buf.length)) > 0) {
                    os.write(buf, 0, c);
                    os.flush();
                }
                os.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Reading the png and writing it to the stream failed.", e);
        }
    }

    /**
     * Creates new ZIP archive containing file or directory represented by passed {@code path}. If {@code path}
     * represents directory, all its files and sub-directories are recursively added to the resulting archive.
     *
     * @param path file or directory to be included in new ZIP archive
     * @return {@link Path} of newly created ZIP file
     */
    private Path writeToZip(Path path) throws IOException {
        Path destZipFile = Files.createTempFile("", "visualization.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destZipFile.toFile()))) {
            if (Files.isDirectory(path)) {
                zipDirectory(path.toFile(), "export", zos);
            } else {
                putEntryToZip(path.toFile(), "", zos);
            }
            zos.flush();
        }
        return destZipFile;
    }

    private void zipDirectory(File folder, String parentFolder, ZipOutputStream zos) throws IOException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipDirectory(file, parentFolder + "/" + file.getName(), zos);
            } else {
                putEntryToZip(file, parentFolder, zos);
            }
        }
    }

    private void putEntryToZip(File file, String parentFolder, ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
        try (InputStream is = new FileInputStream(file); BufferedInputStream bis = new BufferedInputStream(is)) {
            byte[] bytesIn = new byte[4096];
            int read;
            while ((read = bis.read(bytesIn)) != -1) {
                zos.write(bytesIn, 0, read);
            }
            zos.closeEntry();
        }
    }
}
