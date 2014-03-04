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
    private ImageSize mDrawSizeCm;
    /** Relative file name */
    private String mFileName;

    /** Original resolution */
    private ImageSize mImageSizePx;
    /** Original file size */
    private long mImageSize;

    /** If the image is embedded in the archive, or not. */
    private boolean mEmbedded;
    /** The original image file */
    private File mFile;

    public ImageInfo(File tempDir, String fileName, double drawWidthCm,
            double drawHeightCm) throws IOException {
        mDrawSizeCm = new ImageSize(drawWidthCm, drawHeightCm);
        mFileName = fileName;
        File imageFile = new File(fileName);
        if (imageFile.isAbsolute()) {
            mEmbedded = false;
        } else {
            mFile = new File(tempDir, fileName);
            mEmbedded = mFile.exists();
        }
        if (mEmbedded) {
            BufferedImage img = ImageIO.read(mFile);
            mImageSizePx = new ImageSize(img.getWidth(), img.getHeight());
            mImageSize = mFile.length();
        }
    }

    public ImageSize getDrawSizeCm()
    {
        return mDrawSizeCm;
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

    /** Return the image relative name */
    public String getRelativeName()
    {
        return mFileName;
    }

    public ImageSize getImageSizePx()
    {
        return mImageSizePx;
    }

    public long getImageSize()
    {
        return mImageSize;
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
    public void increaseDrawSize(double readingWidth, double readingHeight)
    {
        if (mDrawSizeCm.getX() < readingWidth) {
            mDrawSizeCm.setX(readingWidth);
        }
        if (mDrawSizeCm.getY() < readingHeight) {
            mDrawSizeCm.setY(readingHeight);
        }
    }
}
