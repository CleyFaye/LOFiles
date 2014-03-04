package net.cleyfaye.loimagecomp.imagecompress;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import net.cleyfaye.loimagecomp.imagecompress.ODTFile.ImageFilter;

/**
 * Filter to resize images when saving ODT file.
 * 
 * @author Cley Faye
 */
public class DownsampleImageFilter implements ImageFilter {

    /** Target DPI */
    private final double mDPI;
    /** Target JPG quality */
    private final int mJPGQuality;
    /** Target Interpolation mode */
    private Object mInterpolation;
    /** Do we retain transparency or not */
    private final boolean mKillTransparency;
    /** Temporary directory for compressed images */
    private final File mTempDir = Files.createTempDirectory("loimgcomp")
            .toFile();
    /** List of temporary files for each images path */
    private final Map<String, File> mImageFiles = new HashMap<>();

    public DownsampleImageFilter(final double dpi, final int jpgQuality,
            final int scalingMethod, final boolean killTransparency)
            throws IOException {
        mDPI = dpi;
        mJPGQuality = jpgQuality;
        mKillTransparency = killTransparency;
        mTempDir.deleteOnExit();
        switch (scalingMethod) {
        default:
        case 0:
            mInterpolation = RenderingHints.VALUE_INTERPOLATION_BICUBIC;
            break;
        case 1:
            mInterpolation = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
            break;
        }
    }

    @Override
    public boolean filterImage(final File tempDir, final ImageInfo imageInfo,
            final OutputStream output) throws Exception
    {
        // At this point, each pictures is already saved somewhere.
        final byte[] buffer = new byte[4096];
        final File file = mImageFiles.get(imageInfo.getRelativeName());
        try (FileInputStream fis = new FileInputStream(file)) {
            int length;
            while ((length = fis.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        }
        file.delete();
        return true;
    }

    @Override
    public String filterImageSuffix(final File tempDir,
            final ImageInfo imageInfo) throws Exception
    {
        // This function is called one time on each image, before actually
        // saving. Since we need to know the final image suffix at this
        // point, we have to save in each format to see which one is most
        // efficient.
        // Temp save file is stored for the next step.
        final ImageSize targetImageSize = ImageSize.projectImageSize(
                imageInfo.getImageSizePx(), imageInfo.getDrawSizeCm(), mDPI);
        final BufferedImage original = ImageIO.read(new File(tempDir, imageInfo
                .getRelativeName()));
        final int imageType = mKillTransparency ? BufferedImage.TYPE_INT_RGB
                : original.getType();
        final BufferedImage resized = new BufferedImage(
                (int) targetImageSize.getX(), (int) targetImageSize.getY(),
                imageType);
        final Graphics2D gc = (Graphics2D) resized.getGraphics();
        gc.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        gc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, mInterpolation);
        gc.drawImage(original, 0, 0, (int) targetImageSize.getX(),
                (int) targetImageSize.getY(), null);
        gc.dispose();

        if (original.getTransparency() == Transparency.OPAQUE
                || mKillTransparency) {
            // Either opaque, or kill transparency; save as jpeg or png,
            // whichever is better
            final ImageWriter writer = ImageIO.getImageWritersBySuffix("jpg")
                    .next();
            final ImageWriteParam iwp = writer.getDefaultWriteParam();
            iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            iwp.setCompressionQuality(mJPGQuality / 100f);
            final File tempJPG = Files.createTempFile("loimgcomp", "jpg")
                    .toFile();
            tempJPG.deleteOnExit();
            final File tempPNG = Files.createTempFile("loimgcomp", "png")
                    .toFile();
            tempPNG.deleteOnExit();
            try (ImageOutputStream ios = ImageIO
                    .createImageOutputStream(new FileOutputStream(tempJPG))) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(resized, null, null), iwp);
            }
            try (ImageOutputStream ios = ImageIO
                    .createImageOutputStream(new FileOutputStream(tempPNG))) {
                writer.setOutput(ios);
                writer.write(resized);
            }
            if (tempPNG.length() < tempJPG.length()) {
                // Use png
                tempJPG.delete();
                mImageFiles.put(imageInfo.getRelativeName(), tempPNG);
                return "png";
            } else {
                // Use jpg
                tempPNG.delete();
                mImageFiles.put(imageInfo.getRelativeName(), tempJPG);
                return "jpg";
            }
        } else {
            // Save as png
            final File tempPNG = Files.createTempFile("loimgcomp", "png")
                    .toFile();
            tempPNG.deleteOnExit();
            final ImageWriter writer = ImageIO.getImageWritersBySuffix("png")
                    .next();
            try (ImageOutputStream ios = ImageIO
                    .createImageOutputStream(new FileOutputStream(tempPNG))) {
                writer.setOutput(ios);
                writer.write(resized);
            }
            mImageFiles.put(imageInfo.getRelativeName(), tempPNG);
            return "png";
        }
    }

}
