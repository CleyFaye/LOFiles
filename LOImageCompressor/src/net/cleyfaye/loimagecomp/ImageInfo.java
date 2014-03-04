package net.cleyfaye.loimagecomp;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Store informations about an image.
 * 
 * This class hold the image path, the intended print size, and the original
 * resolution.
 * 
 * TODO If acceptable, move image resizing code around here...
 * 
 * TODO Add a reference to the ODTFile object, for future use
 * 
 * @author Cley Faye
 */
public class ImageInfo {

    /** Intended print size in cm */
    private final ImageSize mDrawSizeCm;
    /** Relative file name */
    private final String mFileName;

    /** Original resolution */
    private ImageSize mImageSizePx;
    /** Original file size */
    private long mImageSize;

    /** If the image is embedded in the archive, or not. */
    private boolean mEmbedded;
    /** The original image file */
    private File mFile;

    public ImageInfo(final File tempDir, final String fileName,
            final double drawWidthCm, final double drawHeightCm)
            throws IOException {
        mDrawSizeCm = new ImageSize(drawWidthCm, drawHeightCm);
        mFileName = fileName;
        final File imageFile = new File(fileName);
        if (imageFile.isAbsolute()) {
            mEmbedded = false;
        } else {
            mFile = new File(tempDir, fileName);
            mEmbedded = mFile.exists();
        }
        if (mEmbedded) {
            final BufferedImage img = ImageIO.read(mFile);
            mImageSizePx = new ImageSize(img.getWidth(), img.getHeight());
            mImageSize = mFile.length();
        }
    }

    public ImageSize getDrawSizeCm()
    {
        return mDrawSizeCm;
    }

    public long getImageSize()
    {
        return mImageSize;
    }

    public ImageSize getImageSizePx()
    {
        return mImageSizePx;
    }

    /** Return the image relative name */
    public String getRelativeName()
    {
        return mFileName;
    }

    /**
     * Update the intended print size.
     * 
     * When the same image is referenced multiple times, we take the largest
     * print size in both directions.
     * 
     * @param readingWidth
     *            New width
     * @param readingHeight
     *            New height
     */
    public void increaseDrawSize(final double readingWidth,
            final double readingHeight)
    {
        if (mDrawSizeCm.getX() < readingWidth) {
            mDrawSizeCm.setX(readingWidth);
        }
        if (mDrawSizeCm.getY() < readingHeight) {
            mDrawSizeCm.setY(readingHeight);
        }
    }

    /**
     * Determine if an image is embedded or not.
     * 
     * If an image is not embedded, we don't have any information about it.
     * 
     * TODO just ignore images references when they are not embedded
     * 
     * @return true if the image is embedded, false otherwise.
     */
    public boolean isEmbedded()
    {
        return mEmbedded;
    }
}
