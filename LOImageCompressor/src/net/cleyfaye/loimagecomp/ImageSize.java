package net.cleyfaye.loimagecomp;

/**
 * Store an image size.
 * 
 * TODO move size-related code here.
 * 
 * @author Cley Faye
 */
public class ImageSize {

    public ImageSize(double x, double y) {
        super();
        mX = x;
        mY = y;
    }

    private double mX;
    private double mY;

    public double getX()
    {
        return mX;
    }

    public void setX(double x)
    {
        mX = x;
    }

    public double getY()
    {
        return mY;
    }

    public void setY(double y)
    {
        mY = y;
    }

    @Override
    public String toString()
    {
        return mX + "x" + mY;
    }
}
