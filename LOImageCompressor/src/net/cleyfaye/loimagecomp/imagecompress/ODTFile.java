package net.cleyfaye.loimagecomp.imagecompress;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import net.cleyfaye.loimagecomp.imagecompress.interfaces.ImageFilter;
import net.cleyfaye.loimagecomp.utils.ProgressCheck;
import net.cleyfaye.loimagecomp.utils.ProgressCheck.Instance;
import net.cleyfaye.loimagecomp.utils.Utils;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Manage the content of an ODT file
 * 
 * This class is able to load an ODT file, and save it somewhere else.
 * 
 * The save will pass files through some filters that can change the content.
 * For now it only change pictures.
 * 
 * TODO Implement a cleaner filter system
 * 
 * TODO Force save of the mimetype as uncompressed
 * 
 * @author Cley Faye
 */
public class ODTFile {

    /** DOM of content.xml */
    private Document mContent;
    /** DOM of styles.xml */
    private Document mStyles;
    /** DOM of initial manifest */
    private Document mManifest;

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

    private static DocumentBuilderFactory sDOMFactory = DocumentBuilderFactory
            .newInstance();

    private static DocumentBuilder sDocumentBuilder = null;

    private static XPathFactory sXPathFactory = XPathFactory.newInstance();

    private static XPath sXPath = sXPathFactory.newXPath();

    /**
     * Create an ODTFile object from an existing ODT file
     * 
     * @throws XPathExpressionException
     */
    public ODTFile(final File odtFile, final ProgressCheck progressCheck)
            throws IOException, ParserConfigurationException, SAXException,
            XPathExpressionException {
        final Instance progress = new Instance(progressCheck);
        mTempPath.deleteOnExit();
        mODTFile = odtFile;
        extractFiles(progress);
        checkMimeType(progress);
        readXMLs(progress);
        readImagesInfo(progress);
    }

