package net.cleyfaye.loimagecomp;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class ImageInfo {

    private ImageSize mDrawSizeCm;
    private String mFileName;

    private ImageSize mImageSizePx;
    private long mImageSize;

    private boolean mEmbedded;
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

    public boolean isEmbedded()
    {
        return mEmbedded;
    }

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
