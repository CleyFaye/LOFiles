package net.cleyfaye.loimagecomp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class DocODT {

    private File mODTFile;
    private File mTempPath = Files.createTempDirectory("loimgcomp").toFile();
    private List<String> mFiles = new ArrayList<>();
    private Map<String, ImageInfo> mImagesMap = new HashMap<>();
    private List<ImageInfo> mImages = new ArrayList<>();

    public int getImagesCount()
    {
        return mImages.size();
    }

    public ImageInfo getImageInfo(int index)
    {
        return mImages.get(index);
    }

    public DocODT(File odtFile) throws IOException,
            ParserConfigurationException, SAXException {
        mTempPath.deleteOnExit();
        mODTFile = odtFile;
        extractFiles();
        checkMimeType();
        readImagesInfo();
    }

    private void extractFiles() throws IOException
    {
        try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(
                mODTFile))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInput.getNextEntry()) != null) {
                String fileName = zipEntry.getName();
                File tempPath = new File(mTempPath, fileName);
                tempPath.getParentFile().mkdirs();
                try (FileOutputStream output = new FileOutputStream(tempPath)) {
                    byte[] buffer = new byte[4096];
                    int readCount;
                    while ((readCount = zipInput.read(buffer)) > 0) {
                        output.write(buffer, 0, readCount);
                    }
                    mFiles.add(fileName);
                }
            }
        }
    }

    private void checkMimeType() throws IOException
    {
        File mimetypeFile = new File(mTempPath, "mimetype");
        List<String> lines = Files.readAllLines(mimetypeFile.toPath(),
                Charset.forName("UTF-8"));
        if (lines.size() < 1
                || lines.get(0).compareTo(
                        "application/vnd.oasis.opendocument.text") != 0) {
            throw new IOException("Not an ODT file");
        }
    }

    private static SAXParserFactory sSAXFactory = SAXParserFactory
            .newInstance();

    public static double sizeStringToDouble(String sizeString)
            throws IOException
    {
        if (sizeString == null || sizeString.isEmpty()) {
            return 0;
        }
        if (sizeString.endsWith("cm")) {
            return Double.valueOf(sizeString.substring(0,
                    sizeString.length() - 2));
        } else if (sizeString.endsWith("in")) {
            // Not sure this is even possible. Too lazy to read full spec.
            return Double.valueOf(sizeString.substring(0,
                    sizeString.length() - 2)) * 2.54;
        }
        throw new IOException("Unexpected image size information");
    }

    private class ContentReaderHandler extends DefaultHandler {
        private boolean mReadingFrame = false;
        private double mReadingWidth = 0;
        private double mReadingHeight = 0;
        private String mFileName;
        private final Map<String, ImageInfo> mImagesMap;
        private final List<ImageInfo> mImages;

        public ContentReaderHandler(Map<String, ImageInfo> imagesMap,
                List<ImageInfo> images) {
            mImagesMap = imagesMap;
            mImages = images;
        }

        public void startElement(String uri, String localName, String qName,
                org.xml.sax.Attributes attributes) throws SAXException
        {
            try {
                if (qName.equals("draw:frame")) {
                    mReadingFrame = true;
                    mReadingWidth = sizeStringToDouble(attributes
                            .getValue("svg:width"));
                    mReadingHeight = sizeStringToDouble(attributes
                            .getValue("svg:height"));
                }
                if (mReadingFrame) {
                    if (qName.equals("draw:image")) {
                        mFileName = attributes.getValue("xlink:href");
                    }
                }
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }

        public void endElement(String uri, String localName, String qName)
                throws SAXException
        {
            try {
                if (qName.equals("draw:frame") && mReadingFrame) {
                    mReadingFrame = false;
                    if (mImagesMap.containsKey(mFileName)) {
                        mImagesMap.get(mFileName).increaseDrawSize(
                                mReadingWidth, mReadingHeight);
                    } else {
                        ImageInfo info = new ImageInfo(mTempPath, mFileName,
                                mReadingWidth, mReadingHeight);
                        mImagesMap.put(mFileName, info);
                        mImages.add(info);
                    }
                }
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }
    }

    private class StylesReaderHandler extends DefaultHandler {
        private final Map<String, ImageInfo> mImagesMap;
        private final List<ImageInfo> mImages;

        public StylesReaderHandler(Map<String, ImageInfo> imagesMap,
                List<ImageInfo> images) {
            mImagesMap = imagesMap;
            mImages = images;
        }

        public void startElement(String uri, String localName, String qName,
                org.xml.sax.Attributes attributes) throws SAXException
        {
            try {
                if (qName.equals("draw:fill-image")) {
                    String fileName = attributes.getValue("xlink:href");
                    if (!mImagesMap.containsKey(fileName)) {
                        ImageInfo info = new ImageInfo(mTempPath, fileName, 0,
                                0);
                        mImagesMap.put(fileName, info);
                        mImages.add(info);
                    }
                }
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }
    }

    private void readImagesInfo() throws IOException,
            ParserConfigurationException, SAXException
    {
        File contentFile = new File(mTempPath, "content.xml");
        File styleFile = new File(mTempPath, "styles.xml");
        SAXParser saxParser = sSAXFactory.newSAXParser();

        saxParser.parse(contentFile, new ContentReaderHandler(mImagesMap,
                mImages));
        saxParser
                .parse(styleFile, new StylesReaderHandler(mImagesMap, mImages));
    }

    public static interface ImageFilter {
        public boolean filterImage(File tempDir, ImageInfo imageInfo,
                OutputStream output) throws Exception;

        public String filterImageSuffix(File tempDir, ImageInfo imageInfo)
                throws Exception;
    }

    public boolean createCopy(File target, ImageFilter imageFilter)
            throws Exception
    {
        byte[] buffer = new byte[4096];
        Map<String, String> namesSubstitution = new HashMap();
        for (ImageInfo info : mImagesMap.values()) {
            if (!info.isEmbedded()) {
                continue;
            }
            String newSuffix = imageFilter.filterImageSuffix(mTempPath, info);
            if (newSuffix == null) {
                return false;
            }
            String newName = MainWindow.replaceSuffix(info.getRelativeName(),
                    newSuffix);
            namesSubstitution.put(info.getRelativeName(), newName);
        }
        try (ZipOutputStream zipOutput = new ZipOutputStream(
                new FileOutputStream(target))) {
            zipOutput.setLevel(9);
            for (String filePath : mFiles) {
                if (filePath.startsWith("Pictures/")) {
                    // We'll do pictures at the end
                    continue;
                }
                if (filePath.equals("content.xml")) {
                    // This one is special. file names might change.
                    zipOutput.putNextEntry(new ZipEntry("content.xml"));
                    try (FileInputStream fis = new FileInputStream(new File(
                            mTempPath, "content.xml"))) {
                        filterContent(namesSubstitution, fis, zipOutput);
                    }
                    zipOutput.closeEntry();
                    continue;
                }
                if (filePath.equals("META-INF/manifest.xml")) {
                    // Special too. If we don't "fix" the manifest, LO gets all
                    // wonkytonky
                    // Note: I could generate a new "legit" manifest. But it's
                    // outside the scope of this exercise :)
                    zipOutput
                            .putNextEntry(new ZipEntry("META-INF/manifest.xml"));
                    try (FileInputStream fis = new FileInputStream(new File(
                            mTempPath, "META-INF/manifest.xml"))) {
                        filterManifest(namesSubstitution, fis, zipOutput);
                    }
                    zipOutput.closeEntry();
                    continue;
                }
                if (filePath.equals("styles.xml")) {
                    zipOutput.putNextEntry(new ZipEntry("styles.xml"));
                    try (FileInputStream fis = new FileInputStream(new File(
                            mTempPath, "styles.xml"))) {
                        filterStyles(namesSubstitution, fis, zipOutput);
                    }
                    zipOutput.closeEntry();
                    continue;
                }
                zipOutput.putNextEntry(new ZipEntry(filePath));
                try (FileInputStream input = new FileInputStream(new File(
                        mTempPath, filePath))) {
                    int length;
                    while ((length = input.read(buffer)) > 0) {
                        zipOutput.write(buffer, 0, length);
                    }
                }
                zipOutput.closeEntry();
            }
            // Now we do pictures
            if (imageFilter == null) {
                imageFilter = new ImageFilter() {

                    @Override
                    public boolean filterImage(File tempDir,
                            ImageInfo imageInfo, OutputStream output)
                            throws FileNotFoundException, IOException
                    {
                        try (FileInputStream fis = new FileInputStream(
                                new File(tempDir, imageInfo.getRelativeName()))) {
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = fis.read(buffer)) > 0) {
                                output.write(buffer, 0, length);
                            }
                        }
                        return true;
                    }

                    @Override
                    public String filterImageSuffix(File tempDir,
                            ImageInfo imageInfo) throws Exception
                    {
                        return MainWindow.getFileSuffix(imageInfo
                                .getRelativeName());
                    }
                };
            }
            for (ImageInfo info : mImagesMap.values()) {
                if (!info.isEmbedded()) {
                    continue;
                }
                zipOutput.putNextEntry(new ZipEntry(namesSubstitution.get(info
                        .getRelativeName())));
                if (!imageFilter.filterImage(mTempPath, info, zipOutput)) {
                    return false;
                }
                zipOutput.closeEntry();
            }
        }
        return true;
    }

    private static DocumentBuilderFactory sDOMFactory = DocumentBuilderFactory
            .newInstance();
    private static TransformerFactory sDOMOutFactory = TransformerFactory
            .newInstance();

    private void filterContent(Map<String, String> imageReplacements,
            InputStream input, OutputStream output)
            throws ParserConfigurationException, SAXException, IOException,
            TransformerException
    {
        DocumentBuilder builder = sDOMFactory.newDocumentBuilder();
        Document doc = builder.parse(input);

        NodeList nodes = doc.getElementsByTagName("draw:image");
        for (int i = 0; i < nodes.getLength(); ++i) {
            Node node = nodes.item(i);
            NamedNodeMap attributes = node.getAttributes();
            Node nodeAttr = attributes.getNamedItem("xlink:href");
            if (imageReplacements.containsKey(nodeAttr.getTextContent())) {
                nodeAttr.setTextContent(imageReplacements.get(nodeAttr
                        .getTextContent()));
            }
        }
        Transformer transformer = sDOMOutFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(output);
        transformer.transform(source, result);
    }

    /*
     * private void filterContent(Map<String, String> imageReplacements,
     * InputStream input, OutputStream output) throws
     * ParserConfigurationException, SAXException, IOException,
     * TransformerException { StringBuilder builder = new StringBuilder();
     * BufferedReader reader = new BufferedReader(new InputStreamReader(input,
     * Charset.forName("UTF-8"))); String line; while ((line =
     * reader.readLine()) != null) { builder.append(line); } String result =
     * builder.toString(); for (String key : imageReplacements.keySet()) {
     * result = result.replace(key, imageReplacements.get(key)); }
     * output.write(result.getBytes(Charset.forName("UTF-8"))); }
     */

    public long getSize()
    {
        return mODTFile.length();
    }

    private void filterManifest(Map<String, String> imageReplacements,
            InputStream input, OutputStream output)
            throws ParserConfigurationException, SAXException, IOException,
            TransformerException
    {
        DocumentBuilder builder = sDOMFactory.newDocumentBuilder();
        Document doc = builder.parse(input);

        NodeList nodes = doc.getElementsByTagName("manifest:file-entry");
        for (int i = 0; i < nodes.getLength(); ++i) {
            Node node = nodes.item(i);
            NamedNodeMap attributes = node.getAttributes();
            Node nodeAttr = attributes.getNamedItem("manifest:full-path");
            if (imageReplacements.containsKey(nodeAttr.getTextContent())) {
                nodeAttr.setTextContent(imageReplacements.get(nodeAttr
                        .getTextContent()));
            }
        }
        Transformer transformer = sDOMOutFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(output);
        transformer.transform(source, result);
    }

    private void filterStyles(Map<String, String> imageReplacements,
            InputStream input, OutputStream output)
            throws ParserConfigurationException, SAXException, IOException,
            TransformerException
    {
        DocumentBuilder builder = sDOMFactory.newDocumentBuilder();
        Document doc = builder.parse(input);

        NodeList nodes = doc.getElementsByTagName("draw:fill-image");
        for (int i = 0; i < nodes.getLength(); ++i) {
            Node node = nodes.item(i);
            NamedNodeMap attributes = node.getAttributes();
            Node nodeAttr = attributes.getNamedItem("xlink:href");
            if (imageReplacements.containsKey(nodeAttr.getTextContent())) {
                nodeAttr.setTextContent(imageReplacements.get(nodeAttr
                        .getTextContent()));
            }
        }
        Transformer transformer = sDOMOutFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(output);
        transformer.transform(source, result);
    }

    /*
     * private void filterManifest(Map<String, String> imageReplacements,
     * InputStream input, OutputStream output) throws
     * ParserConfigurationException, SAXException, IOException,
     * TransformerException { StringBuilder builder = new StringBuilder();
     * BufferedReader reader = new BufferedReader(new InputStreamReader(input,
     * Charset.forName("UTF-8"))); String line; while ((line =
     * reader.readLine()) != null) { builder.append(line); } String result =
     * builder.toString(); for (String key : imageReplacements.keySet()) {
     * result = result.replace(key, imageReplacements.get(key)); }
     * output.write(result.getBytes(Charset.forName("UTF-8"))); }
     */
}