    /** Check that the extracted file is an actual ODT file */
    private void checkMimeType(final ProgressCheck progress) throws IOException
    {
        progress.progressMessage("Checking manifest");
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
     * @param progressCheck
     *            Callback to display progress
     * @return true if the process completed, false if it was interrupted by a
     *         filter callback
     * @throws Exception
     */
    public boolean createCopy(final File target, ImageFilter imageFilter,
            final ProgressCheck progressCheck) throws Exception
    {
        final Instance progress = new Instance(progressCheck);
        progress.progressNewMaxValue(mImagesMap.values().size() * 3
                + mFiles.size() + 4); // 4: mimetype content.xml styles.xml
                                      // manifest
        int progressValue = 0;
        final byte[] buffer = new byte[4096];
        // We get new names for all pictures. Needed mainly to change
        // from one file format to another
        final Map<String, String> namesSubstitution = new HashMap<>();
        progress.progressMessage("Preparing");
        for (final ImageInfo info : mImagesMap.values()) {
            if (!progress.progress(++progressValue)) {
                return false;
            }
            if (!info.isEmbedded()) {
                continue;
            }
            imageFilter.prepareImage(info);
        }
        for (final ImageInfo info : mImagesMap.values()) {
            if (!progress.progress(++progressValue)) {
                return false;
            }
            if (!info.isEmbedded()) {
                continue;
            }
            final String newName = imageFilter.getImageFileName(info);
            namesSubstitution.put(info.getRelativeName(), newName);
        }
        // We work on copies of content.xml and styles.xml
        final Document contentCopy = Utils.cloneDOM(mContent);
        final Document stylesCopy = Utils.cloneDOM(mStyles);
        final Document manifestCopy = Utils.cloneDOM(mManifest);

        // Create the output
        try (ZipOutputStream zipOutput = new ZipOutputStream(
                new FileOutputStream(target))) {

            progress.progressMessage("Core content");
            // First save mimetype
            zipOutput.setLevel(0);
            zipOutput.setMethod(ZipOutputStream.STORED);

            {
                final byte[] mimetypeBytes = "application/vnd.oasis.opendocument.text"
                        .getBytes(Charset.forName("ASCII"));
                final ZipEntry mimetypeEntry = new ZipEntry("mimetype");
                mimetypeEntry.setMethod(ZipEntry.STORED);
                mimetypeEntry.setCompressedSize(mimetypeBytes.length);
                mimetypeEntry.setSize(mimetypeBytes.length);
                final CRC32 crc = new CRC32();
                crc.update(mimetypeBytes);
                mimetypeEntry.setCrc(crc.getValue());
                zipOutput.putNextEntry(mimetypeEntry);
                zipOutput.write(mimetypeBytes);
                if (!progress.progress(++progressValue)) {
                    return false;
                }
            }

            // Then save content.xml and styles.xml
            zipOutput.setMethod(ZipOutputStream.DEFLATED);
            zipOutput.setLevel(9);
            zipOutput.putNextEntry(new ZipEntry("content.xml"));
            filterContent(namesSubstitution, contentCopy);
            Utils.saveDOM(contentCopy, zipOutput);
            if (!progress.progress(++progressValue)) {
                return false;
            }
            zipOutput.putNextEntry(new ZipEntry("styles.xml"));
            filterStyles(namesSubstitution, stylesCopy);
            Utils.saveDOM(stylesCopy, zipOutput);
            if (!progress.progress(++progressValue)) {
                return false;
            }

            // Save manifest.xml
            zipOutput.putNextEntry(new ZipEntry("META-INF/manifest.xml"));
            filterManifest(namesSubstitution, manifestCopy);
            Utils.saveDOM(manifestCopy, zipOutput);
            if (!progress.progress(++progressValue)) {
                return false;
            }

            // Save all pictures
            progress.progressMessage("Images");
            if (imageFilter == null) {
                imageFilter = new DummyImageFilter();
            }
            for (final ImageInfo info : mImagesMap.values()) {
                if (!progress.progress(++progressValue)) {
                    return false;
                }
                if (!info.isEmbedded()) {
                    continue;
                }
                zipOutput.putNextEntry(new ZipEntry(namesSubstitution.get(info
                        .getRelativeName())));
                imageFilter.getImageData(info, zipOutput);
                zipOutput.closeEntry();
            }

            // Save all remaining files
            progress.progressMessage("Remaining files");
            for (final String filePath : mFiles) {
                if (!progress.progress(++progressValue)) {
                    return false;
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

        }
        return true;
    }

    /** Extract files in the temporary directory */
    private void extractFiles(final ProgressCheck progress) throws IOException
    {
        progress.progressMessage("Uncompressing file");
        try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(
                mODTFile))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInput.getNextEntry()) != null) {
                final String fileName = zipEntry.getName();
                final File tempPath = new File(mTempPath, fileName);
                Utils.createTempFilePath(tempPath);
                tempPath.deleteOnExit();
                try (FileOutputStream output = new FileOutputStream(tempPath)) {
                    final byte[] buffer = new byte[4096];
                    int readCount;
                    while ((readCount = zipInput.read(buffer)) > 0) {
                        output.write(buffer, 0, readCount);
                    }
                }
                if (fileName.equals("mimetype")
                        || fileName.equals("content.xml")
                        || fileName.equals("styles.xml")
                        || fileName.equals("META-INF/manifest.xml")) {
                    // Don't store them in mFiles
                } else if (fileName.startsWith("Pictures/")) {
                    mImagesMap.put(fileName, new ImageInfo(this, fileName));
                } else {
                    mFiles.add(fileName);
                }
            }
        }
    }

    /**
     * Replace pictures path in content.xml
     * 
     * @param imageReplacements
     *            List of filenames to replace
     * @param input
     *            Source content.xml
     */
    private void filterContent(final Map<String, String> imageReplacements,
            final Document input) throws ParserConfigurationException,
            SAXException, IOException, TransformerException,
            XPathExpressionException
    {
        final XPathExpression expr = sXPath
                .compile("//*[@*[name()='xlink:href' and starts-with(., 'Pictures/')]]");
        final NodeList nodes = (NodeList) expr.evaluate(input,
                XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); ++i) {
            final Node node = nodes.item(i);
            final NamedNodeMap attributes = node.getAttributes();
            final Node nodeAttr = attributes.getNamedItem("xlink:href");
            if (imageReplacements.containsKey(nodeAttr.getTextContent())) {
                nodeAttr.setTextContent(imageReplacements.get(nodeAttr
                        .getTextContent()));
            }
        }
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
     */
    private void filterManifest(final Map<String, String> imageReplacements,
            final Document input) throws ParserConfigurationException,
            SAXException, IOException, TransformerException
    {
        final NodeList nodes = input
                .getElementsByTagName("manifest:file-entry");
        for (int i = 0; i < nodes.getLength(); ++i) {
            final Node node = nodes.item(i);
            final NamedNodeMap attributes = node.getAttributes();
            final Node nodeAttr = attributes.getNamedItem("manifest:full-path");
            if (imageReplacements.containsKey(nodeAttr.getTextContent())) {
                nodeAttr.setTextContent(imageReplacements.get(nodeAttr
                        .getTextContent()));
            }
        }
    }

    /**
     * Filter images path in styles.xml
     * 
     * @param imageReplacements
     *            Image names replacements
     * @param input
     *            Source styles.xml
     */
    private void filterStyles(final Map<String, String> imageReplacements,
            final Document input) throws ParserConfigurationException,
            SAXException, IOException, TransformerException,
            XPathExpressionException
    {
        final XPathExpression expr = sXPath
                .compile("//*[@*[name()='xlink:href' and starts-with(., 'Pictures/')]]");
        final NodeList nodes = (NodeList) expr.evaluate(input,
                XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); ++i) {
            final Node node = nodes.item(i);
            final NamedNodeMap attributes = node.getAttributes();
            final Node nodeAttr = attributes.getNamedItem("xlink:href");
            if (imageReplacements.containsKey(nodeAttr.getTextContent())) {
                nodeAttr.setTextContent(imageReplacements.get(nodeAttr
                        .getTextContent()));
            }
        }
    }

    /** Return the selected image informations */
    public Collection<ImageInfo> getAllImageInfo()
    {
        return mImagesMap.values();
    }

    /**
     * Return a file from the original ODT source.
     * 
     * @param fileName
     *            The relative file name
     * @return The file object, or null if the file doesn't exist.
     */
    public File getExtractedFile(final String fileName) throws IOException
    {
        final File relativePath = new File(fileName);
        if (relativePath.isAbsolute()) {
            return null;
        }
        final File resultFile = new File(mTempPath, fileName);
        return resultFile.exists() ? resultFile : null;
    }

    /** Return the number of images in the file */
    public int getImagesCount()
    {
        return mImagesMap.size();
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
     * 
     * @throws XPathExpressionException
     */
    private void readImagesInfo(final ProgressCheck progress)
            throws IOException, ParserConfigurationException, SAXException,
            XPathExpressionException
    {
        progress.progressMessage("Reading image informations");
        int progressValue = 0;
        final XPathExpression contentImageExpression = sXPath
                .compile("//*[name()='draw:frame']/*[name()='draw:image'][@*[name()='xlink:href' and starts-with(., 'Pictures/')]]");
        final XPathExpression stylesImageExpression = sXPath
                .compile("//*[@*[name()='xlink:href' and starts-with(., 'Pictures/')]]");
        final NodeList contentImageNodes = (NodeList) contentImageExpression
                .evaluate(mContent, XPathConstants.NODESET);
        final NodeList styleImageNodes = (NodeList) stylesImageExpression
                .evaluate(mStyles, XPathConstants.NODESET);
        progress.progressNewMaxValue(contentImageNodes.getLength()
                + styleImageNodes.getLength() + 1);
        for (int i = 0; i < contentImageNodes.getLength(); ++i) {
            final Node node = contentImageNodes.item(i);
            final Node parentNode = node.getParentNode();
            if (parentNode.getNodeName().equals("draw:frame")) {
                final String imagePath = node.getAttributes()
                        .getNamedItem("xlink:href").getTextContent();
                final NamedNodeMap parentNodeAttributes = parentNode
                        .getAttributes();
                final ImageInfo imageInfo = mImagesMap.get(imagePath);
                final Node widthItem = parentNodeAttributes
                        .getNamedItem("svg:width");
                final Node heightItem = parentNodeAttributes
                        .getNamedItem("svg:height");
                if (widthItem != null && heightItem != null) {
                    final double drawWidth = Utils.sizeStringToDouble(widthItem
                            .getTextContent());
                    final double drawHeight = Utils
                            .sizeStringToDouble(heightItem.getTextContent());
                    imageInfo.increaseDrawSize(drawWidth, drawHeight);
                }
                final Node nameNode = parentNodeAttributes
                        .getNamedItem("draw:name");
                if (nameNode != null) {
                    imageInfo.addName(nameNode.getTextContent());
                }
            }
            progress.progress(++progressValue);
        }
        for (int i = 0; i < styleImageNodes.getLength(); ++i) {
            final Node node = styleImageNodes.item(i);
            final NamedNodeMap nodeAttributes = node.getAttributes();
            final String imagePath = nodeAttributes.getNamedItem("xlink:href")
                    .getTextContent();
            final ImageInfo imageInfo = mImagesMap.get(imagePath);
            final Node displayNameNode = nodeAttributes
                    .getNamedItem("draw:display-name");
            if (displayNameNode != null) {
                imageInfo.addName(displayNameNode.getTextContent());
            } else {
                final Node nameNode = nodeAttributes.getNamedItem("draw:name");
                if (nameNode != null) {
                    imageInfo.addName(nameNode.getTextContent());
                }
            }
            progress.progress(++progressValue);
        }
    }

    /**
     * Read XMLs files (content.xml and styles.xml)
     * 
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    private void readXMLs(final ProgressCheck progress)
            throws ParserConfigurationException, SAXException, IOException
    {
        progress.progressMessage("Reading content");
        progress.progressNewMaxValue(3);
        if (sDocumentBuilder == null) {
            sDocumentBuilder = sDOMFactory.newDocumentBuilder();
        }
        mContent = sDocumentBuilder.parse(getExtractedFile("content.xml"));
        progress.progress(1);
        mStyles = sDocumentBuilder.parse(getExtractedFile("styles.xml"));
        progress.progress(2);
        mManifest = sDocumentBuilder
                .parse(getExtractedFile("META-INF/manifest.xml"));
    }
}
