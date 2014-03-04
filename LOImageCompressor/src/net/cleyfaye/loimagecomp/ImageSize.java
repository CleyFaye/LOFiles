package net.cleyfaye.loimagecomp;

/**
 * Store an image size.
 * 
 * TODO move size-related code here.
 * 
 * @author Cley Faye
 */
public class ImageSize {

    private double mX;

    private double mY;

    public ImageSize(final double x, final double y) {
        super();
        mX = x;
        mY = y;
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
