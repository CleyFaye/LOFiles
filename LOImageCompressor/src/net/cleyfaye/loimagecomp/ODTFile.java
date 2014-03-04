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

/**
 * Manage the content of an ODT file
 * 
 * This class is able to load an ODT file, and save it somewhere else.
 * 
 * The save will pass files through some filters that can change the content.
 * For now it only change pictures.
 * 
 * TODO Implement a cleaner filter system TODO Force save of the mimetype as
 * uncompressed TODO Load the original document as DOM instead of using SAX TODO
 * Use XPath
 * 
 * @author Cley Faye
 */
public class ODTFile {

    /** The origianl ODT file. */
    private File mODTFile;
    /** A temporary path where the ODT file is extracted. */
    private File mTempPath = Files.createTempDirectory("loimgcomp").toFile();
    /** A list of files present in the original ODT. */
    private List<String> mFiles = new ArrayList<>();
    /**
     * A mapping between image files (in "Pictures/" directory) and the stored
     * image informations
     */
    private Map<String, ImageInfo> mImagesMap = new HashMap<>();
    /** All the image informations objects */
    private List<ImageInfo> mImages = new ArrayList<>();

    /** Return the number of images in the file */
    public int getImagesCount()
    {
        return mImages.size();
    }

    /** Return the selected image informations */
    public ImageInfo getImageInfo(int index)
    {
        return mImages.get(index);
    }

    /** Create an ODTFile object from an existing ODT file */
    public ODTFile(File odtFile) throws IOException,
            ParserConfigurationException, SAXException {
        mTempPath.deleteOnExit();
        mODTFile = odtFile;
        extractFiles();
        checkMimeType();
        readImagesInfo();
    }

    /** Extract files in the temporary directory */
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

    /** Check that the extracted file is an actual ODT file */
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

    /**
     * Get the image size from a string to a double.
     * 
     * @param sizeString
     *            The string containing the size. Either "XXcm" or "YYin" is
     *            accepted.
     * @return The size, in cm.
     * 
     *         TODO move this in an utility class
     */
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

    /**
     * SAX handler to get image print size from the content.xml
     * 
     * TODO remove this in favor of XPath
     * 
     * @author Cley Faye
     */
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

    /**
     * Get image information from styles.xml.
     * 
     * TODO remove this in favor of XPath. OR remove it completely, as we don't
     * extract anything useful here.
     * 
     * @author Cley Faye
     */
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

    /**
     * Get image informations from content.xml and styles.xml
     * 
     * This mainly extract print size from content.xml
     */
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

    /** A filter that process each image from source to destination. */
    public static interface ImageFilter {
        public boolean filterImage(File tempDir, ImageInfo imageInfo,
                OutputStream output) throws Exception;

        public String filterImageSuffix(File tempDir, ImageInfo imageInfo)
                throws Exception;
    }

    /**
     * Save a copy of the ODT file.
     * 
     * This function create a copy of the original ODT file, and change it's
     * content according to given filters.
     * 
     * @param target
     *            The output file
     * @param imageFilter
     *            A filter for image files
     * @return true if the process completed, false if it was interrupted by a
     *         filter callback
     * @throws Exception
     */
    public boolean createCopy(File target, ImageFilter imageFilter)
            throws Exception
    {
        byte[] buffer = new byte[4096];
        // First, we get new names for all pictures. Needed mainly to change
        // from one file format to another
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
        // Create the output
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
                    // TODO treat special cases like this separately
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
                // TODO move this out of the way
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

    /**
     * Replace pictures path in content.xml
     * 
     * TODO Use XPath to make sure we won't miss anything
     * 
     * @param imageReplacements
     *            List of filenames to replace
     * @param input
     *            Source content.xml
     * @param output
     *            Destination content.xml
     */
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

    /** Return the size of the original ODT file */
    public long getSize()
    {
        return mODTFile.length();
    }

    /**
     * Replace all files references in the manifest
     * 
     * TODO create manifest while saving instead of changing the old one
     * 
     * @param imageReplacements
     *            Image names to replace
     * @param input
     *            Source manifest file
     * @param output
     *            Destination manifest file
     */
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

    /**
     * Filter images path in styles.xml
     * 
     * @param imageReplacements
     *            Image names replacements
     * @param input
     *            Source styles.xml
     * @param output
     *            Destination styles.xml
     */
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
}
