package net.cleyfaye.loimagecomp;

import static net.cleyfaye.loimagecomp.Utils.getFileSuffix;
import static net.cleyfaye.loimagecomp.Utils.replaceFileSuffix;
import static net.cleyfaye.loimagecomp.Utils.sizeStringToDouble;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
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
 * uncompressed TODO Load the original document as DOM instead of using SAX
 * 
 * TODO Use XPath
 * 
 * @author Cley Faye
 */
public class ODTFile {

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

        public ContentReaderHandler(final Map<String, ImageInfo> imagesMap,
                final List<ImageInfo> images) {
            mImagesMap = imagesMap;
            mImages = images;
        }

        @Override
        public void endElement(final String uri, final String localName,
                final String qName) throws SAXException
        {
            try {
                if (qName.equals("draw:frame") && mReadingFrame) {
                    mReadingFrame = false;
                    if (mImagesMap.containsKey(mFileName)) {
                        mImagesMap.get(mFileName).increaseDrawSize(
                                mReadingWidth, mReadingHeight);
                    } else {
                        final ImageInfo info = new ImageInfo(mTempPath,
                                mFileName, mReadingWidth, mReadingHeight);
                        mImagesMap.put(mFileName, info);
                        mImages.add(info);
                    }
                }
            } catch (final IOException e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void startElement(final String uri, final String localName,
                final String qName, final org.xml.sax.Attributes attributes)
                throws SAXException
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
            } catch (final IOException e) {
                throw new SAXException(e);
            }
        }
    }

    /** A filter that process each image from source to destination. */
    public static interface ImageFilter {
        public boolean filterImage(File tempDir, ImageInfo imageInfo,
                OutputStream output) throws Exception;

        public String filterImageSuffix(File tempDir, ImageInfo imageInfo)
                throws Exception;
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

        public StylesReaderHandler(final Map<String, ImageInfo> imagesMap,
                final List<ImageInfo> images) {
            mImagesMap = imagesMap;
            mImages = images;
        }

        @Override
        public void startElement(final String uri, final String localName,
                final String qName, final org.xml.sax.Attributes attributes)
                throws SAXException
        {
            try {
                if (qName.equals("draw:fill-image")) {
                    final String fileName = attributes.getValue("xlink:href");
                    if (!mImagesMap.containsKey(fileName)) {
                        final ImageInfo info = new ImageInfo(mTempPath,
                                fileName, 0, 0);
                        mImagesMap.put(fileName, info);
                        mImages.add(info);
                    }
                }
            } catch (final IOException e) {
                throw new SAXException(e);
            }
        }
    }

    /** The origianl ODT file. */
    private final File mODTFile;
    /** A temporary path where the ODT file is extracted. */
    private final File mTempPath = Files.createTempDirectory("loimgcomp")
            .toFile();

    /** A list of files present in the original ODT. */
    private final List<String> mFiles = new ArrayList<>();

    /**
     * A mapping between image files (in "Pictures/" directory) and the stored
     * image informations
     */
    private final Map<String, ImageInfo> mImagesMap = new HashMap<>();

    /** All the image informations objects */
    private final List<ImageInfo> mImages = new ArrayList<>();

    private static SAXParserFactory sSAXFactory = SAXParserFactory
            .newInstance();

    private static DocumentBuilderFactory sDOMFactory = DocumentBuilderFactory
            .newInstance();

    private static TransformerFactory sDOMOutFactory = TransformerFactory
            .newInstance();

    /** Create an ODTFile object from an existing ODT file */
    public ODTFile(final File odtFile) throws IOException,
            ParserConfigurationException, SAXException {
        mTempPath.deleteOnExit();
        mODTFile = odtFile;
        extractFiles();
        checkMimeType();
        readImagesInfo();
    }

    /** Check that the extracted file is an actual ODT file */
    private void checkMimeType() throws IOException
    {
        final File mimetypeFile = new File(mTempPath, "mimetype");
        final List<String> lines = Files.readAllLines(mimetypeFile.toPath(),
                Charset.forName("UTF-8"));
        if (lines.size() < 1
                || lines.get(0).compareTo(
                        "application/vnd.oasis.opendocument.text") != 0) {
            throw new IOException("Not an ODT file");
        }
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
    public boolean createCopy(final File target, ImageFilter imageFilter)
            throws Exception
    {
        final byte[] buffer = new byte[4096];
        // First, we get new names for all pictures. Needed mainly to change
        // from one file format to another
        final Map<String, String> namesSubstitution = new HashMap<>();
        for (final ImageInfo info : mImagesMap.values()) {
            if (!info.isEmbedded()) {
                continue;
            }
            final String newSuffix = imageFilter.filterImageSuffix(mTempPath,
                    info);
            if (newSuffix == null) {
                return false;
            }
            final String newName = replaceFileSuffix(info.getRelativeName(),
                    newSuffix);
            namesSubstitution.put(info.getRelativeName(), newName);
        }
        // Create the output
        try (ZipOutputStream zipOutput = new ZipOutputStream(
                new FileOutputStream(target))) {
            zipOutput.setLevel(9);
            for (final String filePath : mFiles) {
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
                    public boolean filterImage(final File tempDir,
                            final ImageInfo imageInfo, final OutputStream output)
                            throws FileNotFoundException, IOException
                    {
                        try (FileInputStream fis = new FileInputStream(
                                new File(tempDir, imageInfo.getRelativeName()))) {
                            final byte[] buffer = new byte[4096];
                            int length;
                            while ((length = fis.read(buffer)) > 0) {
                                output.write(buffer, 0, length);
                            }
                        }
                        return true;
                    }

                    @Override
                    public String filterImageSuffix(final File tempDir,
                            final ImageInfo imageInfo) throws Exception
                    {
                        return getFileSuffix(imageInfo.getRelativeName());
                    }
                };
            }
            for (final ImageInfo info : mImagesMap.values()) {
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

    /** Extract files in the temporary directory */
    private void extractFiles() throws IOException
    {
        try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(
                mODTFile))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInput.getNextEntry()) != null) {
                final String fileName = zipEntry.getName();
                final File tempPath = new File(mTempPath, fileName);
                tempPath.getParentFile().mkdirs();
                try (FileOutputStream output = new FileOutputStream(tempPath)) {
                    final byte[] buffer = new byte[4096];
                    int readCount;
                    while ((readCount = zipInput.read(buffer)) > 0) {
                        output.write(buffer, 0, readCount);
                    }
                    mFiles.add(fileName);
                }
            }
        }
    }

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
    private void filterContent(final Map<String, String> imageReplacements,
            final InputStream input, final OutputStream output)
            throws ParserConfigurationException, SAXException, IOException,
            TransformerException
    {
        final DocumentBuilder builder = sDOMFactory.newDocumentBuilder();
        final Document doc = builder.parse(input);

        final NodeList nodes = doc.getElementsByTagName("draw:image");
        for (int i = 0; i < nodes.getLength(); ++i) {
            final Node node = nodes.item(i);
            final NamedNodeMap attributes = node.getAttributes();
            final Node nodeAttr = attributes.getNamedItem("xlink:href");
            if (imageReplacements.containsKey(nodeAttr.getTextContent())) {
                nodeAttr.setTextContent(imageReplacements.get(nodeAttr
                        .getTextContent()));
            }
        }
        final Transformer transformer = sDOMOutFactory.newTransformer();
        final DOMSource source = new DOMSource(doc);
        final StreamResult result = new StreamResult(output);
        transformer.transform(source, result);
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
    private void filterManifest(final Map<String, String> imageReplacements,
            final InputStream input, final OutputStream output)
            throws ParserConfigurationException, SAXException, IOException,
            TransformerException
    {
        final DocumentBuilder builder = sDOMFactory.newDocumentBuilder();
        final Document doc = builder.parse(input);

        final NodeList nodes = doc.getElementsByTagName("manifest:file-entry");
        for (int i = 0; i < nodes.getLength(); ++i) {
            final Node node = nodes.item(i);
            final NamedNodeMap attributes = node.getAttributes();
            final Node nodeAttr = attributes.getNamedItem("manifest:full-path");
            if (imageReplacements.containsKey(nodeAttr.getTextContent())) {
                nodeAttr.setTextContent(imageReplacements.get(nodeAttr
                        .getTextContent()));
            }
        }
        final Transformer transformer = sDOMOutFactory.newTransformer();
        final DOMSource source = new DOMSource(doc);
        final StreamResult result = new StreamResult(output);
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
    private void filterStyles(final Map<String, String> imageReplacements,
            final InputStream input, final OutputStream output)
            throws ParserConfigurationException, SAXException, IOException,
            TransformerException
    {
        final DocumentBuilder builder = sDOMFactory.newDocumentBuilder();
        final Document doc = builder.parse(input);

        final NodeList nodes = doc.getElementsByTagName("draw:fill-image");
        for (int i = 0; i < nodes.getLength(); ++i) {
            final Node node = nodes.item(i);
            final NamedNodeMap attributes = node.getAttributes();
            final Node nodeAttr = attributes.getNamedItem("xlink:href");
            if (imageReplacements.containsKey(nodeAttr.getTextContent())) {
                nodeAttr.setTextContent(imageReplacements.get(nodeAttr
                        .getTextContent()));
            }
        }
        final Transformer transformer = sDOMOutFactory.newTransformer();
        final DOMSource source = new DOMSource(doc);
        final StreamResult result = new StreamResult(output);
        transformer.transform(source, result);
    }

    /** Return the selected image informations */
    public ImageInfo getImageInfo(final int index)
    {
        return mImages.get(index);
    }

    /** Return the number of images in the file */
    public int getImagesCount()
    {
        return mImages.size();
    }

    /** Return the size of the original ODT file */
    public long getSize()
    {
        return mODTFile.length();
    }

    /**
     * Get image informations from content.xml and styles.xml
     * 
     * This mainly extract print size from content.xml
     */
    private void readImagesInfo() throws IOException,
            ParserConfigurationException, SAXException
    {
        final File contentFile = new File(mTempPath, "content.xml");
        final File styleFile = new File(mTempPath, "styles.xml");
        final SAXParser saxParser = sSAXFactory.newSAXParser();

        saxParser.parse(contentFile, new ContentReaderHandler(mImagesMap,
                mImages));
        saxParser
                .parse(styleFile, new StylesReaderHandler(mImagesMap, mImages));
    }
}
