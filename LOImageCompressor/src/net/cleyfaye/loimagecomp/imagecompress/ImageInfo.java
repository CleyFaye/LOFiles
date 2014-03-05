package net.cleyfaye.loimagecomp.imagecompress;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Store informations about an image.
 * 
 * This class hold the image path, the intended print size, and the original
 * resolution, as well as the target DPI and resolution.
 * 
 * TODO images not embedded are never referenced in ImageInfo. Remove
 * isEmbedded()
 * 
 * @author Cley Faye
 */
public class ImageInfo {

    /** Source ODT file */
    private final ODTFile mODTFile;
    /** Maximum intended print size in cm */
    private final ImageSize mDrawSizeCm = new ImageSize(0, 0);
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
    /** The target DPI */
    private double mTargetDPI = 0;
    /** Target resolution */
    private ImageSize mTargetImageSizePx;
    /**
     * Image "names"
     * 
     * As found in the document
     */
    private final List<String> mNames = new ArrayList<>();

    public ImageInfo(final ODTFile odtFile, final String fileName)
            throws IOException {
        mODTFile = odtFile;
        mFileName = fileName;
        final File imageFile = new File(fileName);
        if (imageFile.isAbsolute()) {
            mEmbedded = false;
        } else {
            mFile = mODTFile.getExtractedFile(fileName);
            mEmbedded = mFile != null;
        }
        if (mEmbedded) {
            final BufferedImage img = ImageIO.read(mFile);
            mImageSizePx = new ImageSize(img.getWidth(), img.getHeight());
            mImageSize = mFile.length();
            mTargetImageSizePx = mImageSizePx;
        }
    }

    public void addName(final String name)
    {
        if (name != null && !name.isEmpty()) {
            if (!mNames.contains(name)) {
                mNames.add(name);
            }
        }
    }

    public ImageSize getDrawSizeCm()
    {
        return new ImageSize(mDrawSizeCm);
    }

    /** Return the original image file */
    public File getFile()
    {
        return mFile;
    }

    public long getImageSize()
    {
        return mImageSize;
    }

    public ImageSize getImageSizePx()
    {
        return new ImageSize(mImageSizePx);
    }

    /** Return the image relative name */
    public String getRelativeName()
    {
        return mFileName;
    }

    public ImageSize getTargetImageSizePx()
    {
        return new ImageSize(mTargetImageSizePx);
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

    /** Change this image's target DPI */
    public void setTargetDPI(final double value)
    {
        mTargetDPI = value;
        if (mEmbedded) {
            mTargetImageSizePx = ImageSize.projectImageSize(mImageSizePx,
                    mDrawSizeCm, mTargetDPI);
        }
    }

    @Override
    public String toString()
    {
        if (mNames.isEmpty()) {
            return mFileName;
        } else {
            final StringBuilder sb = new StringBuilder();
            for (final String name : mNames) {
                if (sb.length() == 0) {
                    sb.append(name);
                } else {
                    sb.append(", ");
                    sb.append(name);
                }
            }
            return sb.toString();
        }
    }
}
