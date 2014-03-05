package net.cleyfaye.loimagecomp.imagecompress.interfaces;

import java.io.OutputStream;

import net.cleyfaye.loimagecomp.imagecompress.ImageInfo;

/**
 * A filter that process each image from source to destination.
 * 
 * There will be a batch of calls to prepareImage(), followed by a batch of
 * calls to getImageSuffix(), and a batch of calls to getImageData(), in that
 * order. The ImageInfo objects between each batch will be the same.
 * 
 * @author Cley Faye
 */
public interface ImageFilter {

    /**
     * Return the image data
     * 
     * Store the final image file using the given output stream
     * 
     * @param imageInfo
     *            The image info
     * @param output
     *            The output stream
     */
    public void getImageData(ImageInfo imageInfo, OutputStream output)
            throws Exception;

    /**
     * Return the final image suffix.
     * 
     * @param imageInfo
     *            The image info
     * @return The image suffix ("jpg" or "png").
     */
    public String getImageSuffix(ImageInfo imageInfo) throws Exception;

    /**
     * Called on each image once to prepare it.
     * 
     * @param imageInfo
     *            The image info.
     */
    public void prepareImage(ImageInfo imageInfo) throws Exception;
}
