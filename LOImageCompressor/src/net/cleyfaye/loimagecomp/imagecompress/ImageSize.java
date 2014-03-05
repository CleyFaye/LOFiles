package net.cleyfaye.loimagecomp.imagecompress;

/**
 * Store an image size.
 * 
 * @author Cley Faye
 */
public class ImageSize {

    /**
     * Compute new image resolution after resizing.
     * 
     * This can shrink images, but never make them bigger. The new size will
     * never be less than 1x1. If the draw size is less than 0.1x0.1, no
     * resizing occurs (it's used to indicate that we don't know the intended
     * print size).
     * 
     * @param originalSizePx
     *            Original image size in pixels
     * @param drawSizeCm
     *            Intended print size in cm. If 0x0, it's supposed to be
     *            unknown.
     * @param dpi
     *            Target DPI
     * @return The new image size, in pixel.
     */
    static public ImageSize projectImageSize(final ImageSize originalSizePx,
            final ImageSize drawSizeCm, final double dpi)
    {
        if (drawSizeCm.getX() < 0.1 || drawSizeCm.getY() < 0.1) {
            // No print size, just return original resolution
            return new ImageSize(originalSizePx);
        }
        // That's how you convert dot per inch to dot per cm
        final double dpcm = dpi * 0.393701;
        final ImageSize targetSize = new ImageSize(drawSizeCm.getX() * dpcm,
                drawSizeCm.getY() * dpcm);
        if (targetSize.getX() > originalSizePx.getX()
                || targetSize.getY() > originalSizePx.getY()) {
            // We're getting bigger result, so just return the original size
            // instead
            return new ImageSize(originalSizePx);
        }
        // Check if we're close to the original size
        if (Math.abs(targetSize.getX() - originalSizePx.getX()) < 10
                || Math.abs(targetSize.getY() - originalSizePx.getY()) < 10) {
            return new ImageSize(originalSizePx);
        }
        // Final step: make sure we're not outputting image that are half a
        // pixel wide...
        targetSize.setX(Math.round(targetSize.getX()));
        targetSize.setY(Math.round(targetSize.getY()));
        if (targetSize.getX() < 1) {
            targetSize.setX(1);
        }
        if (targetSize.getY() < 1) {
            targetSize.setY(1);
        }
        return targetSize;
    }

    private double mX;

    private double mY;

    public ImageSize(final double x, final double y) {
        mX = x;
        mY = y;
    }

    public ImageSize(final ImageSize src) {
        mX = src.mX;
        mY = src.mY;
    }

    public double getX()
    {
        return mX;
    }

    public double getY()
    {
        return mY;
    }

    public void setX(final double x)
    {
        mX = x;
    }

    public void setY(final double y)
    {
        mY = y;
    }

    @Override
    public String toString()
    {
        return mX + "x" + mY;
    }

}
